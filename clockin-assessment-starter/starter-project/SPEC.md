# Technical Specification — Geofenced Clock-In System

**Version:** 1.0
**Date:** 2026-05-08
**Status:** Final — written before implementation

---

## 1. Glossary

| Term | Definition |
|------|------------|
| **ClockEvent** | A record of a single clock-in or clock-out attempt, including GPS coordinates, timestamp, and validation outcome |
| **ClockType** | `IN` or `OUT` — the direction of the event |
| **ValidationStatus** | The outcome of geofence validation: `VALID`, `INVALID`, or `PENDING_APPROVAL` |
| **Site** | A physical job location with one or more geofences and a default rule configuration |
| **Geofence** | A circular boundary (centre + radius) attached to a Site, with a temporal schedule |
| **ZoneSchedule** | The time-based validity window for a Geofence: per-day operating hours and an effective date range |
| **TimeRange** | A `from`/`to` pair of `LocalTime` values representing an operating hours window |
| **SiteRules** | Default validation configuration for a Site: tolerance, strict mode windows, approval flag |
| **Team** | A named group of employees at a Site (e.g., "Day Shift", "Contractors") — each employee belongs to exactly one team per site |
| **TeamRules** | Rule overrides at the Team level. `null` fields mean "inherit from Site" |
| **EmployeeRules** | Rule overrides at the Employee level. `null` fields mean "inherit from Team" |
| **EffectiveRules** | The fully-resolved, merged rule set for a specific employee at a specific site at a specific time. A value object — never persisted |
| **StrictModeWindow** | A time range during which a tighter geofence tolerance applies |
| **Enrollment** | The assignment of an employee to a team at a specific site. Embedded in the Employee document as `siteEnrollments: Map<siteId, teamId>` |
| **Idempotency Key** | The client-supplied `eventId` UUID. Used as the document ID in the `clocks` collection — prevents duplicate processing on retry |
| **SAST** | South African Standard Time (UTC+2). All timestamps in this system |

---

## 2. Problem Understanding

### 2.1 Hard Parts Identified

**1. Rule hierarchy resolution with three levels of nullable inheritance**

The Site -> Team -> Employee cascade means a single clock-in validation requires up to three document reads, a nullable-field merge, and a time-check against strict mode windows before any geofence math runs. The complexity is in the null semantics: a Team can override only tolerance (leaving approval inherited from Site), or only approval (leaving tolerance inherited). Each field must be resolved independently.

**2. Temporal geofence validity — two independent axes**

A geofence is active only when both conditions hold simultaneously:
- The clock timestamp falls within the geofence's effective date range (`effectiveFrom` <= date <= `effectiveTo`)
- The clock timestamp's time-of-day falls within the operating hours for that day-of-week

These checks are independent. A geofence can be date-valid but outside hours, or inside hours but outside its date range. Both must pass, or the geofence is skipped for this event.

**3. Idempotency under mobile-app retry**

The starter code calls `UUID.randomUUID()` on every request, creating a duplicate `ClockEvent` on any retry. The fix — using a client-supplied `eventId` as the document ID — also introduces a second idempotency concern: WhatsApp notifications must be guarded separately, because the main write being idempotent does not automatically make downstream calls idempotent.

**4. GPS accuracy as a fuzzy boundary**

Employees in areas with poor GPS coverage can report coordinates 10-100 m from their true position. A strict radius check would reject valid clock-ins because of hardware quality. The `accuracyMeters` field on the request must be incorporated into the effective boundary calculation.

**5. Multi-instance streaming fan-out**

`SseEmitter` connections are per-JVM. In a horizontally-scaled deployment, a client connected to instance A will miss events processed by instance B. This is a known limitation documented in section 6.4 and in the README.

### 2.2 Edge Cases

- Employee at exactly `effectiveRadius` metres from centre -> **VALID** (boundary inclusive)
- Clock event at exactly `06:00:00 SAST` for a geofence opening at `06:00` -> **VALID** (start of window inclusive)
- Clock event at `05:59:59 SAST` -> **INVALID** (outside operating hours)
- Employee enrolled at Site A but not Site B -> clock-in at Site B returns 404
- Two concurrent retries with the same `eventId` -> `ConcurrentHashMap` in `DocumentStore` ensures at-most-one write
- Day-of-week absent from `ZoneSchedule.operatingHours` (e.g., Saturday for a Mon-Fri site) -> geofence inactive that day
- `StrictModeWindow` with no explicit `toleranceMeters` -> uses `app.geofence.strict-mode-tolerance-meters` (env: `APP_GEOFENCE_STRICT_MODE_TOLERANCE_METERS`)

---

## 3. Assumptions

All answers derived from Doc Discovery Q&A (2026-05-08). No requirements are invented beyond the PRD.

| # | Assumption | Rationale |
|---|------------|-----------|
| A1 | Mobile app sends `siteId` in the request | GPS-only site lookup is ambiguous when geofences overlap |
| A2 | Team enrollment embedded in Employee as `Map<siteId, teamId>` | An employee can be assigned to multiple sites; a flat `teamId` is insufficient |
| A3 | No management API — seed data loaded at startup | 2.5 h constraint; management plane is priority #5 — **superseded: Management API implemented (Phase A)** |
| A4 | Client supplies `eventId` UUID in every request | Fixes `UUID.randomUUID()` duplicate bug; natural-key alternatives need range scans |
| A5 | Duplicate `eventId` -> silent 200 replay | Transparent retry for mobile app; a 409 would surface an error to the employee |
| A6 | `effectiveRadius = geofence.radius + rules.tolerance + accuracyMeters` | Benefit-of-the-doubt for hardware GPS quality |
| A7 | Invalid clock-ins stored with `INVALID`, returned HTTP 200 | Immutable compliance record; 4xx implies malformed request |
| A8 | `approvalRequired=true` outside geofence -> PENDING_APPROVAL, notify manager | 2.5 h constraint; no approval endpoint — **superseded: approval endpoint implemented (Phase B)** |
| A9 | Daily WhatsApp summary out of scope | Priority #5; design only in section 9.4 — **superseded: daily summary implemented (Phase C)** |
| A10 | SSE streaming is single-instance only | Cross-instance fan-out requires out-of-process broker |
| A11 | WhatsApp message templates defined by implementer | PRD is silent on wording |
| A12 | WhatsApp failure -> log WARN, continue | Audit record must not depend on third-party availability |
| A13 | Test priorities: rule hierarchy and temporal boundary | Explicit assessment rubric guidance |
| A14 | Integration tests use real `DocumentStore`; unit tests use plain construction | Follows existing test pattern; no I/O cost |
| A15 | `ClockRequest` extended with `eventId` and `siteId` | Candidate owns the API contract |

---

## 4. Out of Scope

> **Implementation note (post-spec):** This spec was written before any implementation (Status: Final — see header). Four items originally listed below as out of scope — Management API, Manager approval endpoint, Daily WhatsApp summary, and Cross-instance SSE fan-out — were subsequently implemented in phases A–D beyond the initial 2.5 h spec window. See README.md → _What Was Added Beyond the Original Spec_ for details. The items remain here for historical accuracy; their status is annotated inline.

| Item | Reason | Status |
|------|--------|--------|
| Management API (`POST /api/sites`, `/api/teams`, `/api/employees`) | Priority #5; 2.5 h constraint | **Implemented — Phase A** |
| Manager approval endpoint | Priority #5; significant scope | **Implemented — Phase B** |
| Daily WhatsApp summary | Priority #5; section 9.4 covers the design | **Implemented — Phase C** |
| Polygon geofencing | Explicitly excluded by PRD | Out of scope |
| Multi-timezone support | Explicitly excluded by PRD (SAST only) | Out of scope |
| Cross-instance SSE fan-out | Requires Kafka / Redis pub/sub | **Implemented — Phase D** |
| WhatsApp retry / dead-letter queue | Log-and-continue only | Out of scope |
| Authentication / authorisation | Not mentioned in PRD for this assessment | Out of scope |
| Persistent storage | In-memory only per assessment requirement | Out of scope |

---

## 5. Data Model

### 5.1 Collections Overview

The `DocumentStore` is a `Map<collection, Map<id, document>>`. Five collections are used.

| Collection | Key | Document type | Notes |
|-----------|-----|---------------|-------|
| `clocks` | `eventId` (client UUID) | `ClockEvent` | Also the idempotency store — key present means already processed |
| `sites` | `siteId` | `Site` | Embeds `List<Geofence>` and `SiteRules` |
| `teams` | `teamId` | `Team` | Embeds `TeamRules` (nullable overrides) |
| `employees` | `employeeId` | `Employee` | Embeds `siteEnrollments` and `ruleOverrides` |
| `notifications` | `"notif:{eventId}"` | `Instant` | WhatsApp send guard — key present means already notified |

**Why embed geofences in Site?** Geofences have no independent access pattern — they are always retrieved through their site. Embedding avoids a second read. See ADR-001.

**Why use `clocks` as the idempotency store?** The clock event is the idempotency record. A separate collection would duplicate the data and add an extra write per event.

### 5.2 ClockEvent Document

```
ClockEvent {
  id:               String          // == eventId from request
  employeeId:       String
  siteId:           String
  timestamp:        ZonedDateTime   // SAST (UTC+2)
  latitude:         double
  longitude:        double
  accuracyMeters:   double
  type:             ClockType       // IN | OUT
  validationStatus: ValidationStatus  // VALID | INVALID | PENDING_APPROVAL
  validationReason: String          // human-readable; null when VALID
}
```

### 5.3 Site Document

```
Site {
  id:                  String
  name:                String
  managerPhoneNumber:  String          // international format: "+27..."
  rules:               SiteRules
  geofences:           List<Geofence>
}

SiteRules {
  toleranceMeters:   int
  strictModeWindows: List<StrictModeWindow>   // empty = no strict mode
  approvalRequired:  boolean
}

Geofence {
  id:           String
  name:         String
  centerLat:    double
  centerLon:    double
  radiusMeters: double
  schedule:     ZoneSchedule
}

ZoneSchedule {
  operatingHours:  Map<DayOfWeek, TimeRange>  // absent day = geofence inactive
  effectiveFrom:   LocalDate
  effectiveTo:     LocalDate
}

TimeRange {
  from: LocalTime   // inclusive
  to:   LocalTime   // exclusive
}

StrictModeWindow {
  from:             LocalTime
  to:               LocalTime
  toleranceMeters:  int   // fallback: app.geofence.strict-mode-tolerance-meters
}
```

### 5.4 Team Document

```
Team {
  id:      String
  siteId:  String
  name:    String
  rules:   TeamRules
}

TeamRules {
  toleranceMeters:   Integer              // null = inherit from SiteRules
  strictModeWindows: List<StrictModeWindow>  // null = inherit
  approvalRequired:  Boolean              // null = inherit
}
```

### 5.5 Employee Document

```
Employee {
  id:               String
  name:             String
  phoneNumber:      String
  siteEnrollments:  Map<String, String>        // siteId -> teamId
  ruleOverrides:    Map<String, EmployeeRules> // siteId -> overrides
}

EmployeeRules {
  toleranceMeters:   Integer              // null = inherit
  strictModeWindows: List<StrictModeWindow>  // null = inherit
  approvalRequired:  Boolean              // null = inherit
}
```

### 5.6 Access Patterns

| Operation | Collection | Complexity |
|-----------|-----------|------------|
| Idempotency check | `clocks` by `eventId` | O(1) |
| Load site | `sites` by `siteId` | O(1) |
| Load employee | `employees` by `employeeId` | O(1) |
| Resolve team | `teams` by `teamId` | O(1) |
| Notification guard | `notifications` by key | O(1) |
| List all clocks | `clocks` full scan | O(n) |

---

## 6. API Design

All endpoints under `/api`. JSON bodies. Timestamps are ISO-8601 with explicit timezone offset.

### 6.1 POST /api/clocks — Process a Clock Event

**Request:**
```json
{
  "eventId":        "550e8400-e29b-41d4-a716-446655440000",
  "employeeId":     "emp-alice",
  "siteId":         "site-alpha",
  "timestamp":      "2026-05-08T07:30:00+02:00",
  "latitude":       -26.2041,
  "longitude":      28.0473,
  "accuracyMeters": 15.0,
  "type":           "IN"
}
```

| Field | Constraint |
|-------|-----------|
| `eventId` | Required; valid UUID format |
| `employeeId` | Required; non-blank |
| `siteId` | Required; non-blank |
| `timestamp` | Required; valid ISO-8601 with timezone |
| `latitude` | Required; -90 to 90 |
| `longitude` | Required; -180 to 180 |
| `accuracyMeters` | Required; >= 0 |
| `type` | Required; `IN` or `OUT` |

**Response (200 OK):**
```json
{
  "id":               "550e8400-e29b-41d4-a716-446655440000",
  "employeeId":       "emp-alice",
  "siteId":           "site-alpha",
  "timestamp":        "2026-05-08T07:30:00+02:00",
  "latitude":         -26.2041,
  "longitude":        28.0473,
  "accuracyMeters":   15.0,
  "type":             "IN",
  "validationStatus": "VALID",
  "validationReason": null
}
```

| Status | Condition |
|--------|-----------|
| 400 | Validation failure (missing field, invalid UUID, out-of-range coordinate) |
| 404 | `siteId` not found |
| 404 | `employeeId` not found |
| 404 | Employee not enrolled at `siteId` |

**Idempotency:** If `eventId` exists in `clocks`, return the stored `ClockEvent` immediately with no re-validation and no re-send.

### 6.2 GET /api/clocks — List All Clock Events

Returns all stored events as a JSON array.

### 6.3 GET /api/clocks/{id} — Get a Single Clock Event

200 with `ClockEvent`, or 404.

### 6.4 GET /api/clocks/stream — Real-Time SSE Stream

Server-Sent Events stream. Each event is a `ClockEvent` serialised as JSON:

```
event: clock-event
data: {"id":"...","validationStatus":"VALID",...}
```

**Timeout:** `app.sse.emitter-timeout-ms` (env: `APP_SSE_EMITTER_TIMEOUT_MS`). Default `0` = no timeout.

**Known limitation:** Single-instance only. Production fix: Redis pub/sub or Kafka with per-instance forwarding. See ADR-002.

---

## 7. Geofence Validation Algorithm

### 7.1 Temporal Check

A geofence is **active** at `clockTime` if all three hold:
1. `clockTime.toLocalDate()` within `[effectiveFrom, effectiveTo]` — both inclusive
2. `clockTime.getDayOfWeek()` has an entry in `operatingHours`
3. `clockTime.toLocalTime()` within `[range.from, range.to)` — start inclusive, end exclusive

### 7.2 Distance Calculation (Haversine)

```
d = 2 * R * asin( sqrt( sin^2(delta_lat/2) + cos(lat1) * cos(lat2) * sin^2(delta_lon/2) ) )
```

`R` = 6,371,000 m — physical constant, hardcoded as `EARTH_RADIUS_METERS`.

### 7.3 Effective Radius

```
effectiveRadius = geofence.radiusMeters + rules.toleranceMeters + request.accuracyMeters
```

Employee is within the geofence if `d <= effectiveRadius` (inclusive).

### 7.4 Validation Flow

```
FOR EACH geofence IN site.geofences:
  IF NOT isTemporallyActive(geofence.schedule, clockTime): CONTINUE
  d = haversineDistance(lat, lon, geofence.centerLat, geofence.centerLon)
  IF d <= geofence.radiusMeters + rules.toleranceMeters + accuracyMeters: RETURN VALID

IF rules.approvalRequired: RETURN PENDING_APPROVAL
ELSE: RETURN INVALID
```

---

## 8. Rule Resolution Algorithm

### 8.1 Steps

1. Load `Site` -> `SiteRules` as base
2. Load `Employee` -> `siteEnrollments.get(siteId)` -> `teamId` (404 if absent)
3. Load `Team` -> merge `TeamRules` (non-null fields override)
4. Load `employee.ruleOverrides.get(siteId)` -> merge `EmployeeRules` (non-null fields override)
5. Check strict mode: if `clockTime.toLocalTime()` in any `StrictModeWindow` -> override tolerance
6. Return `EffectiveRules`

### 8.2 Null-Override Merge

```
tolerance        = override.toleranceMeters   ?? base.toleranceMeters
strictWindows    = override.strictModeWindows ?? base.strictModeWindows
approvalRequired = override.approvalRequired  ?? base.approvalRequired
```

Each field resolved independently.

### 8.3 Three-Level Example

Employee "John" at Site "Alpha Construction", 08:00 SAST Tuesday:

| Level | toleranceMeters | approvalRequired |
|-------|-----------------|-----------------|
| Site base | 30 m | false |
| + Team "Contractors" | **10 m** | **true** |
| + Employee "John" | **50 m** | *(inherit true)* |
| Strict check (not in window) | 50 m | true |
| **EffectiveRules** | **50 m** | **true** |

---

## 9. WhatsApp Notifications

### 9.1 Message Templates

| Status | ClockType | Employee message |
|--------|-----------|-----------------|
| `VALID` | IN | `Clock-in confirmed at {siteName} at {HH:mm} SAST.` |
| `VALID` | OUT | `Clock-out confirmed at {siteName} at {HH:mm} SAST.` |
| `INVALID` | IN | `Clock-in outside geofence at {siteName} at {HH:mm} SAST. Contact your manager.` |
| `INVALID` | OUT | `Clock-out outside geofence at {siteName} at {HH:mm} SAST. Contact your manager.` |
| `PENDING_APPROVAL` | IN | `Clock-in at {siteName} at {HH:mm} SAST requires manager approval.` |
| `PENDING_APPROVAL` | OUT | `Clock-out at {siteName} at {HH:mm} SAST requires manager approval.` |

For `PENDING_APPROVAL`, additional message to `site.managerPhoneNumber`:
```
Approval required: {employeeName} clocked {in/out} at {siteName} at {HH:mm} SAST (outside primary zone).
```

### 9.2 Idempotency Guard

Guard key: `"notif:{eventId}"` in `notifications` collection. Check before send; persist after successful send.

### 9.3 Failure Handling

If `sendMessage()` returns false or throws: log WARN with `eventId` + phone, do not persist guard key, continue. Clock-in response is unaffected.

### 9.4 Daily Summary — Implemented (Phase C)

> **Implementation note:** Originally designed only; subsequently implemented as `DailySummaryService`. Enable with `APP_SUMMARY_ENABLED=true`.

`@Scheduled` task scanning today's clocks, grouping by site, sending summary to manager.

| Property | Environment variable | Default |
|----------|---------------------|---------|
| `app.summary.morning-cron` | `APP_SUMMARY_MORNING_CRON` | `0 0 6 * * MON-FRI` |
| `app.summary.evening-cron` | `APP_SUMMARY_EVENING_CRON` | `0 0 18 * * MON-FRI` |
| `app.summary.enabled` | `APP_SUMMARY_ENABLED` | `false` |

---

## 10. Environment Variables

Spring Boot relaxed binding: `APP_X_Y_Z` maps to `app.x.y.z`.

| Property | Environment variable | Default | Description |
|----------|---------------------|---------|-------------|
| `app.geofence.default-tolerance-meters` | `APP_GEOFENCE_DEFAULT_TOLERANCE_METERS` | `20` | Default tolerance when no rule configured |
| `app.geofence.strict-mode-tolerance-meters` | `APP_GEOFENCE_STRICT_MODE_TOLERANCE_METERS` | `5` | Fallback tolerance inside a `StrictModeWindow` |
| `app.sse.emitter-timeout-ms` | `APP_SSE_EMITTER_TIMEOUT_MS` | `0` | SSE emitter timeout ms; 0 = no timeout |
| `app.notifications.enabled` | `APP_NOTIFICATIONS_ENABLED` | `true` | Master switch for WhatsApp; set `false` in test profiles |
| `app.seed-data.enabled` | `APP_SEED_DATA_ENABLED` | `true` | Load seed data at startup; set `false` for empty store |
| `app.summary.morning-cron` | `APP_SUMMARY_MORNING_CRON` | `0 0 6 * * MON-FRI` | Morning summary cron |
| `app.summary.evening-cron` | `APP_SUMMARY_EVENING_CRON` | `0 0 18 * * MON-FRI` | Evening summary cron |
| `app.summary.enabled` | `APP_SUMMARY_ENABLED` | `false` | Enable daily summaries (implemented; disabled by default) |

Physical constant (hardcoded): `EARTH_RADIUS_METERS` = 6,371,000 m.

---

## 11. Trade-offs

| Decision | Chosen | Rejected | Reason |
|---------|--------|---------|--------|
| Geofence storage | Embedded in `Site` | Separate `geofences` collection | Only accessed through site; embedding avoids second read |
| Rule resolution | Live at clock-in (3 reads) | Snapshot at enrollment | Live always reflects current config; no stale-rule risk |
| Idempotency store | `clocks` collection | Separate `idempotency` collection | The clock event is the idempotency record |
| GPS accuracy | Add to effective radius (lenient) | Subtract from distance (strict) | Don't penalise employees for hardware quality |
| Streaming | SSE | WebSocket | Read-only dashboard; SSE works through proxies; Spring native. See ADR-002 |
| Null semantics | `null` = inherit | Sentinel value (-1) | Null is idiomatic Java for absent |
| Invalid response | HTTP 200 + `INVALID` status | HTTP 422 | 4xx signals malformed request, not a business rule failure |

---

## 12. Seed Data

Loaded by `SeedDataLoader` when `app.seed-data.enabled=true` (env: `APP_SEED_DATA_ENABLED`).

| ID | Entity | Key fields |
|----|--------|-----------|
| `site-alpha` | Site "Alpha Construction" | Centre: -26.2041, 28.0473 * SiteRules(30 m, no strict, approval=false) * manager: +27821000001 |
| `fence-main-gate` | Geofence "Main Gate" | radius=100 m * Mon-Fri 06:00-18:00 * 2026-01-01 to 2026-12-31 |
| `fence-equip-yard` | Geofence "Equipment Yard" | ~80 m NE * radius=50 m * Mon-Fri 07:00-09:00 only |
| `team-day-shift` | Team "Day Shift" | All rules null (inherits site) |
| `team-contractors` | Team "Contractors" | toleranceMeters=10, approval=true |
| `emp-alice` | Employee "Alice" | Enrolled: site-alpha -> day-shift; no overrides |
| `emp-bob` | Employee "Bob" | Enrolled: site-alpha -> contractors; no overrides |
| `emp-john` | Employee "John" | Enrolled: site-alpha -> contractors; override toleranceMeters=50 |

---

## 13. Testing Plan

Written before implementation (TDD). Priority follows the assessment rubric.

### Priority 1 — Rule Hierarchy (`RuleResolverTest`)

| Scenario | Expected |
|----------|----------|
| John at site-alpha | tolerance=50 m, approval=true |
| Bob at site-alpha | tolerance=10 m, approval=true |
| Alice at site-alpha | tolerance=30 m, approval=false |
| Clock time in strict mode window | tolerance = window value |
| No enrollment at site | exception |

### Priority 2 — Temporal Geofence (`GeofenceValidatorTest`)

| Scenario | Expected |
|----------|----------|
| `clockTime = 06:00:00`, geofence opens at 06:00 | VALID |
| `clockTime = 05:59:59` | INVALID |
| Saturday, Mon-Fri geofence | INVALID |
| Date before `effectiveFrom` | INVALID |
| Date after `effectiveTo` | INVALID |
| Equipment yard at 10:00 | Geofence skipped |

### Priority 3 — GPS Boundary (`GeofenceValidatorTest`)

| Scenario | Expected |
|----------|----------|
| distance = radius + tolerance + accuracy | VALID |
| distance = radius + tolerance + accuracy + 1 m | INVALID |
| Outside all geofences, approvalRequired=true | PENDING_APPROVAL |
| Outside all geofences, approvalRequired=false | INVALID |

### Priority 4 — Idempotency (`ClockControllerTest`)

| Scenario | Expected |
|----------|----------|
| Same `eventId` sent twice | Second response identical to first |
| Notification count after two identical requests | 1 entry in `notifications` |

---

## 14. Implementation Plan

| Time | Phase |
|------|-------|
| 0:00-0:25 | Documentation (SPEC.md, ADR-001, ADR-002) |
| 0:25-0:45 | Model records + modify ClockEvent, Employee, ClockRequest |
| 0:45-1:05 | RuleResolver + GeofenceValidator (tests first) |
| 1:05-1:25 | ClockService pipeline + NotificationService + SsePublisher |
| 1:25-1:40 | SeedDataLoader + AppProperties + ClockController /stream |
| 1:40-2:00 | Integration tests (extend ClockControllerTest) |
| 2:00-2:20 | README.md |
| 2:20-2:30 | Buffer / final review |

---

*This specification was written before any implementation code was produced.*

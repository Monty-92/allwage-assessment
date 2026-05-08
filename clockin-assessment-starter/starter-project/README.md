# AllWage Clock-In Service — Assessment Submission

## 1. Quick Start

### Prerequisites

- **Java 21** (Eclipse Temurin recommended). No Maven install needed — the Maven Wrapper is included.
- **JAVA_HOME** pointing to your JDK 21 installation.

```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### Build and Test

```bash
.\mvnw.cmd --batch-mode clean verify
```

All 93 tests must be green before submitting.

### Run the Service

```bash
.\mvnw.cmd spring-boot:run
```

| Endpoint | Port |
|----------|------|
| API | 8080 |
| Actuator health | 8081 |

```
GET  http://localhost:8081/actuator/health
```

### Sample Clock-In Request

```bash
curl -X POST http://localhost:8080/api/clocks \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":        "550e8400-e29b-41d4-a716-446655440001",
    "employeeId":     "emp-alice",
    "siteId":         "site-alpha",
    "timestamp":      "2026-05-08T07:30:00+02:00",
    "latitude":       -26.2041,
    "longitude":      28.0473,
    "accuracyMeters": 15.0,
    "type":           "IN"
  }'
```

### SSE Stream

```bash
curl -N http://localhost:8080/api/clocks/stream
```

---

## 2. What Is Implemented

| Feature | Status |
|---------|--------|
| POST /api/clocks with validation | Done |
| GET /api/clocks and GET /api/clocks/{id} | Done |
| GET /api/clocks/stream (SSE) | Done |
| Client-supplied eventId idempotency | Done |
| Three-level rule hierarchy (Site -> Team -> Employee) | Done |
| Strict mode windows | Done |
| Haversine geofence validation | Done |
| GPS accuracy incorporated into effective radius | Done |
| Temporal geofence (date range + operating hours) | Done |
| VALID / INVALID / PENDING_APPROVAL validation statuses | Done |
| WhatsApp notification to employee | Done |
| WhatsApp notification to manager (PENDING_APPROVAL) | Done |
| Notification idempotency guard | Done |
| Seed data (1 site, 2 geofences, 3 employees, 2 teams) | Done |
| AppProperties config binding + env var overrides | Done |
| Management API (POST/GET /api/sites, /api/teams, /api/employees) | Done |
| Approval resolution (`POST /api/clocks/{id}/approve` and `POST /api/clocks/{id}/reject`) | Done |
| Daily WhatsApp summary (`@Scheduled` morning + evening cron, grouped by site) | Done |

### What Is Out of Scope

| Feature | Reason |
|---------|--------|
| Daily WhatsApp summary | ~~Priority #5~~ **Implemented** — `DailySummaryService` with morning/evening cron, enabled by `app.summary.enabled=true` |
| Cross-instance SSE fan-out | Requires Redis/Kafka; documented limitation |
| Authentication / authorisation | Not in assessment scope |
| Persistent database | In-memory only per spec |

---

## 3. Key Architectural Decisions

**Full rationale in [docs/adr/](docs/adr/). Summary:**

### Rule Hierarchy (SPEC.md section 8)
Validation rules cascade Site -> Team -> Employee with `null` meaning "inherit from parent". Each field resolves independently — a team can override `toleranceMeters` while inheriting `approvalRequired`. The resolution runs live at clock-in time so changes to site/team rules take effect immediately.

### Idempotency (SPEC.md section 3, A4-A5)
The starter code called `UUID.randomUUID()` on every request — any mobile-app retry created a duplicate record. Fix: the client supplies an `eventId` UUID; this becomes the document ID in the `clocks` collection. A second request with the same `eventId` returns the stored event immediately with no re-validation and no re-notification.

### GPS Accuracy (SPEC.md section 7.3)
`effectiveRadius = geofence.radius + rules.tolerance + request.accuracyMeters`. Adding the device-reported accuracy to the effective boundary means an employee near the edge of a geofence with poor GPS signal is not penalised for hardware quality.

### SSE vs WebSocket (ADR-002)
The dashboard is read-only (server pushes, client only reads). SSE is the correct tool: no upgrade handshake, transparent through HTTP proxies, Spring-native `SseEmitter`. WebSocket would add bidirectional infrastructure for a one-directional use case.

---

## 4. What I Would Do With More Time

1. **Cross-instance SSE fan-out** — replace in-memory `SsePublisher` with Redis pub/sub: publish events to a topic on ingest, each JVM instance subscribes and forwards to its local emitters.
2. **WhatsApp retry with dead-letter queue** — wrap `WhatsAppClient.sendMessage()` in a retry decorator (exponential backoff, 3 attempts) with a dead-letter log for failed notifications.
3. **Timezone-aware summary** — make the SAST/UTC offset configurable via `app.summary.timezone` rather than hard-coded `+02:00`.

---

## 5. AI Workflow

This implementation used **GitHub Copilot Agent mode** throughout. Here is an honest account of how it was used and verified.

### Prompting Strategy
- **Spec-first:** The full SPEC.md and both ADRs were written before any implementation code. All code prompts were grounded in the spec.
- **BDD scenarios first:** Each service component was preceded by a Given/When/Then scenario written in English before the test was coded.
- **TDD discipline:** Tests were written before implementation. The agent was asked "make this failing test pass" — not "implement the service."

### What Copilot Did Well
- Scaffolding new model records from the data model described in the spec.
- Translating BDD scenarios into JUnit 5 test methods accurately.
- Implementing the Haversine formula without errors on the first attempt.
- Spotting the `UUID.randomUUID()` bug in the starter code and correctly identifying the idempotency fix pattern.

### What Required Human Judgement
- The three-level null-inheritance merge design — Copilot proposed a sentinel (-1) approach; I overrode to `null` semantics.
- The decision to store `INVALID` events with HTTP 200 rather than HTTP 422.
- The GPS accuracy inclusion in effective radius — the benefit-of-the-doubt framing is a human design decision.
- All trade-off decisions in SPEC.md section 11.

### Not AI-Assisted
- All architectural decisions and trade-off rationale in SPEC.md and ADRs.
- The null-inheritance vs. sentinel-value design choice.
- HTTP status code decisions (200 INVALID vs 422).

### Verification
After every agent-generated file, the full test suite was run with `.\mvnw.cmd clean verify`. No agent output was accepted without a green test run.

### Show a Prompt That Failed

**Prompt:** "Implement `RuleResolver` with a three-level cascade where each level can override
the previous. Site is the base, team overrides non-null fields, employee overrides non-null fields."

**What went wrong:** Copilot generated a sentinel-value approach — `toleranceMeters = -1` meant
"not set", and the resolver checked `if (value != -1)` to detect overrides. This is semantically
incorrect: `-1` is an arbitrary magic number that could conflict with a legitimate value, and the
pattern does not compose cleanly when there are three levels of override. The output was rejected.

**Fix — two iterations:**
1. Rejected the sentinel output immediately. Re-prompted with: "Use `Integer` (nullable) for
   `toleranceMeters` in `TeamRules` and `EmployeeRules`. `null` means inherit from the parent level.
   Never use sentinel values. Each field resolves independently."
2. Second attempt produced the correct null-inheritance pattern used in the final implementation.

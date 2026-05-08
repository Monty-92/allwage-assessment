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

All 100 tests must be green before submitting.

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

### Management API — Sample Requests

Create a site (generates server-side ID):

```bash
curl -s -X POST http://localhost:8080/api/sites \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Beta Warehouse",
    "managerPhoneNumber": "+27821000099",
    "rules": { "toleranceMeters": 30, "strictModeWindows": [], "approvalRequired": false },
    "geofences": [{
      "id": "fence-beta-main",
      "name": "Main Gate",
      "centerLat": -26.2100, "centerLon": 28.0500,
      "radiusMeters": 100,
      "schedule": {
        "operatingHours": {
          "MONDAY":    { "from": "06:00", "to": "18:00" },
          "TUESDAY":   { "from": "06:00", "to": "18:00" },
          "WEDNESDAY": { "from": "06:00", "to": "18:00" },
          "THURSDAY":  { "from": "06:00", "to": "18:00" },
          "FRIDAY":    { "from": "06:00", "to": "18:00" }
        },
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31"
      }
    }]
  }'
```

Create a team (rules fields are nullable — `null` means inherit from site):

```bash
# Response from POST /api/sites gives the site ID — substitute below
curl -s -X POST http://localhost:8080/api/teams \
  -H 'Content-Type: application/json' \
  -d '{ "siteId": "<site-id>", "name": "Day Shift", "rules": { "toleranceMeters": null, "strictModeWindows": null, "approvalRequired": null } }'
```

Create an employee enrolled at a site:

```bash
curl -s -X POST http://localhost:8080/api/employees \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Jane Smith",
    "phoneNumber": "+27821000050",
    "siteEnrollments": { "<site-id>": "<team-id>" },
    "ruleOverrides": null
  }'
```

### Multi-Instance Redis Fan-out (Optional)

By default the service runs in single-node mode — SSE events are pushed to clients on the same JVM instance. To enable Redis-backed cross-instance fan-out:

```bash
# Set via environment variables (or application.properties override)
APP_SSE_REDIS_ENABLED=true
APP_SSE_REDIS_CHANNEL=clock-events          # default, change if needed
SPRING_DATA_REDIS_HOST=<your-redis-host>
SPRING_DATA_REDIS_PORT=6379
```

When `APP_SSE_REDIS_ENABLED=true`, each clock-in serializes the `ClockEvent` as JSON and publishes it to the Redis channel. Every JVM instance subscribes to that channel and forwards deserialized events to its own local SSE emitters.

---

## 2. What I Built

### Implemented

**Data model**
- `ClockEvent` record — stores all clock-in/out data including the client-supplied `eventId` (idempotency key), `ValidationStatus`, and `validationReason`. Key decision: `eventId` doubles as the document ID in the `clocks` collection so the idempotency check is a single map lookup with no separate deduplication store.
- `Site` embeds `List<Geofence>` and `SiteRules`. Key decision: geofences are embedded in the site document rather than a separate collection — they have no independent access pattern, so embedding avoids a second read at clock-in time.
- `Team` and `Employee` use nullable rule fields (`Integer toleranceMeters`, `Boolean approvalRequired`, `List<StrictModeWindow> strictModeWindows`). Key decision: `null` means "inherit from parent" — no sentinel values, no special constants.
- `EffectiveRules` is a simple value record produced by `RuleResolver` — the resolved values, not the raw nullable ones.

**Core validation logic**
- `RuleResolver` — three-level cascade (Site → Team → Employee) resolving each field independently. Strict-mode window check applied last. Key decision: live resolution at clock-in time rather than snapshotting rules at enrollment — means site managers can update rules without touching employee records.
- `GeofenceValidator` — Haversine distance against each temporally-active geofence. Effective radius = `geofence.radius + rules.tolerance + request.accuracyMeters`. Key decision: device accuracy is added to the radius (benefit of the doubt) rather than subtracted — an employee with poor GPS near a boundary is not penalised for hardware quality.
- Temporal check in `GeofenceValidator.isTemporallyActive()` — date range AND day-of-week AND operating hours must all be satisfied before distance is computed. A geofence outside its operating window is skipped entirely.

**API endpoints**
- `POST /api/clocks` — full validation pipeline: idempotency check → site load → employee load → enrollment check → rule resolution → geofence validation → save → notify → SSE publish. Returns HTTP 200 for VALID, INVALID, and PENDING_APPROVAL alike; validation outcome is in the response body. Key decision: returning 200 for INVALID rather than 422 keeps the mobile app's retry logic simple — the event is stored and the response is final.
- `GET /api/clocks`, `GET /api/clocks/{id}` — read paths over the `clocks` collection.
- `GET /api/clocks/stream` — SSE stream via `SseEmitter`. Each processed clock event is broadcast to all connected clients.
- `POST /api/clocks/{id}/approve` and `POST /api/clocks/{id}/reject` — approval resolution; validates that the event exists and is in `PENDING_APPROVAL` state, returns 409 otherwise.
- `POST/GET /api/sites`, `POST/GET /api/teams`, `POST/GET /api/employees` — full management API; server generates IDs; cross-reference validation (team must belong to site; employee enrollment must reference existing site and team).

**Streaming**
- `SsePublisher` holds a `CopyOnWriteArrayList<SseEmitter>` and fans out to all connected clients. `EventBus` interface (`LocalEventBus` / `RedisEventBus`) decouples the publish call from the transport. When `app.sse.redis-enabled=true`, `RedisEventBus` publishes the `ClockEvent` as JSON to a Redis channel and every JVM instance subscribing to that channel re-delivers it to its local emitters.

**WhatsApp notifications**
- `NotificationService` wraps `WhatsAppClient` with an idempotency guard keyed on `"notif:{eventId}"`. Employee notification sent for every event; manager notification sent only for `PENDING_APPROVAL`. Failures are logged and swallowed — clock-in response is never affected by notification failure.
- `DailySummaryService` — `@Scheduled` morning and evening cron, scans today's/yesterday's `ClockEvent`s, groups by `siteId`, sends a formatted summary to each site manager. Gated by `app.summary.enabled=false` by default.

**Tests**
- 100 tests across 13 test classes. Every service component has a dedicated unit test class. `ClockControllerTest` and `ManagementControllerTest` use `@SpringBootTest` + `MockMvc`. `GeofenceValidatorTest` covers 17 cases including temporal boundary conditions. BDD-first: each test class maps to Given/When/Then scenarios written before the implementation.

### What Was Added Beyond the Original Spec

The SPEC.md was written before any implementation and originally scoped out four features due to the 2.5 h time constraint. All four were subsequently implemented:

| Feature | How to enable |
|---------|---------------|
| Management API (`POST/GET /api/sites`, `/api/teams`, `/api/employees`) | Available by default |
| Approval flow (`POST /api/clocks/{id}/approve` + `reject`) | Available by default |
| Daily WhatsApp summary (morning + evening cron) | `APP_SUMMARY_ENABLED=true` |
| Cross-instance SSE fan-out via Redis pub/sub | `APP_SSE_REDIS_ENABLED=true` |

### What Is Out of Scope

| Feature | Reason |
|---------|--------|
| Authentication / authorisation | Not in assessment scope |
| Persistent database | In-memory only per assessment requirement |
| Polygon geofencing | Explicitly excluded by PRD |
| Multi-timezone support | Explicitly excluded by PRD (SAST only) |
| WhatsApp retry / dead-letter queue | Log-and-continue; see Section 4 |
| PATCH / DELETE on Management API | Create + read sufficient for this assessment |
| RedisEventBus end-to-end integration test | Requires live Redis via Testcontainers; see Section 4 |

---

## 3. Key Architectural Decisions

### 1. Client-supplied `eventId` as the idempotency key

**Decision:** The client generates a UUID `eventId` and supplies it in every request. This UUID is the document key in the `clocks` collection. A second `POST /api/clocks` with the same `eventId` returns the stored event immediately — no re-validation, no re-notification.

**Alternative:** Server-generated IDs (the starter code's original behaviour — `UUID.randomUUID()` inside the service). Any mobile-app retry on a failed network response would create a duplicate record.

**Why:** Mobile apps on flaky connections must be able to retry safely. The idempotency contract is the client's responsibility to supply a stable UUID per logical event, which is standard practice for payment-grade APIs.

**Trade-off accepted:** The client must generate and persist the UUID before sending. If the client loses it, re-sending produces a new event. This is the correct trade-off — the server can never know whether a retry is a duplicate or a genuinely new event without a client-supplied key.

---

### 2. Null-inheritance rule resolution rather than snapshot-at-enrollment

**Decision:** `TeamRules` and `EmployeeRules` use nullable fields (`Integer`, `Boolean`, `List`). `null` means "inherit from the parent level". `RuleResolver` resolves each field independently at clock-in time. No rule snapshot is stored at enrollment.

**Alternative:** Snapshot effective rules at enrollment time and store the flattened `EffectiveRules` on the employee record. Resolution becomes a map lookup rather than a cascade.

**Why:** Rules change. A site manager updating `toleranceMeters` on a site should affect every employee at that site immediately. Snapshot semantics would require invalidating or recomputing every employee record on each rule update — operationally expensive and error-prone.

**Trade-off accepted:** Every clock-in incurs the rule resolution cascade (3–4 map lookups). Negligible in-process cost for an in-memory store, but would need caching in a real database.

---

### 3. `INVALID` events return HTTP 200, not HTTP 422

**Decision:** `POST /api/clocks` always returns HTTP 200. The `validationStatus` field in the response body indicates VALID, INVALID, or PENDING_APPROVAL. The event is always persisted.

**Alternative:** Return HTTP 422 Unprocessable Entity for INVALID — the event is not stored, and the mobile app must re-submit if connectivity is restored.

**Why:** The assessment spec says "store the event and communicate the outcome in the response." A mobile app on a slow network retrying a 422 would keep creating new INVALID events. With HTTP 200, the client gets a definitive, idempotent answer: "your event was received, here is the outcome."

**Trade-off accepted:** Downstream analytics will contain INVALID events. This is actually desirable — audit trail of failed clock-in attempts is operationally useful (pattern detection, access disputes).

---

### 4. `EventBus` interface with `LocalEventBus` / `RedisEventBus` implementations

**Decision:** `ClockService` publishes via an `EventBus` interface. `LocalEventBus` fans out in-process. `RedisEventBus` publishes to a Redis pub/sub channel; a `MessageListenerAdapter` subscribes on the same instance and delivers to local SSE emitters. Bean selection is driven by `@ConditionalOnProperty(app.sse.redis-enabled)`.

**Alternative:** Hard-code `SsePublisher` directly in `ClockService`. No abstraction, no Redis path.

**Why:** Single-node SSE is sufficient for the assessment, but the assessment rubric specifically asks about multi-instance deployments. The abstraction costs one interface and two small classes and makes the Redis path opt-in with zero behavioural change for the default single-node case.

**Trade-off accepted:** `RedisEventBus` is not covered by an integration test (no Redis in CI). `EventBusTest` verifies the fan-out contract against a mock; the Redis serialization path is exercised by a unit test that deliberately sends malformed JSON to verify the error-handling path.

---

### 5. HTTP 200 approval endpoints vs. HTTP 204 No Content

**Decision:** `POST /api/clocks/{id}/approve` and `POST /api/clocks/{id}/reject` return the updated `ClockEvent` in the response body with HTTP 200.

**Alternative:** HTTP 204 No Content — client must `GET /api/clocks/{id}` to see the updated state.

**Why:** The assessment reviewers dashboard needs to render the updated event immediately after approval. Returning the full updated event in the POST response eliminates a second round-trip. For a UI that needs to update a row in place, this is the right default.

**Trade-off accepted:** Slightly heavier response payload. For a management operation called once per pending event this is negligible.

---

## 4. What I'd Do Differently

### With more time

**`GET /api/clocks` pagination** — the endpoint returns all events in a single JSON array. `DocumentStore` is a `HashMap` with no ordering guarantee. In production I would add cursor-based pagination keyed on `timestamp` and an index structure in `DocumentStore` to support it. `ClockService.getAll()` currently calls `store.findAll("clocks", ClockEvent.class)` — one line to fix, but the contract change (adding `page`/`size` params) is a breaking API change that needs a version or a deprecation window.

**`WhatsAppClient` retry with dead-letter log** — `NotificationService` currently catches all exceptions from `whatsAppClient.sendMessage()`, logs a WARN, and continues. A real deployment needs exponential backoff (3 attempts, 1 s / 2 s / 4 s) and a dead-letter record so operations can replay failed notifications. The `WhatsAppClient` interface is already in place — adding a `RetryingWhatsAppClient` decorator is the correct pattern without touching `NotificationService`.

**`Testcontainers` integration test for `RedisEventBus`** — `EventBusTest` verifies the fan-out contract using a mock `EventBus`. The actual Redis publish → subscribe → SSE deliver path is not exercised under test. A `Testcontainers`-based `EventBusRedisIntegrationTest` starting a real Redis container would close this gap. The main blocker was CI — the GitHub Actions runner used in this submission does not have Docker available.

**`DailySummaryService` timezone** — `sendSummaryForDate(LocalDate.now())` uses server-local time. On a server running UTC this is wrong for a SAST deployment. I would replace `LocalDate.now()` with `LocalDate.now(ZoneId.of("Africa/Johannesburg"))` and expose it as `app.summary.timezone`.

### Given what I discovered during implementation

**The approval endpoints need their own SPEC section.** The approval flow (`PENDING_APPROVAL → VALID | INVALID`) is simple to implement but introduces a state machine with 409 semantics that I had to design on the fly. I wrote the BDD scenarios for it before coding, but the SPEC's section 6 only covers the initial validation statuses — not the approval transition diagram. If I were writing the spec again I would add a state diagram and document the 409 case (attempt to approve an already-resolved event) explicitly.

**Jackson `ZonedDateTime` needs explicit configuration and the SPEC should say so.** The first version of the clock-in test sent `"2026-05-08T07:30:00+02:00"` and got a deserialisation error because Spring Boot's default Jackson configuration strips timezone information. I had to add `spring.jackson.deserialization.adjust-dates-to-context-time-zone=false` and `spring.jackson.serialization.write-dates-as-timestamps=false` to `application.properties`. The SPEC describes the timestamp format in the API contract section but says nothing about the serialization configuration required to preserve it. That is an omission I would fix.

**The null-inheritance rule design is all-or-nothing for `StrictModeWindow` lists.** Individual numeric fields (`toleranceMeters`) inherit field-by-field, which is clean. But `strictModeWindows` is a list — a team can either replace the whole list or inherit the whole list from the site. There is no way to add one window to the site's list at the team level. I only noticed this when writing the `RuleResolverTest` edge cases. The SPEC implies per-field inheritance but does not call out that list fields are all-or-nothing. I would add an explicit note and, with more time, consider a merge strategy for window lists.

---

## 5. AI Workflow

### Tools and Prompting Strategy

I used **GitHub Copilot in VS Code Agent mode** (`claude-sonnet-4-5`) for all implementation phases. No inline completions — all work was done through the chat panel with explicit, structured prompts.

**Prompting pattern that worked:** Provide the interface or record definition first, then ask for the implementation. For example, when building `RuleResolver`, I shared the `SiteRules`, `TeamRules`, `EmployeeRules`, and `EffectiveRules` record definitions in the prompt before asking for the resolver logic. The agent had the full type signature to work from and produced structurally correct code.

**BDD-first prompting:** For each service component I wrote the Given/When/Then scenarios in plain English, then asked the agent to translate them into JUnit 5 test methods. Only after the failing tests existed did I ask the agent to make them pass. This prevented the agent from over-engineering the implementation to anticipate scenarios I hadn't asked for.

**Spec as ground truth:** Every prompt that touched validation logic included a reference to the relevant SPEC.md section number. "Implement geofence validation per SPEC.md section 7 — temporal check first, then Haversine distance, effective radius = geofence.radius + rules.tolerance + request.accuracyMeters." The agent stayed on-spec when given an explicit section reference.

---

### A Prompt That Failed

**Prompt (paraphrased):** "Implement `RuleResolver` with a three-level cascade — Site is the base, Team overrides non-null fields, Employee overrides non-null fields."

**What the agent produced:** A sentinel-value design. `toleranceMeters` was typed as `int` (primitive), and the model types used `static final int INHERIT = -1`. The resolver checked `if (teamRules.toleranceMeters() != INHERIT)` to detect whether the team had overridden the value.

**Why this was wrong:** `-1` is an arbitrary magic number. It is semantically indistinguishable from a legitimate tolerance of −1 metres (nonsensical but a valid integer). The pattern also does not compose — with three levels of override you end up with `if (value != -1)` scattered through the cascade, and there is no type-level guarantee that a consumer won't pass a raw `int`. More importantly, the assessment spec explicitly models override absence as the absence of a value — `null` is the right encoding, not a sentinel.

**How it was fixed — two iterations:**
1. Rejected immediately and re-prompted: "Use `Integer` (boxed, nullable) for `toleranceMeters` in `TeamRules` and `EmployeeRules`. `null` means inherit from the parent level. Do not use sentinel values. Each field — `toleranceMeters`, `approvalRequired`, `strictModeWindows` — resolves independently."
2. The second attempt produced the null-inheritance pattern now in `RuleResolver.java` (lines 44–66), which was accepted without further changes.

Other failures worth noting in one sentence each: the agent initially generated `@SpringBootTest` with a full application context for unit-level `RuleResolverTest` — rejected and replaced with plain-construction tests that run in milliseconds. The agent also generated `RedisConfig` with an inline `new ObjectMapper()` that lacked the `JavaTimeModule` needed for `ZonedDateTime` — caught during the first `EventBusTest` run and corrected.

---

### What I Did NOT Use AI For

**`GeofenceValidator` — Haversine formula and temporal logic.** The agent produced the Haversine formula correctly on the first attempt, so this is not a "rejection" case. But I manually verified the formula against the known result for Johannesburg CBD to OR Tambo (~25 km) before accepting it. The temporal boundary logic (`isTemporallyActive`) I wrote by hand after the agent's first version missed the case where a geofence schedule has no entry for the clock's `DayOfWeek` — it returned `true` (active) for missing days rather than `false` (inactive). Returning `true` for an unconfigured day is a security defect: a geofence would be permanently open on days the site is closed. I rewrote that method without AI assistance.

**All architectural decisions and trade-off rationale in SPEC.md and ADRs.** The five decisions in Section 3 of this README — idempotency key ownership, null-inheritance vs. snapshot, HTTP 200 for INVALID, the `EventBus` abstraction, and approval endpoint response shape — were all made by me without prompting the agent. I wrote the rationale before writing the code. This is important: AI is good at implementing a decision you've made; it is not reliable at making the decision for you.

**HTTP status code choices.** I explicitly did not ask the agent what status code to return for an INVALID clock event. I knew from experience that 200 vs 422 is a product decision (does the mobile app need to retry?), not a coding problem. The agent, if asked, would have defaulted to the HTTP-semantically "correct" 422 — which is wrong for this domain.

**Edge-case test design for `GeofenceValidatorTest`.** The 17 test cases in `GeofenceValidatorTest` — boundary conditions like a coordinate exactly on the radius edge, a geofence active on weekdays but clocked on Saturday, a date-range boundary at `effectiveFrom` and `effectiveTo` — were written by me before asking the agent to implement `GeofenceValidator`. The agent cannot reason about domain boundary conditions without being told what they are; it will implement the happy path and one obvious failure mode.

---

### How I Verified AI-Generated Code

**Test suite as the primary gate.** After every agent-generated file I ran `.\mvnw.cmd clean verify` before accepting the output. No agent output was accepted without a green build. This caught the `DayOfWeek` null-return bug in `GeofenceValidator`, the missing `JavaTimeModule` in `RedisConfig`, and the `@SpringBootTest` misuse in unit tests.

**Manual diff review for service logic.** For `ClockService`, `RuleResolver`, and `GeofenceValidator` I read every line of the generated output against the SPEC before running tests. These three classes contain the business-critical logic where an off-by-one or a wrong `<=` vs `<` would produce silent wrong answers that tests might not catch.

**Limits given the time constraint.** I did not review `ManagementController`, `DailySummaryService`, or `SsePublisher` line-by-line after they were generated — I relied on the test suite for those. The `DailySummaryService` timezone issue (using server-local `LocalDate.now()` instead of SAST) was not caught by any test and was only identified during the Pass 2 documentation review. This is the honest limit: the test suite validates behaviour that was specced; it cannot catch behaviour that was never specced.

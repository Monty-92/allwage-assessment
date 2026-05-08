# ADR-002 — Use Server-Sent Events (SSE) for Real-Time Clock-Event Streaming

**Status:** Accepted  
**Date:** 2026-05-08  
**Deciders:** Candidate

---

## Context

The assessment requires a real-time streaming endpoint that publishes clock events to a dashboard client. Two common approaches were considered:

- **Server-Sent Events (SSE)** — unidirectional server-to-client push over a standard HTTP/1.1 connection using `text/event-stream`.
- **WebSocket** — bidirectional, full-duplex connection requiring an HTTP upgrade handshake.

---

## Decision

**Use Server-Sent Events via Spring's `SseEmitter`.**

Endpoint: `GET /api/clocks/stream`  
Content-Type: `text/event-stream`  
Event type: `clock-event`  
Data: `ClockEvent` serialised as JSON

---

## Rationale

| Criterion | SSE | WebSocket |
|-----------|-----|-----------|
| Data direction | Server → client only | Bidirectional |
| Use case match | Dashboard read-only feed | Needed for two-way comms |
| HTTP proxy compatibility | Yes — standard HTTP | Requires upgrade; some proxies block |
| Spring support | `SseEmitter` — no extra dependency | Spring WebSocket / STOMP |
| Client reconnect | Built in (browser `EventSource`) | Manual reconnect logic |
| Implementation complexity | Low | Medium–High |

The dashboard is a read-only consumer. There is no scenario where the client needs to send data back over the stream. SSE is precisely the right tool; WebSocket would add bidirectional overhead with no benefit.

---

## Consequences

### Positive

- No handshake upgrade; works through standard HTTP/1.1 reverse proxies.
- Spring's `SseEmitter` is available on the existing `spring-boot-starter-web` dependency — no new dependencies.
- Browser `EventSource` API handles automatic reconnection.

### Negative / Accepted

- **Single-instance limitation.** `SseEmitter` connections are held in JVM memory. In a horizontally-scaled deployment, a client connected to instance A will not receive events processed by instance B. This is a documented limitation.
- **Production remediation:** Replace `SsePublisher`'s in-memory emitter registry with a Redis pub/sub or Kafka topic fan-out. Each JVM subscribes and forwards events to its own connected clients.

---

## Rejected Alternative

**WebSocket (Spring WebSocket / STOMP)**

- Requires bidirectional capability not needed by any current use case.
- Adds `spring-boot-starter-websocket` dependency and STOMP broker configuration.
- Some enterprise HTTP proxies block WebSocket upgrade requests; SSE avoids this.

package com.allwage.clockin.service;

import com.allwage.clockin.model.ClockEvent;
import org.springframework.lang.NonNull;

/**
 * Abstraction over the event-publishing mechanism for clock events.
 *
 * <p>Two implementations are provided:
 * <ul>
 *   <li>{@link LocalEventBus} — in-process fan-out to SSE emitters on this JVM instance (default).
 *   <li>{@link RedisEventBus} — publishes to a Redis channel so every JVM instance in a
 *       multi-node deployment forwards the event to its own connected SSE clients.
 * </ul>
 *
 * <p>Active implementation is selected via {@code app.sse.redis-enabled} (default: {@code false}).
 */
public interface EventBus {

    /** Publish {@code event} to all connected SSE clients. */
    void publish(@NonNull ClockEvent event);
}

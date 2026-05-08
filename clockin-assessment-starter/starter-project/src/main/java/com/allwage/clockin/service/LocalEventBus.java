package com.allwage.clockin.service;

import com.allwage.clockin.model.ClockEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Single-instance {@link EventBus} implementation.
 *
 * <p>Publishes clock events directly to the in-memory {@link SsePublisher} on this JVM.
 * Active when {@code app.sse.redis-enabled=false} (the default).
 *
 * <p>This is correct for single-node deployments. In a horizontally-scaled deployment,
 * only the clients connected to this instance will receive events processed here.
 * Use {@link RedisEventBus} to broadcast across all instances.
 */
@Component
@ConditionalOnProperty(name = "app.sse.redis-enabled", havingValue = "false", matchIfMissing = true)
public class LocalEventBus implements EventBus {

    private final SsePublisher ssePublisher;

    public LocalEventBus(@NonNull SsePublisher ssePublisher) {
        this.ssePublisher = ssePublisher;
    }

    @Override
    public void publish(@NonNull ClockEvent event) {
        throw new UnsupportedOperationException("not implemented");
    }
}

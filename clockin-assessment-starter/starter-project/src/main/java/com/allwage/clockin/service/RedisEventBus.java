package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.ClockEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Cross-instance {@link EventBus} implementation backed by Redis pub/sub.
 *
 * <p>Active when {@code app.sse.redis-enabled=true}. This implementation:
 * <ol>
 *   <li>On {@link #publish}: serializes the {@link ClockEvent} to JSON and sends it to the
 *       configured Redis channel ({@code app.sse.redis-channel}).
 *   <li>On {@link #onMessage}: receives messages from Redis (subscribed via
 *       {@link com.allwage.clockin.config.RedisConfig}), deserializes the JSON payload, and
 *       forwards the event to the local {@link SsePublisher} so all SSE clients on this JVM
 *       instance receive the event regardless of which instance originally processed the clock-in.
 * </ol>
 *
 * <p>This ensures that in a multi-node deployment all instances receive all events.
 */
@Component
@ConditionalOnProperty(name = "app.sse.redis-enabled", havingValue = "true")
public class RedisEventBus implements EventBus, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventBus.class);

    private final SsePublisher ssePublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;

    public RedisEventBus(
            @NonNull SsePublisher ssePublisher,
            @NonNull StringRedisTemplate redisTemplate,
            @NonNull ObjectMapper objectMapper,
            @NonNull AppProperties appProperties) {
        this.ssePublisher  = ssePublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
        this.channel       = appProperties.getSse().getRedisChannel();
    }

    /** Publish by sending the JSON-serialized event to the Redis pub/sub channel. */
    @Override
    public void publish(@NonNull ClockEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.error("Failed to publish ClockEvent to Redis channel: eventId={}", event.id(), e);
        }
    }

    /**
     * Called by Spring's {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}
     * when a message arrives on the subscribed channel.
     * Deserializes the payload and forwards to local SSE emitters.
     */
    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            ClockEvent event = objectMapper.readValue(message.getBody(), ClockEvent.class);
            ssePublisher.publish(event);
        } catch (IOException e) {
            log.error("Failed to deserialize ClockEvent from Redis channel: {}", e.getMessage(), e);
        }
    }
}

package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BDD — EventBus implementations.
 *
 * LocalEventBus:
 *   GIVEN LocalEventBus
 *     WHEN publish(event) is called
 *     THEN SsePublisher.publish(event) is called once
 *
 * RedisEventBus — publish:
 *   GIVEN RedisEventBus with mocked StringRedisTemplate
 *     WHEN publish(event) is called
 *     THEN convertAndSend is called with the configured channel and JSON payload
 *     AND SsePublisher.publish() is NOT called directly (fan-out goes through Redis)
 *
 *   GIVEN RedisEventBus and Redis is unavailable (convertAndSend throws RuntimeException)
 *     WHEN publish(event) is called
 *     THEN no exception propagates to the caller
 *
 * RedisEventBus — onMessage (subscriber side):
 *   GIVEN a valid JSON RedisMessage for a ClockEvent
 *     WHEN onMessage is received
 *     THEN SsePublisher.publish(deserializedEvent) is called once
 *
 *   GIVEN a malformed (non-JSON) RedisMessage
 *     WHEN onMessage is received
 *     THEN no exception is thrown (error is absorbed)
 */
@ExtendWith(MockitoExtension.class)
class EventBusTest {

    private static final String CHANNEL = "clock-events";
    private static final String EVENT_ID = "bb000000-0000-0000-0000-000000000001";

    @Mock
    private SsePublisher ssePublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ObjectMapper objectMapper;
    private ClockEvent sampleEvent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        sampleEvent = new ClockEvent(
                EVENT_ID, "emp-1", "site-1",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 5.0,
                ClockType.IN, ValidationStatus.VALID, null);
    }

    private AppProperties propsWithChannel(String channel) {
        AppProperties props = new AppProperties();
        props.getSse().setRedisChannel(channel);
        return props;
    }

    // --------------------------------------------------------
    // LocalEventBus
    // --------------------------------------------------------

    @Test
    void localEventBus_publish_delegatesToSsePublisher() {
        LocalEventBus bus = new LocalEventBus(ssePublisher);

        bus.publish(sampleEvent);

        verify(ssePublisher, times(1)).publish(sampleEvent);
    }

    // --------------------------------------------------------
    // RedisEventBus — publish
    // --------------------------------------------------------

    @Test
    void redisEventBus_publish_sendsJsonToRedisChannel() {
        RedisEventBus bus = new RedisEventBus(ssePublisher, redisTemplate, objectMapper,
                propsWithChannel(CHANNEL));

        bus.publish(sampleEvent);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).convertAndSend(eq(CHANNEL), payloadCaptor.capture());
        // Payload must be valid JSON containing the event ID
        assertThat(payloadCaptor.getValue()).contains(EVENT_ID);
    }

    @Test
    void redisEventBus_publish_doesNotCallLocalSsePublisher() {
        RedisEventBus bus = new RedisEventBus(ssePublisher, redisTemplate, objectMapper,
                propsWithChannel(CHANNEL));

        bus.publish(sampleEvent);

        verifyNoInteractions(ssePublisher);
    }

    @Test
    void redisEventBus_publish_redisUnavailable_doesNotPropagateException() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());
        RedisEventBus bus = new RedisEventBus(ssePublisher, redisTemplate, objectMapper,
                propsWithChannel(CHANNEL));

        assertThatNoException().isThrownBy(() -> bus.publish(sampleEvent));
        verifyNoInteractions(ssePublisher);
    }

    // --------------------------------------------------------
    // RedisEventBus — onMessage (subscriber side)
    // --------------------------------------------------------

    @Test
    void redisEventBus_onMessage_forwardsDeserializedEventToSsePublisher() throws Exception {
        RedisEventBus bus = new RedisEventBus(ssePublisher, redisTemplate, objectMapper,
                propsWithChannel(CHANNEL));

        String json = objectMapper.writeValueAsString(sampleEvent);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        DefaultMessage message = new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8), body);

        bus.onMessage(message, null);

        ArgumentCaptor<ClockEvent> eventCaptor = ArgumentCaptor.forClass(ClockEvent.class);
        verify(ssePublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().id()).isEqualTo(EVENT_ID);
        assertThat(eventCaptor.getValue().validationStatus()).isEqualTo(ValidationStatus.VALID);
    }

    @Test
    void redisEventBus_onMessage_malformedJson_doesNotThrow() {
        RedisEventBus bus = new RedisEventBus(ssePublisher, redisTemplate, objectMapper,
                propsWithChannel(CHANNEL));

        byte[] garbage = "not-valid-json".getBytes(StandardCharsets.UTF_8);
        DefaultMessage message = new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8), garbage);

        assertThatNoException().isThrownBy(() -> bus.onMessage(message, null));
        verifyNoInteractions(ssePublisher);
    }
}

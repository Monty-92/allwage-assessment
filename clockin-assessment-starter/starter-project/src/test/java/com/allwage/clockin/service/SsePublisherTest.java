package com.allwage.clockin.service;

import com.allwage.clockin.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * BDD — SsePublisher unit tests.
 *
 * GIVEN no connected subscribers
 *   WHEN publish() is called
 *   THEN no exception is thrown
 *
 * GIVEN a subscriber is registered
 *   WHEN subscribe() is called
 *   THEN a non-null SseEmitter is returned and a subsequent publish() does not throw
 */
class SsePublisherTest {

    private static final ClockEvent SAMPLE_EVENT = new ClockEvent(
            "evt-sse-1", "emp-1", "site-1",
            ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
            -26.2041, 28.0473, 5.0,
            ClockType.IN, ValidationStatus.VALID, null);

    @Test
    void publish_withNoSubscribers_doesNotThrow() {
        SsePublisher publisher = new SsePublisher();

        assertThatNoException().isThrownBy(() -> publisher.publish(SAMPLE_EVENT));
    }

    @Test
    void subscribe_returnsNonNullEmitter_andPublishDoesNotThrow() {
        SsePublisher publisher = new SsePublisher();

        SseEmitter emitter = publisher.subscribe(5000);

        assertThat(emitter).isNotNull();
        assertThatNoException().isThrownBy(() -> publisher.publish(SAMPLE_EVENT));
    }
}

package com.allwage.clockin.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD — @ConditionalOnProperty wiring for LocalEventBus.
 *
 *   GIVEN app.sse.redis-enabled=false
 *     WHEN the Spring context starts
 *     THEN EventBus resolves to LocalEventBus
 */
@SpringBootTest
@TestPropertySource(properties = "app.sse.redis-enabled=false")
class EventBusLocalWiringTest {

    @Autowired
    private EventBus eventBus;

    @Test
    void whenRedisDisabled_eventBusIsLocalEventBus() {
        assertThat(eventBus).isInstanceOf(LocalEventBus.class);
    }
}

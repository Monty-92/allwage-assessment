package com.allwage.clockin.service;

import com.allwage.clockin.model.ClockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE emitters and broadcasts clock events to all connected clients.
 *
 * Known limitation: single-instance only — emitters are held in JVM memory.
 * In a multi-instance deployment each instance maintains its own set of emitters;
 * clients connected to instance A will not receive events processed by instance B.
 * Production fix: Redis pub/sub or Kafka topic fan-out per instance.
 *
 * See ADR-002.
 */
@Component
public class SsePublisher {

    private static final Logger log = LoggerFactory.getLogger(SsePublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Register a new SSE emitter for a connecting client. */
    public SseEmitter subscribe(long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs == 0 ? Long.MAX_VALUE : timeoutMs);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** Publish a clock event to all connected clients. */
    public void publish(ClockEvent event) {
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("clock-event")
                        .data(event));
            } catch (Exception e) {
                log.warn("Failed to send SSE event to emitter; removing. eventId={}", event.id());
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}

package com.allwage.clockin.controller;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.ClockEvent;
import com.allwage.clockin.service.ClockService;
import com.allwage.clockin.service.SsePublisher;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST controller for clock-in/out operations and the SSE dashboard stream.
 */
@RestController
@RequestMapping("/api/clocks")
public class ClockController {

    private final ClockService clockService;
    private final SsePublisher ssePublisher;
    private final AppProperties appProperties;

    public ClockController(@NonNull ClockService clockService,
                           @NonNull SsePublisher ssePublisher,
                           @NonNull AppProperties appProperties) {
        this.clockService = clockService;
        this.ssePublisher = ssePublisher;
        this.appProperties = appProperties;
    }

    /**
     * Process a clock-in or clock-out event from the mobile app.
     * The client-supplied eventId is used as the idempotency key.
     * Duplicate requests for the same eventId return the stored event unchanged.
     */
    @PostMapping
    public @NonNull ResponseEntity<ClockEvent> clock(
            @Valid @RequestBody @NonNull ClockRequest request) {
        ClockEvent event = clockService.processClock(request);
        return ResponseEntity.ok(event);
    }

    /**
     * SSE stream for the real-time dashboard.
     * Clients connect once and receive a "clock-event" message for every processed event.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return ssePublisher.subscribe(appProperties.getSse().getEmitterTimeoutMs());
    }

    /**
     * Get all clock events.
     */
    @GetMapping
    public @NonNull ResponseEntity<List<ClockEvent>> getAll() {
        return ResponseEntity.ok(clockService.findAll());
    }

    /**
     * Get a specific clock event by ID.
     */
    @GetMapping("/{id}")
    public @NonNull ResponseEntity<ClockEvent> getById(@PathVariable @NonNull String id) {
        return clockService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Approve a PENDING_APPROVAL clock event — transitions it to VALID.
     * Returns 409 Conflict if the event is not in PENDING_APPROVAL state.
     */
    @PatchMapping("/{id}/approve")
    public @NonNull ResponseEntity<ClockEvent> approve(@PathVariable @NonNull String id) {
        return ResponseEntity.ok(clockService.approve(id));
    }

    /**
     * Reject a PENDING_APPROVAL clock event — transitions it to INVALID.
     * Returns 409 Conflict if the event is not in PENDING_APPROVAL state.
     */
    @PatchMapping("/{id}/reject")
    public @NonNull ResponseEntity<ClockEvent> reject(@PathVariable @NonNull String id) {
        return ResponseEntity.ok(clockService.reject(id));
    }
}

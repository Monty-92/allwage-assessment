package com.allwage.clockin.controller;

import com.allwage.clockin.model.ClockEvent;
import com.allwage.clockin.service.ClockService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for clock-in/out operations.
 *
 * This provides a basic endpoint for receiving clock events.
 * You'll need to extend this with validation, error handling,
 * and additional endpoints as required.
 */
@RestController
@RequestMapping("/api/clocks")
public class ClockController {

    private final ClockService clockService;

    public ClockController(@NonNull ClockService clockService) {
        this.clockService = clockService;
    }

    /**
     * Process a clock-in or clock-out event from the mobile app.
     */
    @PostMapping
    public @NonNull ResponseEntity<ClockEvent> clock(@Valid @RequestBody @NonNull ClockRequest request) {
        ClockEvent event = new ClockEvent(
            UUID.randomUUID().toString(),
            request.employeeId(),
            request.timestamp(),
            request.latitude(),
            request.longitude(),
            request.accuracyMeters(),
            request.type()
        );

        ClockEvent saved = clockService.processClock(event);
        return ResponseEntity.ok(saved);
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
}

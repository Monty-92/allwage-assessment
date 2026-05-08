package com.allwage.clockin.model;

import java.time.ZonedDateTime;

/**
 * Represents a clock-in or clock-out event received from the mobile app.
 *
 * This is the raw event as received - your implementation should determine
 * whether this clock is valid based on geofencing rules.
 *
 * All timestamps are in SAST (South African Standard Time, UTC+2).
 */
public record ClockEvent(
    String id,
    String employeeId,
    ZonedDateTime timestamp,
    double latitude,
    double longitude,
    double accuracyMeters,
    ClockType type
) {
    public enum ClockType {
        IN, OUT
    }
}

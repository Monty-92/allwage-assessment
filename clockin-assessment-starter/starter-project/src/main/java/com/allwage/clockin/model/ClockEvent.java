package com.allwage.clockin.model;

import java.time.ZonedDateTime;

/**
 * Immutable record of a single clock-in or clock-out event.
 * id == eventId from the request (client-supplied idempotency key).
 * validationReason is null when validationStatus is VALID.
 */
public record ClockEvent(
        String id,
        String employeeId,
        String siteId,
        ZonedDateTime timestamp,
        double latitude,
        double longitude,
        double accuracyMeters,
        ClockType type,
        ValidationStatus validationStatus,
        String validationReason
) {}

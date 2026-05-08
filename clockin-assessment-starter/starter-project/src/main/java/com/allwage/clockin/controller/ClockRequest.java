package com.allwage.clockin.controller;

import com.allwage.clockin.model.ClockEvent.ClockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

/**
 * Request body for clock-in/out endpoint.
 *
 * Timestamps should be provided in ISO-8601 format.
 * All times are assumed to be in SAST (South African Standard Time, UTC+2).
 */
public record ClockRequest(
    @NotBlank(message = "Employee ID is required")
    String employeeId,

    @NotNull(message = "Timestamp is required")
    ZonedDateTime timestamp,

    @NotNull(message = "Latitude is required")
    Double latitude,

    @NotNull(message = "Longitude is required")
    Double longitude,

    @NotNull(message = "Accuracy is required")
    Double accuracyMeters,

    @NotNull(message = "Clock type is required")
    ClockType type
) {}

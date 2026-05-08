package com.allwage.clockin.controller;

import com.allwage.clockin.model.ClockType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.ZonedDateTime;

/**
 * Request body for POST /api/clocks.
 * eventId is the client-supplied idempotency key — used as the document ID.
 * siteId must identify a site in which the employee is enrolled.
 */
public record ClockRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                 message = "eventId must be a valid UUID")
        String eventId,
        @NotBlank String employeeId,
        @NotBlank String siteId,
        @NotNull ZonedDateTime timestamp,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Min(0) Double accuracyMeters,
        @NotNull ClockType type
) {}

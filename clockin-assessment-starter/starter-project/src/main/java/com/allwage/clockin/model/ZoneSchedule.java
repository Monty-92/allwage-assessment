package com.allwage.clockin.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Temporal schedule for a geofence.
 * A geofence is active only when BOTH conditions hold:
 * 1. The clock date is within [effectiveFrom, effectiveTo] (both inclusive)
 * 2. The clock day-of-week has an entry in operatingHours AND the time is within that range
 */
public record ZoneSchedule(
        Map<DayOfWeek, TimeRange> operatingHours,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

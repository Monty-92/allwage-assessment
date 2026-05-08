package com.allwage.clockin.service;

import com.allwage.clockin.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Validates a GPS coordinate against a list of geofences.
 * Uses the Haversine formula for distance calculation.
 * Applies temporal checks (date range + day-of-week + operating hours) before distance check.
 */
@Component
public class GeofenceValidator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * Validates a clock event location against all active geofences.
     *
     * @param geofences     list of geofences to check (from the site)
     * @param lat           event latitude
     * @param lon           event longitude
     * @param accuracyMeters device GPS accuracy (added to effective radius)
     * @param rules         resolved effective rules (tolerance, approvalRequired)
     * @param clockTime     event timestamp (used for temporal checks)
     * @return VALID if within any active geofence, PENDING_APPROVAL or INVALID otherwise
     */
    public ValidationStatus validate(List<Geofence> geofences, double lat, double lon,
                                     double accuracyMeters, EffectiveRules rules,
                                     ZonedDateTime clockTime) {
        for (Geofence geofence : geofences) {
            if (!isTemporallyActive(geofence.schedule(), clockTime)) {
                continue;
            }
            double distance = haversineDistance(lat, lon, geofence.centerLat(), geofence.centerLon());
            double effectiveRadius = geofence.radiusMeters() + rules.toleranceMeters() + accuracyMeters;
            if (distance <= effectiveRadius) {
                return ValidationStatus.VALID;
            }
        }
        return rules.approvalRequired()
                ? ValidationStatus.PENDING_APPROVAL
                : ValidationStatus.INVALID;
    }

    /**
     * Returns true if the geofence is active at the given time.
     * Both date-range and time-of-day must be satisfied.
     */
    boolean isTemporallyActive(ZoneSchedule schedule, ZonedDateTime clockTime) {
        LocalDate date = clockTime.toLocalDate();
        if (date.isBefore(schedule.effectiveFrom()) || date.isAfter(schedule.effectiveTo())) {
            return false;
        }
        TimeRange range = schedule.operatingHours().get(clockTime.getDayOfWeek());
        if (range == null) {
            return false;
        }
        return range.contains(clockTime.toLocalTime());
    }

    /**
     * Haversine great-circle distance between two lat/lon points, in metres.
     * R = 6,371,000 m (hardcoded physical constant — not configurable).
     */
    double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}

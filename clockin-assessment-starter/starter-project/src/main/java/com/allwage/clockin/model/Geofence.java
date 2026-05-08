package com.allwage.clockin.model;

/**
 * A circular geofence boundary attached to a site.
 * radiusMeters is the base radius; tolerance from rules is added at validation time.
 */
public record Geofence(
        String id,
        String name,
        double centerLat,
        double centerLon,
        double radiusMeters,
        ZoneSchedule schedule
) {}

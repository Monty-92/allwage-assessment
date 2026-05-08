package com.allwage.clockin.model;

import java.time.LocalTime;

/**
 * A time window during which a tighter geofence tolerance is applied.
 * toleranceMeters is the override for this window; if not set at this level,
 * the app.geofence.strict-mode-tolerance-meters property applies.
 */
public record StrictModeWindow(LocalTime from, LocalTime to, Integer toleranceMeters) {

    /** Returns true if the given time falls within this window [from, to). */
    public boolean contains(LocalTime time) {
        return !time.isBefore(from) && time.isBefore(to);
    }
}

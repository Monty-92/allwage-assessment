package com.allwage.clockin.model;

import java.time.LocalTime;

/**
 * Operating hours for a single day: [from, to) — from inclusive, to exclusive.
 */
public record TimeRange(LocalTime from, LocalTime to) {

    /** Returns true if the given time falls within [from, to). */
    public boolean contains(LocalTime time) {
        return !time.isBefore(from) && time.isBefore(to);
    }
}

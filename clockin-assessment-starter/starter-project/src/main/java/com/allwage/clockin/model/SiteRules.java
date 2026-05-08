package com.allwage.clockin.model;

import java.util.List;

/**
 * Default validation rules for a site. {@code toleranceMeters} may be {@code null}, in which
 * case {@code app.geofence.default-tolerance-meters} is used as the fallback.
 */
public record SiteRules(
        Integer toleranceMeters,
        List<StrictModeWindow> strictModeWindows,
        boolean approvalRequired
) {}

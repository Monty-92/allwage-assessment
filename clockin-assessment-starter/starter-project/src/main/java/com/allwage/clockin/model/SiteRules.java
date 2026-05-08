package com.allwage.clockin.model;

import java.util.List;

/**
 * Default validation rules for a site. All fields are non-nullable — the site is the root of
 * the rule hierarchy and every field must have a concrete value.
 */
public record SiteRules(
        int toleranceMeters,
        List<StrictModeWindow> strictModeWindows,
        boolean approvalRequired
) {}

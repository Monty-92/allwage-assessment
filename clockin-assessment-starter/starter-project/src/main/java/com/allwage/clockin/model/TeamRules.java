package com.allwage.clockin.model;

import java.util.List;

/**
 * Team-level rule overrides. Null fields mean "inherit from SiteRules".
 * Each field is resolved independently — a team can override only tolerance
 * while inheriting approvalRequired.
 */
public record TeamRules(
        Integer toleranceMeters,
        List<StrictModeWindow> strictModeWindows,
        Boolean approvalRequired
) {}

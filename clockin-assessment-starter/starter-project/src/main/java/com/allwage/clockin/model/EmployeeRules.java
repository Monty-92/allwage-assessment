package com.allwage.clockin.model;

import java.util.List;

/**
 * Employee-level rule overrides per site. Null fields mean "inherit from TeamRules".
 * Stored in Employee.ruleOverrides keyed by siteId.
 */
public record EmployeeRules(
        Integer toleranceMeters,
        List<StrictModeWindow> strictModeWindows,
        Boolean approvalRequired
) {}

package com.allwage.clockin.model;

import java.util.Map;

/**
 * An employee who can clock in at one or more sites.
 * siteEnrollments maps siteId to teamId — an employee belongs to exactly one team per site.
 * ruleOverrides maps siteId to per-site EmployeeRules (nullable fields = inherit from Team).
 */
public record Employee(
        String id,
        String name,
        String phoneNumber,
        Map<String, String> siteEnrollments,
        Map<String, EmployeeRules> ruleOverrides
) {}

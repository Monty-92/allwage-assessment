package com.allwage.clockin.controller;

import com.allwage.clockin.model.EmployeeRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for POST /api/employees.
 * Each siteEnrollment entry maps siteId → teamId. The service validates that the site
 * and team both exist and that the team belongs to the referenced site.
 * ruleOverrides is nullable — null means no per-site overrides for this employee.
 */
public record CreateEmployeeRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber,
        @NotNull Map<String, String> siteEnrollments,
        Map<String, EmployeeRules> ruleOverrides) {
}

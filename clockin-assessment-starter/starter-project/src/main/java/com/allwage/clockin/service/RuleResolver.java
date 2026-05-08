package com.allwage.clockin.service;

import com.allwage.clockin.model.*;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Resolves the effective validation rules for an employee at a site at a specific time.
 * Applies the three-level cascade: Site -> Team -> Employee, each level overriding
 * non-null fields from the level below. Then applies strict mode window check.
 */
@Component
public class RuleResolver {

    /**
     * Resolves effective rules for the given employee at the given site.
     *
     * @param site       the site (provides base SiteRules)
     * @param team       the team the employee belongs to at this site
     * @param employee   the employee
     * @param siteId     the site ID (used to look up employee-level overrides)
     * @param clockTime  the clock event timestamp (used for strict mode check)
     * @return fully resolved EffectiveRules
     */
    public EffectiveRules resolveRules(Site site, Team team, Employee employee,
                                       String siteId, ZonedDateTime clockTime) {
        SiteRules base = site.rules();
        if (base == null) {
            throw new IllegalStateException("Site " + site.id() + " has no rules configured");
        }

        // Level 1: start with site defaults
        int tolerance = base.toleranceMeters();
        List<StrictModeWindow> strictWindows = base.strictModeWindows();
        boolean approvalRequired = base.approvalRequired();

        // Level 2: apply team overrides (null = inherit)
        TeamRules teamRules = team.rules();
        if (teamRules != null) {
            if (teamRules.toleranceMeters() != null) tolerance = teamRules.toleranceMeters();
            if (teamRules.strictModeWindows() != null) strictWindows = teamRules.strictModeWindows();
            if (teamRules.approvalRequired() != null) approvalRequired = teamRules.approvalRequired();
        }

        // Level 3: apply employee-level overrides for this site (null = inherit)
        EmployeeRules empRules = employee.ruleOverrides() != null
                ? employee.ruleOverrides().get(siteId)
                : null;
        if (empRules != null) {
            if (empRules.toleranceMeters() != null) tolerance = empRules.toleranceMeters();
            if (empRules.strictModeWindows() != null) strictWindows = empRules.strictModeWindows();
            if (empRules.approvalRequired() != null) approvalRequired = empRules.approvalRequired();
        }

        // Strict mode: if clock time falls inside any window, override tolerance
        if (strictWindows != null) {
            for (StrictModeWindow window : strictWindows) {
                if (window.contains(clockTime.toLocalTime())) {
                    tolerance = window.toleranceMeters();
                    break;
                }
            }
        }

        return new EffectiveRules(tolerance, approvalRequired);
    }
}

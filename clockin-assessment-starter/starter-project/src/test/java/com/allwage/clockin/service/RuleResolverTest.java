package com.allwage.clockin.service;

import com.allwage.clockin.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BDD Priority 1 — Rule hierarchy resolution.
 *
 * GIVEN three employees at site-alpha with different team enrollments and overrides
 * WHEN resolveRules is called for each
 * THEN the effective tolerance and approvalRequired reflect the correct cascade
 */
class RuleResolverTest {

    private RuleResolver resolver;
    private Site siteAlpha;
    private Team teamDayShift;
    private Team teamContractors;
    private Employee alice;
    private Employee bob;
    private Employee john;

    // A Tuesday at 08:00 SAST — not inside any strict mode window
    private static final ZonedDateTime TUESDAY_MORNING =
            ZonedDateTime.of(2026, 5, 5, 8, 0, 0, 0, ZoneOffset.ofHours(2));

    @BeforeEach
    void setUp() {
        resolver = new RuleResolver();

        SiteRules siteRules = new SiteRules(30, List.of(), false);
        siteAlpha = new Site("site-alpha", "Alpha Construction", "+27821000001", siteRules, List.of());

        // Day Shift — all nulls (inherits site)
        teamDayShift = new Team("team-day-shift", "site-alpha", "Day Shift",
                new TeamRules(null, null, null));

        // Contractors — override tolerance=10, approval=true
        teamContractors = new Team("team-contractors", "site-alpha", "Contractors",
                new TeamRules(10, null, true));

        // Alice — day-shift, no overrides
        alice = new Employee("emp-alice", "Alice", "+27821000002",
                Map.of("site-alpha", "team-day-shift"),
                Map.of());

        // Bob — contractors, no overrides
        bob = new Employee("emp-bob", "Bob", "+27821000003",
                Map.of("site-alpha", "team-contractors"),
                Map.of());

        // John — contractors, override tolerance=50
        john = new Employee("emp-john", "John", "+27821000004",
                Map.of("site-alpha", "team-contractors"),
                Map.of("site-alpha", new EmployeeRules(50, null, null)));
    }

    // ---------- GIVEN Alice at site-alpha (day-shift, no overrides) ----------

    /**
     * GIVEN Alice enrolled in Day Shift (all rules null)
     * WHEN resolveRules for site-alpha at 08:00
     * THEN tolerance=30 (site base), approvalRequired=false (site base)
     */
    @Test
    void alice_inheritsAllFromSite() {
        EffectiveRules rules = resolver.resolveRules(siteAlpha, teamDayShift, alice, "site-alpha", TUESDAY_MORNING);

        assertThat(rules.toleranceMeters()).isEqualTo(30);
        assertThat(rules.approvalRequired()).isFalse();
    }

    // ---------- GIVEN Bob at site-alpha (contractors, no overrides) ----------

    /**
     * GIVEN Bob enrolled in Contractors (tolerance=10, approval=true)
     * WHEN resolveRules for site-alpha at 08:00
     * THEN tolerance=10 (team), approvalRequired=true (team)
     */
    @Test
    void bob_inheritsFromTeam() {
        EffectiveRules rules = resolver.resolveRules(siteAlpha, teamContractors, bob, "site-alpha", TUESDAY_MORNING);

        assertThat(rules.toleranceMeters()).isEqualTo(10);
        assertThat(rules.approvalRequired()).isTrue();
    }

    // ---------- GIVEN John at site-alpha (contractors + employee override) ----------

    /**
     * GIVEN John enrolled in Contractors, employee override tolerance=50
     * WHEN resolveRules for site-alpha at 08:00
     * THEN tolerance=50 (employee override), approvalRequired=true (from team, inherited)
     */
    @Test
    void john_employeeOverrideWinsForTolerance() {
        EffectiveRules rules = resolver.resolveRules(siteAlpha, teamContractors, john, "site-alpha", TUESDAY_MORNING);

        assertThat(rules.toleranceMeters()).isEqualTo(50);
        assertThat(rules.approvalRequired()).isTrue();
    }

    // ---------- GIVEN a strict mode window is active ----------

    /**
     * GIVEN a site with a strict mode window 07:00-09:00 (tolerance=5)
     * AND clock time is 08:00 (inside window)
     * WHEN resolveRules
     * THEN tolerance=5 (strict mode override)
     */
    @Test
    void strictModeWindow_overridesTolerance_whenTimeInWindow() {
        SiteRules rulesWithStrict = new SiteRules(30,
                List.of(new StrictModeWindow(LocalTime.of(7, 0), LocalTime.of(9, 0), 5)),
                false);
        Site siteWithStrict = new Site("site-alpha", "Alpha Construction", "+27821000001",
                rulesWithStrict, List.of());

        EffectiveRules rules = resolver.resolveRules(siteWithStrict, teamDayShift, alice, "site-alpha", TUESDAY_MORNING);

        assertThat(rules.toleranceMeters()).isEqualTo(5);
    }

    /**
     * GIVEN a strict mode window 07:00-09:00
     * AND clock time is 10:00 (outside window)
     * WHEN resolveRules
     * THEN tolerance=30 (not overridden)
     */
    @Test
    void strictModeWindow_doesNotOverride_whenTimeOutsideWindow() {
        SiteRules rulesWithStrict = new SiteRules(30,
                List.of(new StrictModeWindow(LocalTime.of(7, 0), LocalTime.of(9, 0), 5)),
                false);
        Site siteWithStrict = new Site("site-alpha", "Alpha Construction", "+27821000001",
                rulesWithStrict, List.of());

        ZonedDateTime tenAM = TUESDAY_MORNING.withHour(10);
        EffectiveRules rules = resolver.resolveRules(siteWithStrict, teamDayShift, alice, "site-alpha", tenAM);

        assertThat(rules.toleranceMeters()).isEqualTo(30);
    }
}

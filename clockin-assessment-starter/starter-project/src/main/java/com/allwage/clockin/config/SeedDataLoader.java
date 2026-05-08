package com.allwage.clockin.config;

import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Loads seed data at startup when app.seed-data.enabled=true (env: APP_SEED_DATA_ENABLED).
 *
 * Seed data:
 *   Site:      site-alpha — Alpha Construction, centre -26.2041 / 28.0473
 *   Geofences: fence-main-gate (100m radius, Mon-Fri 06:00-18:00)
 *              fence-equip-yard (50m radius, Mon-Fri 07:00-09:00, ~80m NE)
 *   Teams:     team-day-shift (all nulls — inherits site)
 *              team-contractors (tolerance=10, approval=true)
 *   Employees: emp-alice (day-shift, no overrides)
 *              emp-bob   (contractors, no overrides)
 *              emp-john  (contractors, tolerance override=50)
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final DocumentStore store;
    private final AppProperties appProperties;

    public SeedDataLoader(DocumentStore store, AppProperties appProperties) {
        this.store = store;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeedData().isEnabled()) {
            log.info("Seed data disabled — skipping");
            return;
        }

        log.info("Loading seed data...");
        loadSite();
        loadTeams();
        loadEmployees();
        log.info("Seed data loaded successfully");
    }

    private void loadSite() {
        // Main gate: 100m radius, Mon-Fri 06:00-18:00, 2026 full year
        Map<DayOfWeek, TimeRange> weekdayHours = Map.of(
                DayOfWeek.MONDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.TUESDAY,   new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.WEDNESDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.THURSDAY,  new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.FRIDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0))
        );
        Geofence mainGate = new Geofence("fence-main-gate", "Main Gate",
                -26.2041, 28.0473, 100.0,
                new ZoneSchedule(weekdayHours,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        // Equipment yard: 50m radius, Mon-Fri 07:00-09:00, ~80m NE of main gate
        Map<DayOfWeek, TimeRange> morningOnly = Map.of(
                DayOfWeek.MONDAY,    new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0)),
                DayOfWeek.TUESDAY,   new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0)),
                DayOfWeek.WEDNESDAY, new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0)),
                DayOfWeek.THURSDAY,  new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0)),
                DayOfWeek.FRIDAY,    new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0))
        );
        Geofence equipYard = new Geofence("fence-equip-yard", "Equipment Yard",
                -26.2034, 28.0484, 50.0,
                new ZoneSchedule(morningOnly,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        SiteRules rules = new SiteRules(30, List.of(), false);
        Site siteAlpha = new Site("site-alpha", "Alpha Construction", "+27821000001",
                rules, List.of(mainGate, equipYard));

        store.save("sites", "site-alpha", siteAlpha);
    }

    private void loadTeams() {
        Team dayShift = new Team("team-day-shift", "site-alpha", "Day Shift",
                new TeamRules(null, null, null));
        Team contractors = new Team("team-contractors", "site-alpha", "Contractors",
                new TeamRules(10, null, true));

        store.save("teams", "team-day-shift", dayShift);
        store.save("teams", "team-contractors", contractors);
    }

    private void loadEmployees() {
        Employee alice = new Employee("emp-alice", "Alice", "+27821000002",
                Map.of("site-alpha", "team-day-shift"),
                Map.of());

        Employee bob = new Employee("emp-bob", "Bob", "+27821000003",
                Map.of("site-alpha", "team-contractors"),
                Map.of());

        Employee john = new Employee("emp-john", "John", "+27821000004",
                Map.of("site-alpha", "team-contractors"),
                Map.of("site-alpha", new EmployeeRules(50, null, null)));

        store.save("employees", "emp-alice", alice);
        store.save("employees", "emp-bob", bob);
        store.save("employees", "emp-john", john);
    }
}

package com.allwage.clockin.service;

import com.allwage.clockin.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD Priority 2 + 3 — Geofence validation (temporal and GPS boundary).
 */
class GeofenceValidatorTest {

    private GeofenceValidator validator;

    // Site-alpha centre: -26.2041, 28.0473
    private static final double CENTRE_LAT = -26.2041;
    private static final double CENTRE_LON = 28.0473;
    private static final double RADIUS_M = 100.0;

    // Mon-Fri 06:00-18:00, 2026-01-01 to 2026-12-31
    private Geofence mainGate;

    // Monday at exactly 06:00:00 SAST
    private static final ZonedDateTime MON_06_00 =
            ZonedDateTime.of(2026, 5, 4, 6, 0, 0, 0, ZoneOffset.ofHours(2));

    @BeforeEach
    void setUp() {
        validator = new GeofenceValidator();
        mainGate = buildGeofence(RADIUS_M,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Map.of(
                        DayOfWeek.MONDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                        DayOfWeek.TUESDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                        DayOfWeek.WEDNESDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                        DayOfWeek.THURSDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                        DayOfWeek.FRIDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0))
                ));
    }

    // ---- Priority 2: Temporal checks ----

    /**
     * GIVEN clockTime = 06:00:00 and geofence opens at 06:00
     * WHEN employee is at the site centre (distance=0)
     * THEN result is VALID (start inclusive)
     */
    @Test
    void atOpeningTime_isValid() {
        ValidationStatus status = validator.validate(
                List.of(mainGate), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), MON_06_00);

        assertThat(status).isEqualTo(ValidationStatus.VALID);
    }

    /**
     * GIVEN clockTime = 05:59:59 (one second before opening)
     * WHEN employee is at the site centre
     * THEN result is INVALID (outside operating hours)
     */
    @Test
    void oneSecondBeforeOpening_isInvalid() {
        ZonedDateTime beforeOpen = MON_06_00.minusSeconds(1);
        ValidationStatus status = validator.validate(
                List.of(mainGate), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), beforeOpen);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    /**
     * GIVEN clockTime = Saturday
     * AND geofence only operates Mon-Fri
     * WHEN employee is at the site centre
     * THEN result is INVALID (geofence inactive on Saturday)
     */
    @Test
    void saturday_monFriGeofence_isInvalid() {
        ZonedDateTime saturday = ZonedDateTime.of(2026, 5, 9, 9, 0, 0, 0, ZoneOffset.ofHours(2));
        ValidationStatus status = validator.validate(
                List.of(mainGate), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), saturday);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    /**
     * GIVEN clockDate = 2025-12-31 (one day before effectiveFrom = 2026-01-01)
     * WHEN employee is at the site centre
     * THEN result is INVALID (geofence not yet active)
     */
    @Test
    void beforeEffectiveFrom_isInvalid() {
        ZonedDateTime beforeStart = ZonedDateTime.of(2025, 12, 31, 9, 0, 0, 0, ZoneOffset.ofHours(2));
        ValidationStatus status = validator.validate(
                List.of(mainGate), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), beforeStart);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    /**
     * GIVEN clockDate = 2027-01-01 (one day after effectiveTo = 2026-12-31)
     * WHEN employee is at the site centre
     * THEN result is INVALID (geofence expired)
     */
    @Test
    void afterEffectiveTo_isInvalid() {
        ZonedDateTime afterEnd = ZonedDateTime.of(2027, 1, 1, 9, 0, 0, 0, ZoneOffset.ofHours(2));
        ValidationStatus status = validator.validate(
                List.of(mainGate), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), afterEnd);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    /**
     * GIVEN a geofence with only 07:00-09:00 operating hours
     * WHEN clockTime = 10:00 (outside window)
     * THEN that geofence is skipped, result is INVALID
     */
    @Test
    void geofenceOutsideHours_isSkipped() {
        Geofence equipYard = buildGeofence(50.0,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Map.of(DayOfWeek.MONDAY, new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0))));

        ZonedDateTime tenAM = MON_06_00.withHour(10);
        ValidationStatus status = validator.validate(
                List.of(equipYard), CENTRE_LAT, CENTRE_LON, 0.0,
                new EffectiveRules(0, false), tenAM);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    // ---- Priority 3: GPS boundary checks ----

    /**
     * GIVEN distance = radius + tolerance + accuracy (exactly on effective boundary)
     * WHEN validate
     * THEN result is VALID (boundary inclusive)
     */
    @Test
    void onEffectiveBoundary_isValid() {
        // Place employee exactly at effectiveRadius = 100 + 20 + 5 = 125m from centre
        double[] coords = offsetByMeters(CENTRE_LAT, CENTRE_LON, 125.0);
        ValidationStatus status = validator.validate(
                List.of(mainGate), coords[0], coords[1], 5.0,
                new EffectiveRules(20, false), MON_06_00);

        assertThat(status).isEqualTo(ValidationStatus.VALID);
    }

    /**
     * GIVEN distance = radius + tolerance + accuracy + 2m (just outside effective boundary)
     * WHEN validate
     * THEN result is INVALID
     */
    @Test
    void justOutsideEffectiveBoundary_isInvalid() {
        // 127m from centre — 2m outside 125m effective radius
        double[] coords = offsetByMeters(CENTRE_LAT, CENTRE_LON, 127.0);
        ValidationStatus status = validator.validate(
                List.of(mainGate), coords[0], coords[1], 5.0,
                new EffectiveRules(20, false), MON_06_00);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    /**
     * GIVEN employee is outside all geofences
     * AND approvalRequired=true
     * WHEN validate
     * THEN result is PENDING_APPROVAL
     */
    @Test
    void outsideGeofence_withApprovalRequired_isPendingApproval() {
        double[] farAway = offsetByMeters(CENTRE_LAT, CENTRE_LON, 500.0);
        ValidationStatus status = validator.validate(
                List.of(mainGate), farAway[0], farAway[1], 0.0,
                new EffectiveRules(0, true), MON_06_00);

        assertThat(status).isEqualTo(ValidationStatus.PENDING_APPROVAL);
    }

    /**
     * GIVEN employee is outside all geofences
     * AND approvalRequired=false
     * WHEN validate
     * THEN result is INVALID
     */
    @Test
    void outsideGeofence_withoutApproval_isInvalid() {
        double[] farAway = offsetByMeters(CENTRE_LAT, CENTRE_LON, 500.0);
        ValidationStatus status = validator.validate(
                List.of(mainGate), farAway[0], farAway[1], 0.0,
                new EffectiveRules(0, false), MON_06_00);

        assertThat(status).isEqualTo(ValidationStatus.INVALID);
    }

    // ---- helpers ----

    private Geofence buildGeofence(double radius, LocalDate from, LocalDate to,
                                    Map<DayOfWeek, TimeRange> hours) {
        return new Geofence("g1", "Test Fence", CENTRE_LAT, CENTRE_LON, radius,
                new ZoneSchedule(hours, from, to));
    }

    /**
     * Returns [lat, lon] approximately {@code distanceMeters} north of the given point.
     * Sufficient precision for test-level assertions (~1 m error at 125 m).
     */
    private static double[] offsetByMeters(double lat, double lon, double distanceMeters) {
        double deltaLat = distanceMeters / 111_319.5;
        return new double[]{lat + deltaLat, lon};
    }
}

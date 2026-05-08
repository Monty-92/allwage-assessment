package com.allwage.clockin.service;

import com.allwage.clockin.controller.ClockRequest;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BDD — ClockService pipeline coverage: 404 failure paths, idempotency, and
 * validation outcomes (VALID / INVALID when geofence is inactive or employee is outside).
 *
 * Uses real DocumentStore, RuleResolver, and GeofenceValidator.
 * NotificationService and SsePublisher are mocked to isolate side-effect assertions.
 */
@ExtendWith(MockitoExtension.class)
class ClockServiceTest {

    private static final String SITE_ID   = "site-001";
    private static final String TEAM_ID   = "team-001";
    private static final String EMPLOYEE_ID = "emp-001";
    private static final String EVENT_ID  = "event-001";

    private static final double CENTRE_LAT = -26.2041;
    private static final double CENTRE_LON = 28.0473;

    // Monday 2026-05-04 at 09:00 SAST — within Mon-Fri 06:00-18:00 window
    private static final ZonedDateTime VALID_TIME =
            ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2));

    // Saturday — outside the Mon-Fri operating hours
    private static final ZonedDateTime SATURDAY =
            ZonedDateTime.of(2026, 5, 9, 9, 0, 0, 0, ZoneOffset.ofHours(2));

    @Mock
    private NotificationService notificationService;

    @Mock
    private SsePublisher ssePublisher;

    private DocumentStore store;
    private ClockService service;

    private Site site;
    private Team team;
    private Employee employee;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        service = new ClockService(store, new RuleResolver(), new GeofenceValidator(),
                notificationService, ssePublisher);

        Map<DayOfWeek, TimeRange> hours = Map.of(
                DayOfWeek.MONDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.TUESDAY,   new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.WEDNESDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.THURSDAY,  new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.FRIDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0))
        );
        Geofence geofence = new Geofence("g-1", "Main Gate",
                CENTRE_LAT, CENTRE_LON, 100.0,
                new ZoneSchedule(hours, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        site     = new Site(SITE_ID, "Test Site", "+27820000001",
                new SiteRules(30, List.of(), false), List.of(geofence));
        team     = new Team(TEAM_ID, SITE_ID, "Test Team", new TeamRules(null, null, null));
        employee = new Employee(EMPLOYEE_ID, "Test Employee", "+27820000002",
                Map.of(SITE_ID, TEAM_ID), Map.of());
    }

    // ---- Happy path ----

    /**
     * GIVEN valid employee, site, team and employee standing at the geofence centre
     * WHEN processClock is called
     * THEN event is stored with VALID status, notification and SSE are triggered
     */
    @Test
    void happyPath_validLocation_storesEventAndReturnsValid() {
        store.save("sites",     SITE_ID,     site);
        store.save("teams",     TEAM_ID,     team);
        store.save("employees", EMPLOYEE_ID, employee);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 10.0, ClockType.IN);

        ClockEvent result = service.processClock(request);

        assertThat(result.id()).isEqualTo(EVENT_ID);
        assertThat(result.validationStatus()).isEqualTo(ValidationStatus.VALID);
        assertThat(store.findById("clocks", EVENT_ID, ClockEvent.class)).isPresent();
        verify(notificationService).notify(any(), any(), any(), any(), any());
        verify(ssePublisher).publish(any());
    }

    // ---- Not-found failure paths ----

    /**
     * GIVEN no site exists in the store for the requested siteId
     * WHEN processClock is called
     * THEN ResponseStatusException(404) is thrown before notification or SSE
     */
    @Test
    void siteNotFound_throws404() {
        store.save("employees", EMPLOYEE_ID, employee);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 0.0, ClockType.IN);

        assertThatThrownBy(() -> service.processClock(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e ->
                        assertThat(((ResponseStatusException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
        verify(ssePublisher, never()).publish(any());
    }

    /**
     * GIVEN site exists but no employee in the store
     * WHEN processClock is called
     * THEN ResponseStatusException(404) is thrown
     */
    @Test
    void employeeNotFound_throws404() {
        store.save("sites", SITE_ID, site);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 0.0, ClockType.IN);

        assertThatThrownBy(() -> service.processClock(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e ->
                        assertThat(((ResponseStatusException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * GIVEN site and employee exist, but employee has no enrollment at the requested site
     * WHEN processClock is called
     * THEN ResponseStatusException(404) is thrown (not enrolled)
     */
    @Test
    void employeeNotEnrolledAtSite_throws404() {
        store.save("sites", SITE_ID, site);
        Employee notEnrolled = new Employee(EMPLOYEE_ID, "Test Employee", "+27820000002",
                Map.of(), Map.of());
        store.save("employees", EMPLOYEE_ID, notEnrolled);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 0.0, ClockType.IN);

        assertThatThrownBy(() -> service.processClock(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e ->
                        assertThat(((ResponseStatusException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * GIVEN employee enrolled in teamId "missing-team" but that team is not in the store
     * WHEN processClock is called
     * THEN ResponseStatusException(404) is thrown (team not found)
     */
    @Test
    void teamNotFound_throws404() {
        store.save("sites", SITE_ID, site);
        Employee enrolledInMissingTeam = new Employee(EMPLOYEE_ID, "Test Employee", "+27820000002",
                Map.of(SITE_ID, "missing-team"), Map.of());
        store.save("employees", EMPLOYEE_ID, enrolledInMissingTeam);
        // "missing-team" deliberately not saved

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 0.0, ClockType.IN);

        assertThatThrownBy(() -> service.processClock(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e ->
                        assertThat(((ResponseStatusException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- Idempotency ----

    /**
     * GIVEN a clock event already processed and stored for an eventId
     * WHEN processClock is called again with the same eventId
     * THEN the stored event is returned unchanged, notification and SSE are not re-triggered
     */
    @Test
    void duplicateEventId_returnsStoredEvent_noRenotification() {
        store.save("sites",     SITE_ID,     site);
        store.save("teams",     TEAM_ID,     team);
        store.save("employees", EMPLOYEE_ID, employee);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT, CENTRE_LON, 10.0, ClockType.IN);

        ClockEvent first  = service.processClock(request);
        ClockEvent second = service.processClock(request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.validationStatus()).isEqualTo(first.validationStatus());

        // Side effects triggered exactly once across both calls
        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any());
        verify(ssePublisher, times(1)).publish(any());
    }

    // ---- Validation outcomes ----

    /**
     * GIVEN employee is ~11 km outside all geofences (outside tolerance)
     * WHEN processClock is called
     * THEN event is stored with INVALID status; notification is still triggered
     */
    @Test
    void outsideAllGeofences_storesInvalidEvent() {
        store.save("sites",     SITE_ID,     site);
        store.save("teams",     TEAM_ID,     team);
        store.save("employees", EMPLOYEE_ID, employee);

        // ~11 km north of centre — well outside the 100 m geofence
        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, VALID_TIME,
                CENTRE_LAT + 0.1, CENTRE_LON, 0.0, ClockType.IN);

        ClockEvent result = service.processClock(request);

        assertThat(result.validationStatus()).isEqualTo(ValidationStatus.INVALID);
        assertThat(store.findById("clocks", EVENT_ID, ClockEvent.class)).isPresent();
        verify(notificationService).notify(any(), any(), any(), any(), any());
    }

    /**
     * GIVEN geofence operates Mon-Fri only and the clock time is a Saturday
     * WHEN processClock is called
     * THEN event is stored with INVALID status (geofence temporally inactive)
     */
    @Test
    void geofenceTemporallyInactive_storesInvalidEvent() {
        store.save("sites",     SITE_ID,     site);
        store.save("teams",     TEAM_ID,     team);
        store.save("employees", EMPLOYEE_ID, employee);

        ClockRequest request = new ClockRequest(EVENT_ID, EMPLOYEE_ID, SITE_ID, SATURDAY,
                CENTRE_LAT, CENTRE_LON, 0.0, ClockType.IN);

        ClockEvent result = service.processClock(request);

        assertThat(result.validationStatus()).isEqualTo(ValidationStatus.INVALID);
        assertThat(store.findById("clocks", EVENT_ID, ClockEvent.class)).isPresent();
    }
}

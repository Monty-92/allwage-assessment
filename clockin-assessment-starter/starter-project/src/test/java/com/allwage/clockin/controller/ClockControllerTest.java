package com.allwage.clockin.controller;

import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.seed-data.enabled=false"
)
class ClockControllerTest {

    private static final String SITE_ID = "test-site";
    private static final String TEAM_ID = "test-team";
    private static final String EMPLOYEE_ID = "emp-123";
    private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentStore store;

    @BeforeEach
    void setUp() {
        store.clearCollection("clocks");
        store.clearCollection("sites");
        store.clearCollection("teams");
        store.clearCollection("employees");
        store.clearCollection("notifications");

        // Site with a geofence centred exactly on the test coordinates.
        // Schedule covers 2024-01-15 (Monday) 06:00-18:00, so 09:00 is in-window.
        Map<DayOfWeek, TimeRange> hours = Map.of(
                DayOfWeek.MONDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.TUESDAY,   new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.WEDNESDAY, new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.THURSDAY,  new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                DayOfWeek.FRIDAY,    new TimeRange(LocalTime.of(6, 0), LocalTime.of(18, 0))
        );
        Geofence geofence = new Geofence("g-1", "Test Zone",
                -26.2041, 28.0473, 100.0,
                new ZoneSchedule(hours, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));

        Site site = new Site(SITE_ID, "Test Site", "+27820000001",
                new SiteRules(30, List.of(), false),
                List.of(geofence));
        store.save("sites", SITE_ID, site);

        Team team = new Team(TEAM_ID, SITE_ID, "Test Team",
                new TeamRules(null, null, null));
        store.save("teams", TEAM_ID, team);

        Employee employee = new Employee(EMPLOYEE_ID, "Test Employee", "+27820000002",
                Map.of(SITE_ID, TEAM_ID),
                Map.of());
        store.save("employees", EMPLOYEE_ID, employee);
    }

    @Test
    void clockIn_savesToStore_andReturnsValid() {
        ResponseEntity<ClockEvent> response = restTemplate.postForEntity(
                "/api/clocks",
                new HttpEntity<>(clockInBody(EVENT_ID), jsonHeaders()),
                ClockEvent.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(response.getBody().type()).isEqualTo(ClockType.IN);
        assertThat(response.getBody().validationStatus()).isEqualTo(ValidationStatus.VALID);

        // Verify persisted
        List<ClockEvent> stored = store.findAll("clocks", ClockEvent.class);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().id()).isEqualTo(EVENT_ID);
    }

    @Test
    void clockIn_idempotent_duplicateEventIdReturnsStoredEvent() {
        HttpEntity<String> entity = new HttpEntity<>(clockInBody(EVENT_ID), jsonHeaders());

        // First POST
        ResponseEntity<ClockEvent> first = restTemplate.postForEntity("/api/clocks", entity, ClockEvent.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second POST — same eventId
        ResponseEntity<ClockEvent> second = restTemplate.postForEntity("/api/clocks", entity, ClockEvent.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        // Exactly one event persisted
        assertThat(store.findAll("clocks", ClockEvent.class)).hasSize(1);

        // Exactly one notification guard key persisted
        assertThat(store.findAll("notifications", String.class)).hasSize(1);
    }

    @Test
    void clockIn_missingRequiredFields_returns400() {
        // Request omits eventId and siteId
        String requestBody = """
                {
                    "employeeId": "emp-123",
                    "timestamp": "2024-01-15T09:00:00+02:00",
                    "latitude": -26.2041,
                    "longitude": 28.0473,
                    "accuracyMeters": 10.0,
                    "type": "IN"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/clocks",
                new HttpEntity<>(requestBody, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String clockInBody(String eventId) {
        return """
                {
                    "eventId": "%s",
                    "employeeId": "emp-123",
                    "siteId": "test-site",
                    "timestamp": "2024-01-15T09:00:00+02:00",
                    "latitude": -26.2041,
                    "longitude": 28.0473,
                    "accuracyMeters": 10.0,
                    "type": "IN"
                }
                """.formatted(eventId);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

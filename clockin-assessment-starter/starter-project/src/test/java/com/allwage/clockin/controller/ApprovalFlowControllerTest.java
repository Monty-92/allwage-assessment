package com.allwage.clockin.controller;

import com.allwage.clockin.model.*;
import com.allwage.clockin.service.EventBus;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD — Approval flow integration tests for ClockController.
 *
 * GIVEN a PENDING_APPROVAL event exists
 *   WHEN POST /api/clocks/{id}/approve  THEN 200 with VALID status
 *   WHEN POST /api/clocks/{id}/reject   THEN 200 with INVALID status
 *
 * GIVEN the event does not exist
 *   WHEN POST /api/clocks/{id}/approve  THEN 404
 *   WHEN POST /api/clocks/{id}/reject   THEN 404
 *
 * GIVEN the event is already VALID
 *   WHEN POST /api/clocks/{id}/approve  THEN 409 Conflict
 *   WHEN POST /api/clocks/{id}/reject   THEN 409 Conflict
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.seed-data.enabled=false")
class ApprovalFlowControllerTest {

    private static final String PENDING_ID = "bbb00000-0000-0000-0000-000000000001";
    private static final String VALID_ID   = "bbb00000-0000-0000-0000-000000000002";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private EventBus eventBus;

    @Autowired
    private DocumentStore store;

    @BeforeEach
    void setUp() {
        store.clearCollection("clocks");

        store.save("clocks", PENDING_ID, new ClockEvent(
                PENDING_ID, "emp-1", "site-1",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 5.0,
                ClockType.IN, ValidationStatus.PENDING_APPROVAL,
                "Employee is outside primary zone; manager approval required"));

        store.save("clocks", VALID_ID, new ClockEvent(
                VALID_ID, "emp-1", "site-1",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 5.0,
                ClockType.IN, ValidationStatus.VALID, null));
    }

    // --------------------------------------------------------
    // POST /approve
    // --------------------------------------------------------

    @Test
    void approve_pendingEvent_returns200WithValidStatus() {
        ResponseEntity<ClockEvent> response = restTemplate.postForEntity(
                "/api/clocks/" + PENDING_ID + "/approve", null, ClockEvent.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().validationStatus()).isEqualTo(ValidationStatus.VALID);
        assertThat(response.getBody().validationReason()).isNull();
    }

    @Test
    void approve_nonExistentEvent_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/clocks/nonexistent-id/approve", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void approve_alreadyValidEvent_returns409() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/clocks/" + VALID_ID + "/approve", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --------------------------------------------------------
    // POST /reject
    // --------------------------------------------------------

    @Test
    void reject_pendingEvent_returns200WithInvalidStatus() {
        ResponseEntity<ClockEvent> response = restTemplate.postForEntity(
                "/api/clocks/" + PENDING_ID + "/reject", null, ClockEvent.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().validationStatus()).isEqualTo(ValidationStatus.INVALID);
    }

    @Test
    void reject_nonExistentEvent_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/clocks/nonexistent-id/reject", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reject_alreadyValidEvent_returns409() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/clocks/" + VALID_ID + "/reject", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

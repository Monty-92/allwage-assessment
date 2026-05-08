package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * BDD — Approval flow unit tests for ClockService.
 *
 * GIVEN a PENDING_APPROVAL event exists
 *   WHEN approve() is called  THEN event transitions to VALID and SSE is published
 *   WHEN reject() is called   THEN event transitions to INVALID and SSE is published
 *
 * GIVEN a VALID/INVALID event
 *   WHEN approve() or reject() is called  THEN 409 Conflict
 *
 * GIVEN an unknown event ID
 *   WHEN approve() or reject() is called  THEN 404 Not Found
 */
@ExtendWith(MockitoExtension.class)
class ClockServiceApprovalTest {

    private static final String EVENT_ID = "aaa00000-0000-0000-0000-000000000001";

    @Mock
    private NotificationService notificationService;

    @Mock
    private EventBus eventBus;

    private DocumentStore store;
    private ClockService service;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        service = new ClockService(store, new RuleResolver(new AppProperties()),
                new GeofenceValidator(), notificationService, eventBus);
    }

    private void savePendingEvent() {
        ClockEvent event = new ClockEvent(
                EVENT_ID, "emp-1", "site-1",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 5.0,
                ClockType.IN, ValidationStatus.PENDING_APPROVAL,
                "Employee is outside primary zone; manager approval required");
        store.save("clocks", EVENT_ID, event);
    }

    private void saveEventWithStatus(ValidationStatus status) {
        ClockEvent event = new ClockEvent(
                EVENT_ID, "emp-1", "site-1",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 5.0,
                ClockType.IN, status, null);
        store.save("clocks", EVENT_ID, event);
    }

    // --------------------------------------------------------
    // approve()
    // --------------------------------------------------------

    @Test
    void approve_pendingEvent_transitionsToValid() {
        savePendingEvent();

        ClockEvent result = service.approve(EVENT_ID);

        assertThat(result.validationStatus()).isEqualTo(ValidationStatus.VALID);
        assertThat(result.validationReason()).isNull();
        assertThat(result.id()).isEqualTo(EVENT_ID);

        // Persisted state must also be VALID
        ClockEvent persisted = store.findById("clocks", EVENT_ID, ClockEvent.class).orElseThrow();
        assertThat(persisted.validationStatus()).isEqualTo(ValidationStatus.VALID);
    }

    @Test
    void approve_pendingEvent_publishesSseEvent() {
        savePendingEvent();

        service.approve(EVENT_ID);

        verify(eventBus).publish(any(ClockEvent.class));
    }

    @Test
    void approve_validEvent_throwsConflict() {
        saveEventWithStatus(ValidationStatus.VALID);

        assertThatThrownBy(() -> service.approve(EVENT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approve_invalidEvent_throwsConflict() {
        saveEventWithStatus(ValidationStatus.INVALID);

        assertThatThrownBy(() -> service.approve(EVENT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approve_nonExistentEvent_throwsNotFound() {
        assertThatThrownBy(() -> service.approve("nonexistent-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --------------------------------------------------------
    // reject()
    // --------------------------------------------------------

    @Test
    void reject_pendingEvent_transitionsToInvalid() {
        savePendingEvent();

        ClockEvent result = service.reject(EVENT_ID);

        assertThat(result.validationStatus()).isEqualTo(ValidationStatus.INVALID);
        assertThat(result.id()).isEqualTo(EVENT_ID);

        // Persisted state must also be INVALID
        ClockEvent persisted = store.findById("clocks", EVENT_ID, ClockEvent.class).orElseThrow();
        assertThat(persisted.validationStatus()).isEqualTo(ValidationStatus.INVALID);
    }

    @Test
    void reject_pendingEvent_publishesSseEvent() {
        savePendingEvent();

        service.reject(EVENT_ID);

        verify(eventBus).publish(any(ClockEvent.class));
    }

    @Test
    void reject_validEvent_throwsConflict() {
        saveEventWithStatus(ValidationStatus.VALID);

        assertThatThrownBy(() -> service.reject(EVENT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reject_nonExistentEvent_throwsNotFound() {
        assertThatThrownBy(() -> service.reject("nonexistent-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

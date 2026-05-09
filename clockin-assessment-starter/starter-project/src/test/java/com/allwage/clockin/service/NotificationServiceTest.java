package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BDD — NotificationService unit tests.
 *
 * GIVEN employee phone is null, event is VALID
 *   WHEN notify() is called   THEN no exception, no WhatsApp call
 *
 * GIVEN employee phone is null, event is PENDING_APPROVAL, manager phone is set
 *   WHEN notify() is called   THEN manager receives an “Approval required” message
 *
 * GIVEN notifications disabled via app.notifications.enabled=false
 *   WHEN notify() is called   THEN no WhatsApp calls at all
 *
 * GIVEN notify() is called twice with the same eventId
 *   WHEN second call   THEN idempotency guard prevents duplicate send
 *
 * GIVEN PENDING_APPROVAL event with both employee and manager phones
 *   WHEN notify() is called   THEN both receive messages; manager message contains “Approval required”
 *
 * GIVEN VALID event with employee phone
 *   WHEN notify() is called   THEN employee message contains “confirmed”
 *
 * GIVEN INVALID event with employee phone
 *   WHEN notify() is called   THEN employee message contains “outside geofence”
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private WhatsAppClient whatsAppClient;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getNotifications().setEnabled(true);
        notificationService = new NotificationService(whatsAppClient, new DocumentStore(), props);
    }

    /**
     * GIVEN an employee with a null phone number
     * WHEN notify is called
     * THEN no exception is thrown and WhatsApp sendMessage is never called
     */
    @Test
    void notify_withNullEmployeePhone_doesNotThrow_andSkipsWhatsApp() {
        ClockEvent event = new ClockEvent(
                "event-001", "emp-001", "site-001",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 10.0,
                ClockType.IN,
                ValidationStatus.VALID, null);

        assertThatNoException().isThrownBy(() ->
                notificationService.notify(event, null, "Test Employee", "Test Site", "+27820000001"));

        verify(whatsAppClient, never()).sendMessage(any(), any());
    }

    /**
     * GIVEN employee phone is null, event is PENDING_APPROVAL, manager phone is set
     * WHEN notify() is called
     * THEN manager receives an "Approval required" message (early-return bug fix)
     */
    @Test
    void notify_noEmployeePhone_pendingApproval_withManagerPhone_notifiesManager() {
        ClockEvent event = makeEvent("event-002", ValidationStatus.PENDING_APPROVAL);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        notificationService.notify(event, null, "Test Employee", "Test Site", "+27820000001");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient, times(1)).sendMessage(eq("+27820000001"), captor.capture());
        assertThat(captor.getValue()).contains("Approval required");
    }

    /**
     * GIVEN notifications are disabled
     * WHEN notify() is called
     * THEN no WhatsApp messages are sent
     */
    @Test
    void notify_notificationsDisabled_skipsAllWhatsApp() {
        AppProperties disabledProps = new AppProperties();
        disabledProps.getNotifications().setEnabled(false);
        NotificationService disabledService = new NotificationService(whatsAppClient, new DocumentStore(), disabledProps);

        disabledService.notify(makeEvent("event-dis", ValidationStatus.VALID),
                "+27820000001", "Employee", "Site", null);

        verifyNoInteractions(whatsAppClient);
    }

    /**
     * GIVEN the same eventId is notified twice
     * WHEN notify() is called a second time
     * THEN the idempotency guard prevents a duplicate send
     */
    @Test
    void notify_duplicateEventId_idempotencyGuardPreventsDuplicate() {
        ClockEvent event = makeEvent("event-idem", ValidationStatus.VALID);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        notificationService.notify(event, "+27820000001", "Employee", "Site", null);
        notificationService.notify(event, "+27820000001", "Employee", "Site", null);

        verify(whatsAppClient, times(1)).sendMessage(any(), any());
    }

    /**
     * GIVEN PENDING_APPROVAL event with both employee and manager phones
     * WHEN notify() is called
     * THEN two messages are sent; manager message contains "Approval required"
     */
    @Test
    void notify_pendingApproval_withBothPhones_sendsManagerApprovalMessage() {
        ClockEvent event = makeEvent("event-pend", ValidationStatus.PENDING_APPROVAL);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        notificationService.notify(event, "+27820000001", "Jane", "Warehouse", "+27820000002");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient, times(2)).sendMessage(any(), captor.capture());
        assertThat(captor.getAllValues()).anyMatch(msg -> msg.contains("Approval required"));
    }

    /**
     * GIVEN a VALID clock-in event with employee phone
     * WHEN notify() is called
     * THEN the employee message confirms the clock-in
     */
    @Test
    void notify_validStatus_employeeMessageContainsConfirmed() {
        ClockEvent event = makeEvent("event-valid-msg", ValidationStatus.VALID);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        notificationService.notify(event, "+27820000001", "Employee", "Test Site", null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendMessage(any(), captor.capture());
        assertThat(captor.getValue()).containsIgnoringCase("confirmed");
    }

    /**
     * GIVEN an INVALID clock-in event with employee phone
     * WHEN notify() is called
     * THEN the employee message mentions outside geofence
     */
    @Test
    void notify_invalidStatus_employeeMessageMentionsOutsideGeofence() {
        ClockEvent event = makeEvent("event-inv-msg", ValidationStatus.INVALID);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        notificationService.notify(event, "+27820000001", "Employee", "Test Site", null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendMessage(any(), captor.capture());
        assertThat(captor.getValue()).containsIgnoringCase("outside geofence");
    }

    // --------------------------------------------------------
    // Helper
    // --------------------------------------------------------

    private ClockEvent makeEvent(String eventId, ValidationStatus status) {
        return new ClockEvent(
                eventId, "emp-" + eventId, "site-001",
                ZonedDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.ofHours(2)),
                -26.2041, 28.0473, 10.0,
                ClockType.IN, status, status == ValidationStatus.VALID ? null : "reason");
    }
}

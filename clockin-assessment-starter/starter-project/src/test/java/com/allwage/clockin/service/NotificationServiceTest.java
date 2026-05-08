package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BDD — NotificationService edge cases.
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
}

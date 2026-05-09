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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BDD — DailySummaryService unit tests.
 *
 * GIVEN summary is disabled
 *   WHEN sendSummaryForDate() is called   THEN no messages are sent
 *
 * GIVEN summary is enabled and no events exist for the date
 *   WHEN sendSummaryForDate() is called   THEN no messages are sent
 *
 * GIVEN summary is enabled and 3 events for one site
 *   WHEN sendSummaryForDate() is called   THEN exactly one message to that site's manager phone
 *
 * GIVEN summary is enabled and events for two different sites
 *   WHEN sendSummaryForDate() is called   THEN one message per site manager (two total)
 *
 * GIVEN summary is enabled and the message contains event counts
 *   WHEN sendSummaryForDate() is called   THEN message mentions site name and event count
 *
 * GIVEN summary is enabled and some events have INVALID or PENDING_APPROVAL status
 *   WHEN sendSummaryForDate() is called   THEN message mentions the anomaly count
 *
 * GIVEN 3 employees clocked IN, 2 then clocked OUT
 *   WHEN sendSummaryForDate() is called   THEN message mentions headcount of 1 currently on-site
 *
 * GIVEN WhatsAppClient returns false (send failure)
 *   WHEN sendSummaryForDate() is called   THEN no exception is thrown
 */
@ExtendWith(MockitoExtension.class)
class DailySummaryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);  // Monday
    private static final ZoneOffset SAST = ZoneOffset.ofHours(2);

    private static final String SITE_A_ID      = "site-alpha";
    private static final String SITE_A_NAME    = "Alpha Warehouse";
    private static final String SITE_A_MANAGER = "+27820000011";

    private static final String SITE_B_ID      = "site-beta";
    private static final String SITE_B_NAME    = "Beta Office";
    private static final String SITE_B_MANAGER = "+27820000022";

    @Mock
    private WhatsAppClient whatsAppClient;

    private DocumentStore store;
    private DailySummaryService service;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();

        // Sites
        store.save("sites", SITE_A_ID, new Site(SITE_A_ID, SITE_A_NAME, SITE_A_MANAGER,
                new SiteRules(30, List.of(), false), List.of()));
        store.save("sites", SITE_B_ID, new Site(SITE_B_ID, SITE_B_NAME, SITE_B_MANAGER,
                new SiteRules(30, List.of(), false), List.of()));
    }

    private DailySummaryService serviceWithSummaryEnabled(boolean enabled) {
        AppProperties props = new AppProperties();
        props.getSummary().setEnabled(enabled);
        return new DailySummaryService(store, whatsAppClient, props);
    }

    private void saveEvent(String eventId, String siteId, LocalDate date) {
        saveEvent(eventId, "emp-" + eventId, siteId, date, ClockType.IN, ValidationStatus.VALID);
    }

    private void saveEvent(String eventId, String employeeId, String siteId, LocalDate date,
                           ClockType type, ValidationStatus status) {
        ZonedDateTime ts = date.atTime(8, 30).atOffset(SAST).toZonedDateTime();
        store.save("clocks", eventId, new ClockEvent(
                eventId, employeeId, siteId, ts,
                -26.2041, 28.0473, 5.0,
                type, status, status == ValidationStatus.VALID ? null : "reason"));
    }

    // --------------------------------------------------------
    // Disabled feature guard
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_summaryDisabled_sendsNoMessages() {
        service = serviceWithSummaryEnabled(false);
        saveEvent("e-1", SITE_A_ID, TODAY);

        service.sendSummaryForDate(TODAY);

        verifyNoInteractions(whatsAppClient);
    }

    // --------------------------------------------------------
    // No events for the date
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_noEventsForDate_sendsNoMessages() {
        service = serviceWithSummaryEnabled(true);
        // Save an event for a DIFFERENT date
        saveEvent("e-yesterday", SITE_A_ID, TODAY.minusDays(1));

        service.sendSummaryForDate(TODAY);

        verifyNoInteractions(whatsAppClient);
    }

    // --------------------------------------------------------
    // Single site with events
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_eventsForOneSite_sendsOneMessage() {
        service = serviceWithSummaryEnabled(true);
        saveEvent("e-1", SITE_A_ID, TODAY);
        saveEvent("e-2", SITE_A_ID, TODAY);

        service.sendSummaryForDate(TODAY);

        verify(whatsAppClient, times(1)).sendMessage(eq(SITE_A_MANAGER), any());
    }

    @Test
    void sendSummaryForDate_messageContainsSiteNameAndCount() {
        service = serviceWithSummaryEnabled(true);
        saveEvent("e-1", SITE_A_ID, TODAY);
        saveEvent("e-2", SITE_A_ID, TODAY);
        saveEvent("e-3", SITE_A_ID, TODAY);

        service.sendSummaryForDate(TODAY);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendMessage(eq(SITE_A_MANAGER), msgCaptor.capture());
        String msg = msgCaptor.getValue();
        assertThat(msg).contains(SITE_A_NAME);
        assertThat(msg).contains("3");
        assertThat(msg).containsIgnoringCase("currently on-site");
        assertThat(msg).containsIgnoringCase("anomaly");
    }

    // --------------------------------------------------------
    // Two sites — one message per manager
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_eventsForTwoSites_sendsOneMessagePerManager() {
        service = serviceWithSummaryEnabled(true);
        saveEvent("e-a1", SITE_A_ID, TODAY);
        saveEvent("e-b1", SITE_B_ID, TODAY);
        saveEvent("e-b2", SITE_B_ID, TODAY);

        service.sendSummaryForDate(TODAY);

        verify(whatsAppClient, times(1)).sendMessage(eq(SITE_A_MANAGER), any());
        verify(whatsAppClient, times(1)).sendMessage(eq(SITE_B_MANAGER), any());
    }

    // --------------------------------------------------------
    // Failure tolerance
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_whatsappFails_doesNotThrow() {
        service = serviceWithSummaryEnabled(true);
        saveEvent("e-1", SITE_A_ID, TODAY);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(false);

        // Must not throw
        service.sendSummaryForDate(TODAY);
    }

    // --------------------------------------------------------
    // Site with no manager phone — skip gracefully
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_siteHasNoManagerPhone_skipsGracefully() {
        service = serviceWithSummaryEnabled(true);
        // Override site A with no manager phone
        store.save("sites", SITE_A_ID, new Site(SITE_A_ID, SITE_A_NAME, null,
                new SiteRules(30, List.of(), false), List.of()));
        saveEvent("e-1", SITE_A_ID, TODAY);

        service.sendSummaryForDate(TODAY);

        verifyNoInteractions(whatsAppClient);
    }

    // --------------------------------------------------------
    // Anomaly count in summary message
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_eventsWithAnomalies_messageIncludesAnomalyCount() {
        service = serviceWithSummaryEnabled(true);
        // 1 VALID, 1 INVALID, 1 PENDING_APPROVAL — 2 anomalies
        saveEvent("e-valid",   "emp-1", SITE_A_ID, TODAY, ClockType.IN, ValidationStatus.VALID);
        saveEvent("e-invalid", "emp-2", SITE_A_ID, TODAY, ClockType.IN, ValidationStatus.INVALID);
        saveEvent("e-pending", "emp-3", SITE_A_ID, TODAY, ClockType.IN, ValidationStatus.PENDING_APPROVAL);
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        service.sendSummaryForDate(TODAY);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendMessage(eq(SITE_A_MANAGER), captor.capture());
        assertThat(captor.getValue()).containsIgnoringCase("2 anomaly");
    }

    // --------------------------------------------------------
    // On-site headcount in summary message
    // --------------------------------------------------------

    @Test
    void sendSummaryForDate_someEmployeesClockedOut_messageIncludesCorrectHeadcount() {
        service = serviceWithSummaryEnabled(true);
        // emp-1: clocked IN only  → still on site
        // emp-2: clocked IN then OUT → no longer on site
        // emp-3: clocked IN then OUT → no longer on site
        // expected headcount = 1
        ZonedDateTime base = TODAY.atTime(8, 0).atOffset(SAST).toZonedDateTime();
        store.save("clocks", "e1-in",  new ClockEvent("e1-in",  "emp-1", SITE_A_ID, base,            -26.2041, 28.0473, 5.0, ClockType.IN,  ValidationStatus.VALID, null));
        store.save("clocks", "e2-in",  new ClockEvent("e2-in",  "emp-2", SITE_A_ID, base,            -26.2041, 28.0473, 5.0, ClockType.IN,  ValidationStatus.VALID, null));
        store.save("clocks", "e2-out", new ClockEvent("e2-out", "emp-2", SITE_A_ID, base.plusHours(4), -26.2041, 28.0473, 5.0, ClockType.OUT, ValidationStatus.VALID, null));
        store.save("clocks", "e3-in",  new ClockEvent("e3-in",  "emp-3", SITE_A_ID, base,            -26.2041, 28.0473, 5.0, ClockType.IN,  ValidationStatus.VALID, null));
        store.save("clocks", "e3-out", new ClockEvent("e3-out", "emp-3", SITE_A_ID, base.plusHours(6), -26.2041, 28.0473, 5.0, ClockType.OUT, ValidationStatus.VALID, null));
        when(whatsAppClient.sendMessage(any(), any())).thenReturn(true);

        service.sendSummaryForDate(TODAY);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendMessage(eq(SITE_A_MANAGER), captor.capture());
        assertThat(captor.getValue()).containsIgnoringCase("1 currently on-site");
    }
}

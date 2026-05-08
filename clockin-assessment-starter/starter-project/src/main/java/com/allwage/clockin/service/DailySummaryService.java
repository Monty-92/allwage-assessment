package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.ClockEvent;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends a daily WhatsApp attendance summary to each site manager.
 *
 * Two cron triggers are configured via app.summary.morning-cron and
 * app.summary.evening-cron. The morning trigger summarises yesterday's
 * events; the evening trigger summarises today's.
 *
 * The entire feature is gated by app.summary.enabled (default: false).
 */
@Service
public class DailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryService.class);

    private final DocumentStore store;
    private final WhatsAppClient whatsAppClient;
    private final AppProperties appProperties;

    public DailySummaryService(@NonNull DocumentStore store,
                               @NonNull WhatsAppClient whatsAppClient,
                               @NonNull AppProperties appProperties) {
        this.store = store;
        this.whatsAppClient = whatsAppClient;
        this.appProperties = appProperties;
    }

    /**
     * Morning cron — sends a recap of yesterday's clock events to each site manager.
     */
    @Scheduled(cron = "${app.summary.morning-cron}")
    public void sendMorningSummary() {
        sendSummaryForDate(LocalDate.now().minusDays(1));
    }

    /**
     * Evening cron — sends a recap of today's clock events to each site manager.
     */
    @Scheduled(cron = "${app.summary.evening-cron}")
    public void sendEveningSummary() {
        sendSummaryForDate(LocalDate.now());
    }

    /**
     * Core logic: group all clock events for the given date by site,
     * build a summary message, and send it to each site manager.
     * Skips sites with no events that day. No-ops when summary is disabled.
     */
    public void sendSummaryForDate(@NonNull LocalDate date) {
        if (!appProperties.getSummary().isEnabled()) {
            log.debug("Daily summary disabled; skipping for date={}", date);
            return;
        }

        List<ClockEvent> allEvents = store.findAll("clocks", ClockEvent.class);

        Map<String, List<ClockEvent>> bySite = allEvents.stream()
                .filter(e -> e.timestamp().toLocalDate().equals(date))
                .collect(Collectors.groupingBy(ClockEvent::siteId));

        if (bySite.isEmpty()) {
            log.debug("No clock events for date={}; skipping summary", date);
            return;
        }

        for (Map.Entry<String, List<ClockEvent>> entry : bySite.entrySet()) {
            String siteId = entry.getKey();
            List<ClockEvent> siteEvents = entry.getValue();

            Site site = store.findById("sites", siteId, Site.class).orElse(null);
            if (site == null) {
                log.warn("Site not found for siteId={}; skipping summary", siteId);
                continue;
            }

            String managerPhone = site.managerPhoneNumber();
            if (managerPhone == null || managerPhone.isBlank()) {
                log.warn("Site {} has no manager phone; skipping summary", siteId);
                continue;
            }

            String message = buildSummaryMessage(site.name(), date, siteEvents);
            try {
                boolean sent = whatsAppClient.sendMessage(managerPhone, message);
                if (!sent) {
                    log.warn("Summary message not delivered for siteId={} date={}", siteId, date);
                }
            } catch (Exception e) {
                log.warn("Failed to send summary for siteId={} date={}: {}", siteId, date, e.getMessage());
            }
        }
    }

    private String buildSummaryMessage(String siteName, LocalDate date, List<ClockEvent> events) {
        long inCount  = events.stream().filter(e -> e.type() == com.allwage.clockin.model.ClockType.IN).count();
        long outCount = events.stream().filter(e -> e.type() == com.allwage.clockin.model.ClockType.OUT).count();
        return "Daily attendance summary for " + siteName + " on " + date
                + ": " + events.size() + " event(s) — "
                + inCount + " clock-in(s), " + outCount + " clock-out(s).";
    }
}

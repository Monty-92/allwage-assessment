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
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Evening cron — sends a recap of today's clock events to each site manager.
     */
    @Scheduled(cron = "${app.summary.evening-cron}")
    public void sendEveningSummary() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Core logic: group all clock events for the given date by site,
     * build a summary message, and send it to each site manager.
     * Skips sites with no events that day. No-ops when summary is disabled.
     */
    public void sendSummaryForDate(@NonNull LocalDate date) {
        throw new UnsupportedOperationException("not implemented");
    }
}

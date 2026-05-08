package com.allwage.clockin.service;

import com.allwage.clockin.config.AppProperties;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends WhatsApp notifications to employees and managers.
 * Idempotency is enforced via the notifications collection keyed by "notif:{eventId}".
 * Failures are logged at WARN level and do not propagate — the audit record is primary.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppClient whatsAppClient;
    private final DocumentStore store;
    private final AppProperties appProperties;

    public NotificationService(WhatsAppClient whatsAppClient, DocumentStore store,
                                AppProperties appProperties) {
        this.whatsAppClient = whatsAppClient;
        this.store = store;
        this.appProperties = appProperties;
    }

    /**
     * Sends a WhatsApp notification to the employee (and manager if PENDING_APPROVAL).
     * Uses idempotency guard: if "notif:{eventId}" exists in notifications, skips send.
     */
    public void notify(ClockEvent event, String employeePhone, String employeeName,
                       String siteName, String managerPhone) {
        if (!appProperties.getNotifications().isEnabled()) {
            return;
        }

        String guardKey = "notif:" + event.id();
        if (store.findById("notifications", guardKey, String.class).isPresent()) {
            log.debug("Notification already sent for eventId={}; skipping", event.id());
            return;
        }

        String direction = event.type() == ClockType.IN ? "in" : "out";
        String time = event.timestamp().format(TIME_FMT);

        String employeeMsg = buildEmployeeMessage(event.validationStatus(), event.type(), siteName, time);
        boolean sent = trySend(employeePhone, employeeMsg, event.id());

        if (event.validationStatus() == ValidationStatus.PENDING_APPROVAL && managerPhone != null) {
            String managerMsg = "Approval required: " + employeeName + " clocked " + direction
                    + " at " + siteName + " at " + time + " SAST (outside primary zone).";
            trySend(managerPhone, managerMsg, event.id());
        }

        if (sent) {
            store.save("notifications", guardKey, guardKey);
        }
    }

    private String buildEmployeeMessage(ValidationStatus status, ClockType type,
                                         String siteName, String time) {
        String direction = type == ClockType.IN ? "Clock-in" : "Clock-out";
        return switch (status) {
            case VALID -> direction + " confirmed at " + siteName + " at " + time + " SAST.";
            case INVALID -> direction + " outside geofence at " + siteName
                    + " at " + time + " SAST. Contact your manager.";
            case PENDING_APPROVAL -> direction + " at " + siteName
                    + " at " + time + " SAST requires manager approval.";
        };
    }

    private boolean trySend(String phone, String message, String eventId) {
        try {
            boolean success = whatsAppClient.sendMessage(phone, message);
            if (!success) {
                log.warn("WhatsApp send returned false. eventId={}, phone={}", eventId, phone);
            }
            return success;
        } catch (Exception e) {
            log.warn("WhatsApp send threw exception. eventId={}, phone={}", eventId, phone, e);
            return false;
        }
    }
}

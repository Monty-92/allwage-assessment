package com.allwage.clockin.service;

import com.allwage.clockin.controller.ClockRequest;
import com.allwage.clockin.model.*;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Core clock-in processing pipeline.
 *
 * Pipeline steps:
 * 1. Idempotency check — return stored event if eventId already exists
 * 2. Load site — 404 if not found
 * 3. Load employee — 404 if not found
 * 4. Resolve team enrollment — 404 if employee not enrolled at siteId
 * 5. Load team
 * 6. Resolve effective rules (Site -> Team -> Employee -> StrictMode)
 * 7. Validate geofence
 * 8. Save ClockEvent
 * 9. Send WhatsApp notification (idempotent, failure-tolerant)
 * 10. Publish SSE event
 */
@Service
public class ClockService {

    private static final Logger log = LoggerFactory.getLogger(ClockService.class);

    private final DocumentStore store;
    private final RuleResolver ruleResolver;
    private final GeofenceValidator geofenceValidator;
    private final NotificationService notificationService;
    private final SsePublisher ssePublisher;

    public ClockService(@NonNull DocumentStore store,
                        @NonNull RuleResolver ruleResolver,
                        @NonNull GeofenceValidator geofenceValidator,
                        @NonNull NotificationService notificationService,
                        @NonNull SsePublisher ssePublisher) {
        this.store = store;
        this.ruleResolver = ruleResolver;
        this.geofenceValidator = geofenceValidator;
        this.notificationService = notificationService;
        this.ssePublisher = ssePublisher;
    }

    /**
     * Process an incoming clock request.
     */
    public @NonNull ClockEvent processClock(@NonNull ClockRequest request) {
        // Step 1: idempotency — if we've seen this eventId before, return the stored event
        Optional<ClockEvent> existing = store.findById("clocks", request.eventId(), ClockEvent.class);
        if (existing.isPresent()) {
            log.debug("Duplicate eventId={}; returning stored event", request.eventId());
            return existing.get();
        }

        // Step 2: load site
        Site site = store.findById("sites", request.siteId(), Site.class)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Site not found: " + request.siteId()));

        // Step 3: load employee
        Employee employee = store.findById("employees", request.employeeId(), Employee.class)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Employee not found: " + request.employeeId()));

        // Step 4: resolve team enrollment
        String teamId = employee.siteEnrollments() != null
                ? employee.siteEnrollments().get(request.siteId())
                : null;
        if (teamId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Employee " + request.employeeId() + " is not enrolled at site " + request.siteId());
        }

        // Step 5: load team
        Team team = store.findById("teams", teamId, Team.class)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Team not found: " + teamId));

        // Step 6: resolve effective rules
        EffectiveRules rules = ruleResolver.resolveRules(site, team, employee,
                request.siteId(), request.timestamp());

        // Step 7: validate geofence
        ValidationStatus validationStatus = geofenceValidator.validate(
                site.geofences(), request.latitude(), request.longitude(),
                request.accuracyMeters(), rules, request.timestamp());

        String validationReason = switch (validationStatus) {
            case VALID -> null;
            case INVALID -> "Employee is outside all active geofences";
            case PENDING_APPROVAL -> "Employee is outside primary zone; manager approval required";
        };

        log.info("Clock event eventId={} employeeId={} siteId={} status={}",
                request.eventId(), request.employeeId(), request.siteId(), validationStatus);

        // Step 8: build and save ClockEvent
        ClockEvent clockEvent = new ClockEvent(
                request.eventId(),
                request.employeeId(),
                request.siteId(),
                request.timestamp(),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                request.type(),
                validationStatus,
                validationReason
        );
        store.save("clocks", clockEvent.id(), clockEvent);

        // Step 9: notify (idempotent, failure-tolerant)
        notificationService.notify(clockEvent, employee.phoneNumber(), employee.name(),
                site.name(), site.managerPhoneNumber());

        // Step 10: publish SSE
        ssePublisher.publish(clockEvent);

        return clockEvent;
    }

    /**
     * Find a clock event by ID.
     */
    public @NonNull Optional<ClockEvent> findById(@NonNull String id) {
        return store.findById("clocks", id, ClockEvent.class);
    }

    /**
     * Approve a PENDING_APPROVAL clock event — transitions it to VALID.
     * Throws 404 if the event does not exist.
     * Throws 409 Conflict if the event is not in PENDING_APPROVAL state.
     */
    public @NonNull ClockEvent approve(@NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Reject a PENDING_APPROVAL clock event — transitions it to INVALID.
     * Throws 404 if the event does not exist.
     * Throws 409 Conflict if the event is not in PENDING_APPROVAL state.
     */
    public @NonNull ClockEvent reject(@NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Get all clock events.
     */
    public @NonNull List<ClockEvent> findAll() {
        return store.findAll("clocks", ClockEvent.class);
    }
}

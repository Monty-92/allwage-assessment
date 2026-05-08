package com.allwage.clockin.service;

import com.allwage.clockin.model.ClockEvent;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for handling clock events.
 *
 * Currently this just saves clock events to the store.
 * Your task is to extend this with geofence validation,
 * WhatsApp confirmations, and other requirements.
 */
@Service
public class ClockService {

    private static final Logger log = LoggerFactory.getLogger(ClockService.class);

    private final DocumentStore store;

    public ClockService(@NonNull DocumentStore store) {
        this.store = store;
    }

    /**
     * Process an incoming clock event from the mobile app.
     *
     * Currently this just saves the event. You should extend this
     * to validate against geofences and send confirmations.
     *
     * @param clockEvent The clock event to process
     * @return The saved clock event
     */
    public @NonNull ClockEvent processClock(@NonNull ClockEvent clockEvent) {
        log.info("Processing clock event: {} for employee {}",
            clockEvent.type(), clockEvent.employeeId());

        store.save("clocks", clockEvent.id(), clockEvent);

        return clockEvent;
    }

    /**
     * Find a clock event by ID.
     */
    public @NonNull Optional<ClockEvent> findById(@NonNull String id) {
        return store.findById("clocks", id, ClockEvent.class);
    }

    /**
     * Get all clock events.
     */
    public @NonNull List<ClockEvent> findAll() {
        return store.findAll("clocks", ClockEvent.class);
    }
}

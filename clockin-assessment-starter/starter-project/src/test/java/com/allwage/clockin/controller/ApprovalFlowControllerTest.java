package com.allwage.clockin.controller;

import com.allwage.clockin.model.*;
import com.allwage.clockin.service.EventBus;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BDD — Approval flow integration tests for ClockController.
 *
 * GIVEN a PENDING_APPROVAL event exists
 *   WHEN PATCH /api/clocks/{id}/approve  THEN 200 with VALID status
 *   WHEN PATCH /api/clocks/{id}/reject   THEN 200 with INVALID status
 *
 * GIVEN the event does not exist
 *   WHEN PATCH /api/clocks/{id}/approve  THEN 404
 *   WHEN PATCH /api/clocks/{id}/reject   THEN 404
 *
 * GIVEN the event is already VALID
 *   WHEN PATCH /api/clocks/{id}/approve  THEN 409 Conflict
 *   WHEN PATCH /api/clocks/{id}/reject   THEN 409 Conflict
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.seed-data.enabled=false")
@AutoConfigureMockMvc
class ApprovalFlowControllerTest {

    private static final String PENDING_ID = "bbb00000-0000-0000-0000-000000000001";
    private static final String VALID_ID   = "bbb00000-0000-0000-0000-000000000002";

    @Autowired
    private MockMvc mockMvc;

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
    // PATCH /approve
    // --------------------------------------------------------

    @Test
    void approve_pendingEvent_returns200WithValidStatus() throws Exception {
        mockMvc.perform(patch("/api/clocks/" + PENDING_ID + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.validationReason").doesNotExist());
    }

    @Test
    void approve_nonExistentEvent_returns404() throws Exception {
        mockMvc.perform(patch("/api/clocks/nonexistent-id/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_alreadyValidEvent_returns409() throws Exception {
        mockMvc.perform(patch("/api/clocks/" + VALID_ID + "/approve"))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------
    // PATCH /reject
    // --------------------------------------------------------

    @Test
    void reject_pendingEvent_returns200WithInvalidStatus() throws Exception {
        mockMvc.perform(patch("/api/clocks/" + PENDING_ID + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("INVALID"));
    }

    @Test
    void reject_nonExistentEvent_returns404() throws Exception {
        mockMvc.perform(patch("/api/clocks/nonexistent-id/reject"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reject_alreadyValidEvent_returns409() throws Exception {
        mockMvc.perform(patch("/api/clocks/" + VALID_ID + "/reject"))
                .andExpect(status().isConflict());
    }
}

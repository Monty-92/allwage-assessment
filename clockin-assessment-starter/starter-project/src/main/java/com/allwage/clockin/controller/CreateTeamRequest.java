package com.allwage.clockin.controller;

import com.allwage.clockin.model.TeamRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/teams.
 * The referenced siteId must exist; the service validates this and returns 404 if not.
 */
public record CreateTeamRequest(
        @NotBlank String siteId,
        @NotBlank String name,
        @NotNull TeamRules rules) {
}

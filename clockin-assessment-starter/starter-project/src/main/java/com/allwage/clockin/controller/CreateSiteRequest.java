package com.allwage.clockin.controller;

import com.allwage.clockin.model.Geofence;
import com.allwage.clockin.model.SiteRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for POST /api/sites.
 * Server generates the site ID — clients must not supply one.
 */
public record CreateSiteRequest(
        @NotBlank String name,
        @NotBlank String managerPhoneNumber,
        @NotNull SiteRules rules,
        @NotNull List<Geofence> geofences) {
}

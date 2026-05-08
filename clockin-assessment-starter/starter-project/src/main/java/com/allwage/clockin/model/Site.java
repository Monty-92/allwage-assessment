package com.allwage.clockin.model;

import java.util.List;

/**
 * A physical job location. Embeds geofences and site-level rules.
 * See ADR-001: geofences are embedded to avoid secondary reads.
 */
public record Site(
        String id,
        String name,
        String managerPhoneNumber,
        SiteRules rules,
        List<Geofence> geofences
) {}

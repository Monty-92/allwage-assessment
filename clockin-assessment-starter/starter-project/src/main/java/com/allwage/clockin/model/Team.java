package com.allwage.clockin.model;

/**
 * A named group of employees at a site. Each employee belongs to exactly one team per site.
 * TeamRules provides nullable overrides on top of SiteRules.
 */
public record Team(String id, String siteId, String name, TeamRules rules) {}

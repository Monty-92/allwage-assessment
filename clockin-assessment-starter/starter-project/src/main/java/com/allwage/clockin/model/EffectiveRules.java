package com.allwage.clockin.model;

/**
 * Fully resolved rule set for a specific employee at a specific site at a specific time.
 * This is a value object computed at clock-in time — never persisted.
 */
public record EffectiveRules(int toleranceMeters, boolean approvalRequired) {}

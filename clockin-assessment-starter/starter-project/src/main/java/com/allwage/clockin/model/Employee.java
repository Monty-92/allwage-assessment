package com.allwage.clockin.model;

/**
 * Represents an employee who can clock in/out at job sites.
 */
public record Employee(
    String id,
    String name,
    String phoneNumber
) {}

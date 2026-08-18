package com.bizco.server.scheduling.domain;

/** StateMachines.md &sect;8.1. */
public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    NO_SHOW,
    CANCELLED
}

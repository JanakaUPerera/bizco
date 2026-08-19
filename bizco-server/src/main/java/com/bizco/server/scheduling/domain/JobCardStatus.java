package com.bizco.server.scheduling.domain;

/** StateMachines.md &sect;10.1. */
public enum JobCardStatus {
    CREATED,
    ESTIMATE_PENDING,
    ESTIMATE_APPROVED,
    IN_PROGRESS,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED
}

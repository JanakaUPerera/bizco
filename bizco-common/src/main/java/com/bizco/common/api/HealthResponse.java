package com.bizco.common.api;

import java.time.Instant;

public record HealthResponse(String status, Instant checkedAt, String databaseStatus, String message) {
    public static HealthResponse up() {
        return up("Application services are ready.");
    }

    public static HealthResponse up(final String message) {
        return new HealthResponse("UP", Instant.now(), "UP", message);
    }

    public static HealthResponse down(final String databaseStatus, final String message) {
        return new HealthResponse("DOWN", Instant.now(), databaseStatus, message);
    }

    public boolean ready() {
        return "UP".equals(status) && "UP".equals(databaseStatus);
    }
}

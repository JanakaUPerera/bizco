package com.bizco.common.api;

import java.time.Instant;

public record HealthResponse(String status, Instant checkedAt) {
    public static HealthResponse up() {
        return new HealthResponse("UP", Instant.now());
    }
}

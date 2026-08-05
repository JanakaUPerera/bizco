package com.bizco.common.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldViolation> fieldViolations,
        Instant timestamp
) {
    public static ApiError of(final String code, final String message) {
        return new ApiError(code, message, List.of(), Instant.now());
    }
}

package com.bizco.common.api;

import java.time.Instant;
import java.util.Map;
import java.util.List;

public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        List<FieldError> fieldErrors,
        Instant timestamp,
        String path,
        String correlationId
) {
    public static ApiError of(final String code, final String message) {
        return of(code, message, null, null);
    }

    public static ApiError of(final String code, final String message, final String path,
                              final String correlationId) {
        return new ApiError(code, message, Map.of(), List.of(), Instant.now(), path, correlationId);
    }

    public static ApiError of(final String code, final String message, final Map<String, Object> details,
                              final List<FieldError> fieldErrors, final String path, final String correlationId) {
        return new ApiError(code, message, details == null ? Map.of() : Map.copyOf(details),
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors), Instant.now(), path, correlationId);
    }

    public static ApiError validation(final List<FieldError> fieldErrors, final String path,
                                      final String correlationId) {
        return new ApiError(ApiErrorCode.VALIDATION_FAILED.code(),
                "One or more fields are invalid.", Map.of(), List.copyOf(fieldErrors),
                Instant.now(), path, correlationId);
    }
}

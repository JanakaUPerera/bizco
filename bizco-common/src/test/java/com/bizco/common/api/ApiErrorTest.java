package com.bizco.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

    @Test
    void createsFinalizedErrorShape() {
        final ApiError error = ApiError.of(ApiErrorCode.IDENTITY_ERROR.code(),
                "Invalid credentials", "/api/v1/auth/login", "correlation-id");

        assertEquals("IDENTITY_ERROR", error.code());
        assertEquals("Invalid credentials", error.message());
        assertEquals("/api/v1/auth/login", error.path());
        assertEquals("correlation-id", error.correlationId());
        assertTrue(error.details().isEmpty());
        assertTrue(error.fieldErrors().isEmpty());
        assertNotNull(error.timestamp());
    }

    @Test
    void createsValidationErrorShape() {
        final ApiError error = ApiError.validation(
                List.of(new FieldError("username", "REQUIRED", "Username is required.")),
                "/api/v1/users", "correlation-id");

        assertEquals(ApiErrorCode.VALIDATION_FAILED.code(), error.code());
        assertEquals("username", error.fieldErrors().getFirst().field());
        assertEquals("REQUIRED", error.fieldErrors().getFirst().code());
        assertEquals("correlation-id", error.correlationId());
    }
}

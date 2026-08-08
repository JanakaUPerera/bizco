package com.bizco.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HealthResponseTest {

    @Test
    void upCreatesHealthyResponse() {
        final HealthResponse response = HealthResponse.up();

        assertEquals("UP", response.status());
        assertEquals("UP", response.databaseStatus());
        assertEquals("Application services are ready.", response.message());
        assertNotNull(response.checkedAt());
        assertTrue(response.ready());
    }
}

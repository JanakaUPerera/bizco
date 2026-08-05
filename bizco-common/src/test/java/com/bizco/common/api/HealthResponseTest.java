package com.bizco.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HealthResponseTest {

    @Test
    void upCreatesHealthyResponse() {
        final HealthResponse response = HealthResponse.up();

        assertEquals("UP", response.status());
        assertNotNull(response.checkedAt());
    }
}

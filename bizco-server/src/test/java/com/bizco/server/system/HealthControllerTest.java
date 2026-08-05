package com.bizco.server.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bizco.common.api.HealthResponse;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReturnsUp() {
        final HealthResponse response = new HealthController().health();

        assertEquals("UP", response.status());
        assertNotNull(response.checkedAt());
    }
}

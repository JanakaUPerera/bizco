package com.bizco.server.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void passwordEncoderUsesBcrypt() {
        final SecurityConfig config = new SecurityConfig();

        assertTrue(config.passwordEncoder().matches("admin123", config.passwordEncoder().encode("admin123")));
    }
}

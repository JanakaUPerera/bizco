package com.bizco.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class BizcoClientApplicationTest {

    @Test
    void applicationClassCanBeConstructed() {
        assertDoesNotThrow(BizcoClientApplication::new);
    }
}

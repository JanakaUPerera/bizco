package com.bizco.server.audit.entity;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    @Test
    void auditLogsAreImmutableAfterCreation() {
        final AuditLog auditLog = new AuditLog("USER", "user-1", "USER_UPDATED", "SYSTEM",
                null, Instant.now(), Map.of(), Map.of(), "127.0.0.1", "client-1", "correlation-1");

        assertThrows(UnsupportedOperationException.class, auditLog::preventMutation);
    }
}

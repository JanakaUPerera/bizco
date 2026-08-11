package com.bizco.server.customer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PiiEncryptionServiceTest {

    @Test
    void encryptsAndDecryptsWithoutPlaintextAtRest() {
        final PiiEncryptionService service = new PiiEncryptionService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        final byte[] encrypted = service.encrypt("951234567V");

        assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("951234567V"));
        assertEquals("951234567V", service.decrypt(encrypted));
    }

    @Test
    void rejectsMissingExternalKey() {
        final PiiEncryptionService service = new PiiEncryptionService("");

        assertThrows(IllegalStateException.class, () -> service.encrypt("951234567V"));
    }
}


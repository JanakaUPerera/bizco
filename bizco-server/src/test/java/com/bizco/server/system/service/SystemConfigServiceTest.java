package com.bizco.server.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.system.SystemRequests.SystemConfigUpsertRequest;
import com.bizco.common.dto.system.SystemResponses.SystemConfigEntryResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.system.entity.SystemConfigEntry;
import com.bizco.server.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemConfigServiceTest {

    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SystemConfigService service = new SystemConfigService(repository, auditService, userRepository, objectMapper);

    @Test
    void createsNewKeyWhenMissing() {
        when(repository.findById("business.currency_code")).thenReturn(Optional.empty());
        when(repository.save(any(SystemConfigEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final SystemConfigEntryResponse response = service.upsert("business.currency_code",
                new SystemConfigUpsertRequest("LKR", "Default currency", 0L), null);

        assertEquals("business.currency_code", response.configKey());
        assertEquals("LKR", response.configValue());
        verify(auditService).record(eq("SYSTEM_CONFIG"), eq("business.currency_code"), eq("SYSTEM_CONFIG_UPDATED"),
                eq(null), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(), eq(null), eq(null));
    }

    @Test
    void staleUpdateReturnsConcurrentModification() {
        final SystemConfigEntry entry = new SystemConfigEntry("business.timezone", "\"Asia/Colombo\"", "Default timezone");
        when(repository.findById("business.timezone")).thenReturn(Optional.of(entry));

        final IdentityException exception = assertThrows(IdentityException.class, () -> service.upsert(
                "business.timezone", new SystemConfigUpsertRequest("UTC", "Default timezone", 5L), null));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        assertEquals("\"Asia/Colombo\"", entry.getConfigValueJson());
    }

    @Test
    void missingKeyReturnsNotFound() {
        when(repository.findById("unknown.key")).thenReturn(Optional.empty());

        final IdentityException exception = assertThrows(IdentityException.class, () -> service.get("unknown.key"));

        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
    }
}

package com.bizco.server.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.dto.audit.AuditDtos.AuditLogSearchResponse;
import com.bizco.server.audit.entity.AuditLog;
import com.bizco.server.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService auditService = new AuditService(repository);

    @Test
    void searchMapsPersistedEntriesToResponses() {
        final AuditLog log = new AuditLog("USER", "user-1", "USER_CREATED", "USER", UUID.randomUUID(),
                Instant.now(), Map.of("username", "cashier"), Map.of(), null, "client-1", "corr-1");
        final Pageable pageable = PageRequest.of(0, 20);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        final AuditLogSearchResponse response = auditService.search(null, null, null, null, null, 0, 20);

        assertEquals(1, response.data().size());
        assertEquals("USER_CREATED", response.data().get(0).actionCode());
        assertEquals("corr-1", response.data().get(0).correlationId());
    }

    @Test
    void searchPassesFiltersThrough() {
        final String entityType = "USER";
        final String actionCode = "USER_CREATED";
        final UUID actorUserId = UUID.randomUUID();
        final Instant from = Instant.now().minusSeconds(3600);
        final Instant to = Instant.now();
        when(repository.search(eq(entityType), eq(actionCode), eq(actorUserId), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search(entityType, actionCode, actorUserId, from, to, 0, 20);

        verify(repository).search(eq(entityType), eq(actionCode), eq(actorUserId), eq(from), eq(to), any(Pageable.class));
    }
}

package com.bizco.common.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogEntryResponse(
            Long id,
            String entityType,
            String entityId,
            String actionCode,
            String actorType,
            UUID actorUserId,
            Instant occurredAt,
            Map<String, Object> details,
            Map<String, Object> changedFields,
            String ipAddress,
            String clientId,
            String correlationId
    ) {
    }

    public record AuditLogSearchResponse(
            List<AuditLogEntryResponse> data,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}

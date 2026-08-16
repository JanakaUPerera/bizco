package com.bizco.server.audit.service;

import com.bizco.common.dto.audit.AuditDtos.AuditLogEntryResponse;
import com.bizco.common.dto.audit.AuditDtos.AuditLogSearchResponse;
import com.bizco.server.audit.entity.AuditLog;
import com.bizco.server.audit.repository.AuditLogRepository;
import com.bizco.server.config.CorrelationIdFilter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(final AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(final String entityType, final String entityId, final String actionCode,
                       final UUID actorUserId, final Map<String, Object> details) {
        record(entityType, entityId, actionCode, actorUserId, details, Map.of(), null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(final String entityType, final String entityId, final String actionCode,
                       final UUID actorUserId, final Map<String, Object> details,
                       final Map<String, Object> changedFields, final String ipAddress, final String clientId) {
        repository.save(new AuditLog(entityType, entityId, actionCode,
                actorUserId == null ? "SYSTEM" : "USER", actorUserId, Instant.now(), details,
                changedFields, ipAddress, clientId, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @Transactional(readOnly = true)
    public AuditLogSearchResponse search(final String entityType, final String actionCode, final UUID actorUserId,
                                         final Instant from, final Instant to, final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<AuditLog> result = repository.search(entityType, actionCode, actorUserId, from, to, pageable);
        return new AuditLogSearchResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private AuditLogEntryResponse toResponse(final AuditLog entry) {
        return new AuditLogEntryResponse(entry.getId(), entry.getEntityType(), entry.getEntityId(),
                entry.getActionCode(), entry.getActorType(), entry.getActorUserId(), entry.getOccurredAt(),
                entry.getDetails(), entry.getChangedFields(),
                entry.getIpAddress() == null ? null : entry.getIpAddress().getHostAddress(),
                entry.getClientId(), entry.getCorrelationId());
    }
}

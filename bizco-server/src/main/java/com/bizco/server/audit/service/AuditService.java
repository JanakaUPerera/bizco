package com.bizco.server.audit.service;

import com.bizco.server.audit.entity.AuditLog;
import com.bizco.server.audit.repository.AuditLogRepository;
import com.bizco.server.config.CorrelationIdFilter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
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
}

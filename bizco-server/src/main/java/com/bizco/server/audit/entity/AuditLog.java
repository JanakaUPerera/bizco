package com.bizco.server.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long id;
    @Column(length = 60, nullable = false)
    private String entityType;
    @Column(length = 100, nullable = false)
    private String entityId;
    @Column(length = 80, nullable = false)
    private String actionCode;
    @Column(length = 20, nullable = false)
    private String actorType;
    private UUID actorUserId;
    private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> changedFields = new HashMap<>();
    @JdbcTypeCode(SqlTypes.INET)
    private InetAddress ipAddress;
    @Column(length = 100)
    private String clientId;
    @Column(length = 100)
    private String correlationId;

    protected AuditLog() {
    }

    public AuditLog(final String entityType, final String entityId, final String actionCode,
                    final String actorType, final UUID actorUserId, final Instant occurredAt,
                    final Map<String, Object> details, final Map<String, Object> changedFields,
                    final String ipAddress, final String clientId, final String correlationId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.actionCode = actionCode;
        this.actorType = actorType == null || actorType.isBlank() ? "SYSTEM" : actorType;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.details = details == null ? new HashMap<>() : new HashMap<>(details);
        this.changedFields = changedFields == null ? new HashMap<>() : new HashMap<>(changedFields);
        this.ipAddress = parseIpAddress(ipAddress);
        this.clientId = clientId;
        this.correlationId = correlationId;
    }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getDetails() {
        return Map.copyOf(details);
    }

    public Map<String, Object> getChangedFields() {
        return Map.copyOf(changedFields);
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public String getClientId() {
        return clientId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    private InetAddress parseIpAddress(final String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ipAddress);
        } catch (final java.net.UnknownHostException ex) {
            return null;
        }
    }
}

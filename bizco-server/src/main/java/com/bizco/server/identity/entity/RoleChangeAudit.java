package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "role_change_audit")
public class RoleChangeAudit {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID userId;
    private Long roleId;
    private UUID grantId;
    private String action;
    private UUID actorUserId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details = new HashMap<>();
    private Instant createdAt = Instant.now();

    protected RoleChangeAudit() {
    }

    public RoleChangeAudit(final UUID userId, final Long roleId, final UUID grantId,
                           final String action, final Map<String, Object> details) {
        this.userId = userId;
        this.roleId = roleId;
        this.grantId = grantId;
        this.action = action;
        this.details = details == null ? new HashMap<>() : new HashMap<>(details);
    }
}


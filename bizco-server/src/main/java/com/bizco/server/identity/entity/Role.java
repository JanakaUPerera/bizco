package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue
    private UUID id;
    private String code;
    private String name;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Boolean> permissions = new HashMap<>();
    @Column(name = "is_system")
    private boolean system;
    @Column(name = "is_active")
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private UUID createdBy;

    protected Role() {
    }

    public Role(final String code, final String name, final Map<String, Boolean> permissions, final boolean active) {
        this.code = code;
        this.name = name;
        this.permissions = permissions == null ? new HashMap<>() : new HashMap<>(permissions);
        this.active = active;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isActive() {
        return active;
    }

    public void update(final String code, final String name, final Map<String, Boolean> permissions, final boolean active) {
        this.code = code;
        this.name = name;
        this.permissions = permissions == null ? new HashMap<>() : new HashMap<>(permissions);
        this.active = active;
    }
}


package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;
    @Column(name = "role_name")
    private String code;
    @Column(name = "description")
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissionCodes = new HashSet<>();
    @Column(name = "is_system_role")
    private boolean system;
    @Column(name = "is_active")
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected Role() {
    }

    public Role(final String code, final String name, final Map<String, Boolean> permissions, final boolean active) {
        this.code = code;
        this.name = name;
        this.permissionCodes = allowedPermissionCodes(permissions);
        this.active = active;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Map<String, Boolean> getPermissions() {
        return permissionCodes.stream()
                .sorted()
                .collect(Collectors.toMap(permission -> permission, permission -> true,
                        (left, right) -> left, java.util.LinkedHashMap::new));
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public void update(final String code, final String name, final Map<String, Boolean> permissions, final boolean active) {
        this.code = code;
        this.name = name;
        this.permissionCodes = allowedPermissionCodes(permissions);
        this.active = active;
    }

    private Set<String> allowedPermissionCodes(final Map<String, Boolean> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new HashSet<>();
        }
        return permissions.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
    }
}

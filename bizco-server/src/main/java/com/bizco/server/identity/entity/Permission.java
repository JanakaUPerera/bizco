package com.bizco.server.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @Column(name = "permission_code")
    private String permissionCode;
    private String module;
    private String action;
    private String description;
    private Instant createdAt;

    protected Permission() {
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }
}

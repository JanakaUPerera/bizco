package com.bizco.server.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;
    private String username;
    private String displayName;
    private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_role_id")
    private Role primaryRole;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private UserStatus status = UserStatus.ACTIVE;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    @Column(name = "is_active")
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private UUID createdBy;

    protected User() {
    }

    public User(final String username, final String displayName, final String passwordHash, final Role primaryRole) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.primaryRole = primaryRole;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean canAuthenticate(final Instant now) {
        return active && status == UserStatus.ACTIVE && (lockedUntil == null || lockedUntil.isBefore(now));
    }

    public void recordFailedLogin(final Instant lockedUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            this.status = UserStatus.LOCKED;
            this.lockedUntil = lockedUntil;
        }
    }

    public void recordSuccessfulLogin(final Instant now) {
        failedLoginAttempts = 0;
        status = UserStatus.ACTIVE;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getPrimaryRole() {
        return primaryRole;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return active;
    }

    public void update(final String displayName, final Role primaryRole, final boolean active) {
        this.displayName = displayName;
        this.primaryRole = primaryRole;
        this.active = active;
        if (!active) {
            status = UserStatus.DISABLED;
        }
    }

    public void changePassword(final String passwordHash) {
        this.passwordHash = passwordHash;
    }
}


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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @Column(name = "user_id")
    private UUID id;
    @Column(length = 50)
    private String username;
    @Column(name = "first_name")
    private String displayName;
    private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_role_id")
    private Role primaryRole;
    @Column(name = "is_locked")
    private boolean locked;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private boolean mustChangePassword = true;
    private Instant passwordChangedAt;
    private Instant lastLoginAt;
    @Column(name = "is_active")
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private UUID createdBy;
    @Version
    private long version;

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
        return active && (!locked || (lockedUntil != null && lockedUntil.isBefore(now)));
    }

    public boolean lockExpired(final Instant now) {
        return locked && lockedUntil != null && lockedUntil.isBefore(now);
    }

    public void recordFailedLogin(final Instant lockedUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            this.locked = true;
            this.lockedUntil = lockedUntil;
        }
    }

    public void recordSuccessfulLogin(final Instant now) {
        failedLoginAttempts = 0;
        locked = false;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void lockUntil(final Instant lockedUntil) {
        this.locked = true;
        this.lockedUntil = lockedUntil;
    }

    public void unlock() {
        this.locked = false;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
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
        if (!active) {
            return UserStatus.DISABLED;
        }
        return locked ? UserStatus.LOCKED : UserStatus.ACTIVE;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public long getVersion() {
        return version;
    }

    public void update(final String displayName, final Role primaryRole, final boolean active) {
        this.displayName = displayName;
        this.primaryRole = primaryRole;
        this.active = active;
    }

    public void changePassword(final String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        this.passwordChangedAt = Instant.now();
    }
}

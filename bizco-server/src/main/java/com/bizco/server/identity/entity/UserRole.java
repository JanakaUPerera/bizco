package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments")
public class UserRole {

    @Id
    @GeneratedValue
    @Column(name = "user_role_assignment_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;
    private Instant grantedAt = Instant.now();
    private Instant expiresAt;
    @Column(name = "is_active")
    private boolean active = true;
    private Instant revokedAt;
    private UUID grantedBy;
    private UUID revokedBy;
    private String revokeReason;

    protected UserRole() {
    }

    public UserRole(final User user, final Role role, final Instant expiresAt, final UUID grantedBy) {
        this.user = user;
        this.role = role;
        this.expiresAt = expiresAt;
        this.grantedBy = grantedBy == null && user != null ? user.getId() : grantedBy;
    }

    public boolean activeAt(final Instant now) {
        return active && revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke(final UUID revokedBy) {
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public User getUser() {
        return user;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return active;
    }
}


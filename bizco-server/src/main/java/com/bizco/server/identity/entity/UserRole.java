package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRole {

    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    private Role role;
    private Instant grantedAt = Instant.now();
    private Instant expiresAt;
    private Instant revokedAt;
    private UUID grantedBy;
    private UUID revokedBy;

    protected UserRole() {
    }

    public UserRole(final User user, final Role role, final Instant expiresAt, final UUID grantedBy) {
        this.user = user;
        this.role = role;
        this.expiresAt = expiresAt;
        this.grantedBy = grantedBy;
    }

    public boolean activeAt(final Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke(final UUID revokedBy) {
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
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
}


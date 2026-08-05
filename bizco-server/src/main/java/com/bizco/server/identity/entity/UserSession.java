package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    private String tokenHash;
    private String clientId;
    private Instant issuedAt = Instant.now();
    private Instant expiresAt;
    private Instant revokedAt;

    protected UserSession() {
    }

    public UserSession(final User user, final String tokenHash, final String clientId, final Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.clientId = clientId;
        this.expiresAt = expiresAt;
    }

    public boolean activeAt(final Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        revokedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}


package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue
    @Column(name = "session_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    private String tokenHash;
    private String clientId;
    @JdbcTypeCode(SqlTypes.INET)
    private InetAddress ipAddress;
    @Column(name = "created_at")
    private Instant issuedAt = Instant.now();
    private Instant lastActivityAt = Instant.now();
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokedReason;

    protected UserSession() {
    }

    public UserSession(final User user, final String tokenHash, final String clientId, final Instant expiresAt) {
        this(user, tokenHash, clientId, null, expiresAt);
    }

    public UserSession(final User user, final String tokenHash, final String clientId,
                       final String ipAddress, final Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.clientId = clientId;
        this.ipAddress = parseIpAddress(ipAddress);
        this.expiresAt = expiresAt;
    }

    public boolean activeAt(final Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && !idleExpiredAt(now);
    }

    public boolean idleExpiredAt(final Instant now) {
        return lastActivityAt.plus(java.time.Duration.ofMinutes(15)).compareTo(now) <= 0;
    }

    public void touch(final Instant now, final java.time.Duration idleTimeout) {
        lastActivityAt = now;
        expiresAt = now.plus(idleTimeout);
    }

    public void revoke() {
        revoke("LOGOUT");
    }

    public void revoke(final String reason) {
        revokedAt = Instant.now();
        revokedReason = reason;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getClientId() {
        return clientId;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
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


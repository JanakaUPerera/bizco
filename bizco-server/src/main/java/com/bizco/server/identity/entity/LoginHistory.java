package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_history_id")
    private Long id;
    private UUID userId;
    @Column(name = "attempted_username")
    private String username;
    private String clientId;
    @JdbcTypeCode(SqlTypes.INET)
    private InetAddress ipAddress;
    private boolean success;
    private String failureReason;
    @Column(name = "occurred_at")
    private Instant attemptedAt = Instant.now();

    protected LoginHistory() {
    }

    public LoginHistory(final UUID userId, final String username, final String clientId,
                        final String ipAddress, final boolean success, final String failureReason) {
        this.userId = userId;
        this.username = username;
        this.clientId = clientId;
        this.ipAddress = parseIpAddress(ipAddress);
        this.success = success;
        this.failureReason = failureReason;
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

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureReason() {
        return failureReason;
    }
}

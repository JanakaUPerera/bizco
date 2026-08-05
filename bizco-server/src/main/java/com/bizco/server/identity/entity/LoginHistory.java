package com.bizco.server.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID userId;
    private String username;
    private String clientId;
    private String ipAddress;
    private boolean success;
    private String failureReason;
    private Instant attemptedAt = Instant.now();

    protected LoginHistory() {
    }

    public LoginHistory(final UUID userId, final String username, final String clientId,
                        final String ipAddress, final boolean success, final String failureReason) {
        this.userId = userId;
        this.username = username;
        this.clientId = clientId;
        this.ipAddress = ipAddress;
        this.success = success;
        this.failureReason = failureReason;
    }
}


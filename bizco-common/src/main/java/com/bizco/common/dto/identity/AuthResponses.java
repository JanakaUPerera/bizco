package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record LoginRequest(String username, String password, String clientId) {
    }

    public record LoginResponse(
            String token,
            Instant expiresAt,
            UUID userId,
            String username,
            String displayName,
            Set<String> permissions
    ) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}


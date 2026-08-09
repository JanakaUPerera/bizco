package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record LoginRequest(String username, String password, String clientId) {
    }

    public record LoginResponse(LoginData data) {
    }

    public record LoginData(
            String sessionToken,
            Instant expiresAt,
            AuthenticatedUser user
    ) {
    }

    public record AuthenticatedUser(
            UUID userId,
            String username,
            String displayName,
            String primaryRole,
            Set<String> effectivePermissions
    ) {
    }

    public record CurrentSessionResponse(
            UUID userId,
            String username,
            String displayName,
            String primaryRole,
            Instant expiresAt,
            Set<String> effectivePermissions
    ) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}


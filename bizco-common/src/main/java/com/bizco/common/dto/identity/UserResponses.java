package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserResponses {

    private UserResponses() {
    }

    public record UserResponse(
            UUID id,
            String username,
            String displayName,
            Long primaryRoleId,
            String status,
            boolean active,
            Instant lastLoginAt,
            long version
    ) {
    }

    public record EffectivePermissionsResponse(UUID userId, Set<String> permissions) {
    }

    public record ResetPasswordResponse(UUID userId, String temporaryPassword) {
    }

    public record LoginHistoryEntryResponse(
            Long id,
            UUID userId,
            String attemptedUsername,
            String clientId,
            String ipAddress,
            boolean success,
            String failureReason,
            Instant occurredAt
    ) {
    }

    public record LoginHistorySearchResponse(
            List<LoginHistoryEntryResponse> data,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}


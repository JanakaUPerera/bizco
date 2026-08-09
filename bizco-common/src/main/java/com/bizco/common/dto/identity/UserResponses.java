package com.bizco.common.dto.identity;

import java.time.Instant;
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
}


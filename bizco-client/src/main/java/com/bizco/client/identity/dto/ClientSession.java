package com.bizco.client.identity.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ClientSession(
        String token,
        Instant expiresAt,
        UUID userId,
        String username,
        String displayName,
        Set<String> permissions,
        boolean mustChangePassword
) {
    public boolean hasPermission(final String permission) {
        return permissions != null && permissions.contains(permission);
    }
}

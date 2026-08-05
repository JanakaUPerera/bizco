package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class RoleResponses {

    private RoleResponses() {
    }

    public record RoleResponse(
            UUID id,
            String code,
            String name,
            Map<String, Boolean> permissions,
            boolean system,
            boolean active
    ) {
    }

    public record SecondaryRoleResponse(
            UUID id,
            UUID roleId,
            String roleCode,
            Instant grantedAt,
            Instant expiresAt,
            Instant revokedAt
    ) {
    }
}


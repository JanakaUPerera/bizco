package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class RoleRequests {

    private RoleRequests() {
    }

    public record RoleUpsertRequest(String code, String name, Map<String, Boolean> permissions, boolean active) {
    }

    public record SecondaryRoleGrantRequest(UUID roleId, Instant expiresAt) {
    }
}


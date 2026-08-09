package com.bizco.common.dto.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class RoleRequests {

    private RoleRequests() {
    }

    public record RoleUpsertRequest(String code, String name, Map<String, Boolean> permissions, boolean active,
                                    long version) {
    }

    public record SecondaryRoleGrantRequest(Long roleId, Instant expiresAt) {
    }
}


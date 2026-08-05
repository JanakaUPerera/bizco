package com.bizco.common.dto.identity;

import java.util.UUID;

public final class UserRequests {

    private UserRequests() {
    }

    public record UserCreateRequest(String username, String displayName, String password, UUID primaryRoleId) {
    }

    public record UserUpdateRequest(String displayName, UUID primaryRoleId, boolean active) {
    }
}


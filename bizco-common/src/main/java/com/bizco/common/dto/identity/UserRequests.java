package com.bizco.common.dto.identity;

public final class UserRequests {

    private UserRequests() {
    }

    public record UserCreateRequest(String username, String displayName, String password, Long primaryRoleId) {
    }

    public record UserUpdateRequest(String displayName, Long primaryRoleId, boolean active, long version) {
    }
}


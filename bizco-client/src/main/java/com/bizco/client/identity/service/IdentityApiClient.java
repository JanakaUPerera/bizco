package com.bizco.client.identity.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.identity.PermissionResponses.PermissionResponse;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IdentityApiClient extends ApiClient {

    public IdentityApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<List<UserResponse>> listUsers() {
        return get("/api/v1/users", new TypeReference<>() {
        });
    }

    public CompletableFuture<UserResponse> createUser(final UserCreateRequest request) {
        return post("/api/v1/users", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<UserResponse> updateUser(final UUID userId, final UserUpdateRequest request) {
        return put("/api/v1/users/" + userId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<RoleResponse>> listRoles() {
        return get("/api/v1/roles", new TypeReference<>() {
        });
    }

    public CompletableFuture<List<PermissionResponse>> listPermissions() {
        return get("/api/v1/permissions", new TypeReference<>() {
        });
    }

    public CompletableFuture<RoleResponse> createRole(final RoleUpsertRequest request) {
        return post("/api/v1/roles", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<RoleResponse> updateRole(final Long roleId, final RoleUpsertRequest request) {
        return put("/api/v1/roles/" + roleId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<Void> deleteRole(final Long roleId) {
        return delete("/api/v1/roles/" + roleId);
    }

    public CompletableFuture<List<SecondaryRoleResponse>> listSecondaryRoles(final UUID userId) {
        return get("/api/v1/users/" + userId + "/roles", new TypeReference<>() {
        });
    }

    public CompletableFuture<SecondaryRoleResponse> grantSecondaryRole(
            final UUID userId, final SecondaryRoleGrantRequest request) {
        return post("/api/v1/users/" + userId + "/roles", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<Void> revokeSecondaryRole(final UUID userId, final UUID grantId) {
        return delete("/api/v1/users/" + userId + "/roles/" + grantId);
    }
}

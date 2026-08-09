package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.PermissionResponses.PermissionResponse;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Permission;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.RoleChangeAudit;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.PermissionRepository;
import com.bizco.server.identity.repository.RoleChangeAuditRepository;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleChangeAuditRepository roleChangeAuditRepository;
    private final AuditService auditService;

    public RoleService(final RoleRepository roleRepository, final PermissionRepository permissionRepository,
                       final UserRepository userRepository,
                       final UserRoleRepository userRoleRepository,
                       final RoleChangeAuditRepository roleChangeAuditRepository,
                       final AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleChangeAuditRepository = roleChangeAuditRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::toPermissionResponse)
                .sorted(java.util.Comparator.comparing(PermissionResponse::permissionCode))
                .toList();
    }

    @Transactional
    public RoleResponse createRole(final RoleUpsertRequest request) {
        roleRepository.findByCode(request.code()).ifPresent(role -> {
            throw new IdentityException("Role code already exists");
        });
        final Role role = roleRepository.save(new Role(
                request.code(), request.name(), request.permissions(), request.active()));
        audit(null, role.getId(), null, "ROLE_CREATED", Map.of("code", role.getCode()));
        auditService.record("ROLE", entityId(role, request.code()), "ROLE_CREATED",
                null, Map.of("code", role.getCode()));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(final Long roleId, final RoleUpsertRequest request) {
        final Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IdentityException("Role not found"));
        verifyVersion("Role", role.getVersion(), request.version());
        final Map<String, Object> changedFields = Map.of(
                "code", role.getCode(),
                "name", role.getName(),
                "permissions", role.getPermissions(),
                "active", role.isActive());
        role.update(request.code(), request.name(), request.permissions(), request.active());
        audit(null, role.getId(), null, "ROLE_UPDATED", Map.of("code", role.getCode()));
        auditService.record("ROLE", entityId(role, roleId.toString()), "ROLE_UPDATED", null,
                Map.of("code", role.getCode()), changedFields, null, null);
        return toResponse(role);
    }

    @Transactional
    public void deleteRole(final Long roleId) {
        final Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IdentityException("Role not found"));
        if (role.isSystem()) {
            throw new IdentityException("System roles cannot be deleted");
        }
        roleRepository.delete(role);
        audit(null, role.getId(), null, "ROLE_DELETED", Map.of("code", role.getCode()));
        auditService.record("ROLE", entityId(role, roleId.toString()), "ROLE_DELETED", null,
                Map.of("code", role.getCode()));
    }

    @Transactional
    public SecondaryRoleResponse grantSecondaryRole(final UUID userId, final SecondaryRoleGrantRequest request) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        final Role role = roleRepository.findById(request.roleId()).orElseThrow(() -> new IdentityException("Role not found"));
        if (user.getPrimaryRole() != null && Objects.equals(user.getPrimaryRole().getId(), role.getId())) {
            throw new IdentityException("Primary role cannot be granted as a secondary role");
        }
        final UserRole grant = userRoleRepository.save(new UserRole(user, role, request.expiresAt(), null));
        audit(user.getId(), role.getId(), grant.getId(), "SECONDARY_ROLE_GRANTED", Map.of("roleCode", role.getCode()));
        auditService.record("USER_ROLE_ASSIGNMENT", grant.getId().toString(), "SECONDARY_ROLE_GRANTED",
                null, Map.of("userId", user.getId().toString(), "roleCode", role.getCode()));
        return toSecondaryResponse(grant);
    }

    @Transactional
    public void revokeSecondaryRole(final UUID grantId) {
        final UserRole userRole = userRoleRepository.findById(grantId)
                .orElseThrow(() -> new IdentityException("Secondary role grant not found"));
        if (userRole.getUser().getPrimaryRole() != null
                && Objects.equals(userRole.getUser().getPrimaryRole().getId(), userRole.getRole().getId())) {
            throw new IdentityException("Primary role cannot be revoked through secondary-role endpoint");
        }
        userRole.revoke(null);
        audit(null, userRole.getRole().getId(), userRole.getId(), "SECONDARY_ROLE_REVOKED",
                Map.of("roleCode", userRole.getRole().getCode()));
        auditService.record("USER_ROLE_ASSIGNMENT", userRole.getId().toString(), "SECONDARY_ROLE_REVOKED",
                null, Map.of("roleCode", userRole.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public List<SecondaryRoleResponse> listSecondaryRoles(final UUID userId) {
        return userRoleRepository.findAllByUserId(userId).stream().map(this::toSecondaryResponse).toList();
    }

    private RoleResponse toResponse(final Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), role.isSystem(), role.isActive(), role.getVersion());
    }

    private PermissionResponse toPermissionResponse(final Permission permission) {
        return new PermissionResponse(permission.getPermissionCode(), permission.getModule(),
                permission.getAction(), permission.getDescription());
    }

    private SecondaryRoleResponse toSecondaryResponse(final UserRole userRole) {
        final Role role = userRole.getRole();
        return new SecondaryRoleResponse(userRole.getId(), role.getId(), role.getCode(),
                userRole.getGrantedAt(), userRole.getExpiresAt(), userRole.getRevokedAt());
    }

    private void audit(final UUID userId, final Long roleId, final UUID grantId,
                       final String action, final Map<String, Object> details) {
        roleChangeAuditRepository.save(new RoleChangeAudit(userId, roleId, grantId, action, details));
    }

    private void verifyVersion(final String entityName, final long currentVersion, final long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    entityName + " was modified by another user");
        }
    }

    private String entityId(final Role role, final String fallback) {
        return role.getId() == null ? fallback : role.getId().toString();
    }
}


package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.RoleChangeAudit;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.RoleChangeAuditRepository;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleChangeAuditRepository roleChangeAuditRepository;

    public RoleService(final RoleRepository roleRepository, final UserRepository userRepository,
                       final UserRoleRepository userRoleRepository,
                       final RoleChangeAuditRepository roleChangeAuditRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleChangeAuditRepository = roleChangeAuditRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoleResponse createRole(final RoleUpsertRequest request) {
        roleRepository.findByCode(request.code()).ifPresent(role -> {
            throw new IdentityException("Role code already exists");
        });
        final Role role = roleRepository.save(new Role(
                request.code(), request.name(), request.permissions(), request.active()));
        audit(null, role.getId(), null, "ROLE_CREATED", Map.of("code", role.getCode()));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(final UUID roleId, final RoleUpsertRequest request) {
        final Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IdentityException("Role not found"));
        role.update(request.code(), request.name(), request.permissions(), request.active());
        audit(null, role.getId(), null, "ROLE_UPDATED", Map.of("code", role.getCode()));
        return toResponse(role);
    }

    @Transactional
    public SecondaryRoleResponse grantSecondaryRole(final UUID userId, final SecondaryRoleGrantRequest request) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        final Role role = roleRepository.findById(request.roleId()).orElseThrow(() -> new IdentityException("Role not found"));
        final UserRole grant = userRoleRepository.save(new UserRole(user, role, request.expiresAt(), null));
        audit(user.getId(), role.getId(), grant.getId(), "SECONDARY_ROLE_GRANTED", Map.of("roleCode", role.getCode()));
        return toSecondaryResponse(grant);
    }

    @Transactional
    public void revokeSecondaryRole(final UUID grantId) {
        final UserRole userRole = userRoleRepository.findById(grantId)
                .orElseThrow(() -> new IdentityException("Secondary role grant not found"));
        userRole.revoke(null);
        audit(null, userRole.getRole().getId(), userRole.getId(), "SECONDARY_ROLE_REVOKED",
                Map.of("roleCode", userRole.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public List<SecondaryRoleResponse> listSecondaryRoles(final UUID userId) {
        return userRoleRepository.findAllByUserId(userId).stream().map(this::toSecondaryResponse).toList();
    }

    private RoleResponse toResponse(final Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), role.isSystem(), role.isActive());
    }

    private SecondaryRoleResponse toSecondaryResponse(final UserRole userRole) {
        final Role role = userRole.getRole();
        return new SecondaryRoleResponse(userRole.getId(), role.getId(), role.getCode(),
                userRole.getGrantedAt(), userRole.getExpiresAt(), userRole.getRevokedAt());
    }

    private void audit(final UUID userId, final UUID roleId, final UUID grantId,
                       final String action, final Map<String, Object> details) {
        roleChangeAuditRepository.save(new RoleChangeAudit(userId, roleId, grantId, action, details));
    }
}


package com.bizco.server.identity.service;

import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public PermissionService(final UserRepository userRepository, final UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(final UUID userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new IdentityException("User not found"));
        final Set<String> permissions = new LinkedHashSet<>();
        addAllowedPermissions(permissions, user.getPrimaryRole());
        userRoleRepository.findActiveByUserId(userId, Instant.now())
                .forEach(userRole -> addAllowedPermissions(permissions, userRole.getRole()));
        return permissions;
    }

    private void addAllowedPermissions(final Set<String> permissions, final Role role) {
        if (role == null || !role.isActive()) {
            return;
        }
        role.getPermissions().forEach((permission, allowed) -> {
            if (Boolean.TRUE.equals(allowed)) {
                permissions.add(permission);
            }
        });
    }
}


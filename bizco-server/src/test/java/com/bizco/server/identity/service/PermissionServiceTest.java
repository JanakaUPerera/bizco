package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final PermissionService permissionService = new PermissionService(userRepository, userRoleRepository);

    @Test
    void effectivePermissionsUnionsPrimaryAndActiveSecondaryRoles() {
        final UUID userId = UUID.randomUUID();
        final Role primary = new Role("CASHIER", "Cashier", Map.of(
                "sales.invoice.read", true,
                "identity.role.write", false), true);
        final Role temporary = new Role("SUPERVISOR", "Supervisor", Map.of(
                "sales.invoice.void", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(new UserRole(user, temporary, Instant.now().plusSeconds(60), null)));

        assertEquals(Set.of("sales.invoice.read", "sales.invoice.void"),
                permissionService.effectivePermissions(userId));
    }
}

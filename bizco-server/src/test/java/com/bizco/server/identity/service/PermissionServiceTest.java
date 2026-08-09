package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void secRbac001EffectivePermissionsIncludePrimaryRolePermissions() {
        final UUID userId = UUID.randomUUID();
        final Role primary = new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        assertEquals(Set.of("invoice.create"), permissionService.effectivePermissions(userId));
    }

    @Test
    void secRbac003EffectivePermissionsUnionPrimaryAndActiveSecondaryRoles() {
        final UUID userId = UUID.randomUUID();
        final Role primary = new Role("CASHIER", "Cashier", Map.of(
                "invoice.read", true,
                "role.update", false), true);
        final Role temporary = new Role("SUPERVISOR", "Supervisor", Map.of(
                "invoice.void", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(new UserRole(user, temporary, Instant.now().plusSeconds(60), null)));

        assertEquals(Set.of("invoice.read", "invoice.void"),
                permissionService.effectivePermissions(userId));
    }

    @Test
    void secRbac004ExpiredSecondaryRoleIsIgnoredAtRuntime() {
        final UUID userId = UUID.randomUUID();
        final Role primary = new Role("CASHIER", "Cashier", Map.of("invoice.read", true), true);
        final Role expired = new Role("SUPERVISOR", "Supervisor", Map.of("invoice.void", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(new UserRole(user, expired, Instant.now().minusSeconds(60), null)));

        assertEquals(Set.of("invoice.read"), permissionService.effectivePermissions(userId));
    }

    @Test
    void secRbac005RevokedSecondaryRoleIsIgnoredAtRuntime() {
        final UUID userId = UUID.randomUUID();
        final Role primary = new Role("CASHIER", "Cashier", Map.of("invoice.read", true), true);
        final Role revokedRole = new Role("MANAGER", "Manager", Map.of("invoice.void", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);
        final UserRole revoked = new UserRole(user, revokedRole, Instant.now().plusSeconds(60), null);
        revoked.revoke(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(revoked));

        assertEquals(Set.of("invoice.read"), permissionService.effectivePermissions(userId));
    }

    @Test
    void inactiveRolePermissionsAreIgnored() {
        final UUID userId = UUID.randomUUID();
        final Role inactive = new Role("CUSTOM", "Custom", Map.of("invoice.void", true), false);
        final User user = new User("cashier", "Cashier", "hash", inactive);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        assertFalse(permissionService.effectivePermissions(userId).contains("invoice.void"));
    }
}

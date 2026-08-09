package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.RoleChangeAudit;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.PermissionRepository;
import com.bizco.server.identity.repository.RoleChangeAuditRepository;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleServiceTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final RoleChangeAuditRepository auditRepository = mock(RoleChangeAuditRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final RoleService roleService = new RoleService(roleRepository, permissionRepository, userRepository,
            userRoleRepository, auditRepository, auditService);

    @Test
    void secondaryRoleGrantAndRevokeAreAudited() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Long roleId = 1L;
        final UUID grantId = UUID.randomUUID();
        final Role primary = withId(new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true), 10L);
        final Role role = withId(new Role("SUPERVISOR", "Supervisor", Map.of("invoice.void", true), true), roleId);
        final User user = withId(new User("cashier", "Cashier", "hash", primary), userId);
        final UserRole grant = withId(new UserRole(user, role, Instant.now().plusSeconds(60), null), grantId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.save(org.mockito.ArgumentMatchers.any(UserRole.class))).thenReturn(grant);
        when(userRoleRepository.findById(grantId)).thenReturn(Optional.of(grant));

        final SecondaryRoleResponse response = roleService.grantSecondaryRole(userId,
                new SecondaryRoleGrantRequest(roleId, Instant.now().plusSeconds(60)));
        roleService.revokeSecondaryRole(grantId);

        assertEquals(grantId, response.id());
        assertNotNull(grant.getRevokedAt());
        verify(auditRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(RoleChangeAudit.class));
        verify(auditService).record(eq("USER_ROLE_ASSIGNMENT"), eq(grantId.toString()), eq("SECONDARY_ROLE_GRANTED"),
                eq(null), org.mockito.ArgumentMatchers.anyMap());
        verify(auditService).record(eq("USER_ROLE_ASSIGNMENT"), eq(grantId.toString()), eq("SECONDARY_ROLE_REVOKED"),
                eq(null), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void permissionAssignmentAndRemovalArePersistedForCustomRole() {
        final Role saved = new Role("SALES_CLERK", "Sales clerk", Map.of("invoice.create", true), true);
        when(roleRepository.save(any(Role.class))).thenReturn(saved);

        final RoleResponse created = roleService.createRole(new RoleUpsertRequest(
                "SALES_CLERK", "Sales clerk", Map.of("invoice.create", true, "invoice.void", false), true, 0));

        assertEquals(Map.of("invoice.create", true), created.permissions());

        final Role existing = new Role("SALES_CLERK", "Sales clerk", Map.of(
                "invoice.create", true, "invoice.void", true), true);
        when(roleRepository.findById(22L)).thenReturn(Optional.of(existing));

        final RoleResponse updated = roleService.updateRole(22L, new RoleUpsertRequest(
                "SALES_CLERK", "Sales clerk", Map.of("invoice.create", true, "invoice.void", false), true, 0));

        assertEquals(Map.of("invoice.create", true), updated.permissions());
    }

    @Test
    void secRbac008SystemRoleCannotBeDeleted() throws Exception {
        final Role systemRole = withId(new Role("SUPER_ADMIN", "Super administrator", Map.of(), true), 1L);
        setField(systemRole, "system", true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(systemRole));

        assertThrows(IdentityException.class, () -> roleService.deleteRole(1L));

        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    void staleRoleUpdateReturnsConcurrentModification() {
        final Role existing = new Role("SALES_CLERK", "Sales clerk", Map.of("invoice.create", true), true);
        when(roleRepository.findById(22L)).thenReturn(Optional.of(existing));

        final IdentityException exception = assertThrows(IdentityException.class, () -> roleService.updateRole(22L,
                new RoleUpsertRequest("SALES_CLERK", "Sales clerk", Map.of(), true, 99L)));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        assertEquals(Map.of("invoice.create", true), existing.getPermissions());
    }

    @Test
    void customRoleCanBeDeletedAndAudited() throws Exception {
        final Role customRole = withId(new Role("CUSTOM_SUPPORT", "Custom support", Map.of("customer.read", true), true), 3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(customRole));

        roleService.deleteRole(3L);

        verify(roleRepository).delete(customRole);
        verify(auditRepository).save(any(RoleChangeAudit.class));
    }

    @Test
    void primaryRoleCannotBeGrantedAsSecondaryRole() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role primary = withId(new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true), 5L);
        final User user = withId(new User("cashier", "Cashier", "hash", primary), userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(primary));

        assertThrows(IdentityException.class, () -> roleService.grantSecondaryRole(userId,
                new SecondaryRoleGrantRequest(5L, Instant.now().plusSeconds(60))));

        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void secRbac006PrimaryRoleCannotBeRevokedThroughSecondaryEndpoint() throws Exception {
        final UUID grantId = UUID.randomUUID();
        final Role primary = withId(new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true), 5L);
        final User user = new User("cashier", "Cashier", "hash", primary);
        final UserRole grant = withId(new UserRole(user, primary, Instant.now().plusSeconds(60), null), grantId);
        when(userRoleRepository.findById(grantId)).thenReturn(Optional.of(grant));

        assertThrows(IdentityException.class, () -> roleService.revokeSecondaryRole(grantId));
    }

    @Test
    void listsRevokedAndExpiredSecondaryRolesForAuditVisibility() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = withId(new Role("SUPERVISOR", "Supervisor", Map.of(), true), 2L);
        final User user = withId(new User("cashier", "Cashier", "hash", role), userId);
        final UserRole expiredGrant = withId(new UserRole(user, role, Instant.now().minusSeconds(60), null), UUID.randomUUID());
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(expiredGrant));

        assertEquals(1, roleService.listSecondaryRoles(userId).size());
    }

    private <T> T withId(final T entity, final Object id) throws Exception {
        setField(entity, "id", id);
        return entity;
    }

    private void setField(final Object entity, final String fieldName, final Object value) throws Exception {
        final Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }
}

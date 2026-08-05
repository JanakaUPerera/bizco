package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.RoleChangeAudit;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
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
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final RoleChangeAuditRepository auditRepository = mock(RoleChangeAuditRepository.class);
    private final RoleService roleService = new RoleService(roleRepository, userRepository,
            userRoleRepository, auditRepository);

    @Test
    void secondaryRoleGrantAndRevokeAreAudited() throws Exception {
        final UUID userId = UUID.randomUUID();
        final UUID roleId = UUID.randomUUID();
        final UUID grantId = UUID.randomUUID();
        final Role role = withId(new Role("SUPERVISOR", "Supervisor", Map.of("sales.invoice.void", true), true), roleId);
        final User user = withId(new User("cashier", "Cashier", "hash", role), userId);
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
    }

    @Test
    void listsRevokedAndExpiredSecondaryRolesForAuditVisibility() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = withId(new Role("SUPERVISOR", "Supervisor", Map.of(), true), UUID.randomUUID());
        final User user = withId(new User("cashier", "Cashier", "hash", role), userId);
        final UserRole expiredGrant = withId(new UserRole(user, role, Instant.now().minusSeconds(60), null), UUID.randomUUID());
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(expiredGrant));

        assertEquals(1, roleService.listSecondaryRoles(userId).size());
    }

    private <T> T withId(final T entity, final UUID id) throws Exception {
        final Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
        return entity;
    }
}

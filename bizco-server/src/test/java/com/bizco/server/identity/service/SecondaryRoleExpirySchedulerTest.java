package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecondaryRoleExpirySchedulerTest {

    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final SecondaryRoleExpiryScheduler scheduler =
            new SecondaryRoleExpiryScheduler(userRoleRepository, auditService);

    @Test
    void expiredAssignmentIsRevokedAndAudited() throws Exception {
        final Role primary = new Role("CASHIER", "Cashier", Map.of("invoice.read", true), true);
        final Role temporary = new Role("SUPERVISOR", "Supervisor", Map.of("invoice.void", true), true);
        final User user = new User("cashier", "Cashier", "hash", primary);
        setId(user, User.class, UUID.randomUUID());
        final UserRole assignment = new UserRole(user, temporary, Instant.now().minusSeconds(60), null);
        setId(assignment, UserRole.class, UUID.randomUUID());
        when(userRoleRepository.findExpiredActive(any(Instant.class))).thenReturn(List.of(assignment));

        scheduler.expireSecondaryRoles();

        assertFalse(assignment.isActive());
        assertNotNull(assignment.getRevokedAt());
        verify(auditService).record(eq("USER_ROLE_ASSIGNMENT"), any(String.class), eq("SECONDARY_ROLE_EXPIRED"),
                eq(null), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void noExpiredAssignmentsRecordsNoAudit() {
        when(userRoleRepository.findExpiredActive(any(Instant.class))).thenReturn(List.of());

        scheduler.expireSecondaryRoles();

        verify(auditService, never()).record(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private void setId(final Object entity, final Class<?> entityType, final UUID id) throws Exception {
        final java.lang.reflect.Field field = entityType.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}

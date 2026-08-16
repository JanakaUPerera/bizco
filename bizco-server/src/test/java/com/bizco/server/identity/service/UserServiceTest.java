package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.ResetPasswordResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserService userService = new UserService(userRepository, roleRepository, passwordEncoder,
            auditService, authService);

    @Test
    void secAuth006ManualLockAndUnlockChangeAuthenticationState() {
        final UUID userId = UUID.randomUUID();
        final User user = new User("cashier", "Cashier", "hash",
                new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.lockUser(userId);

        assertTrue(user.isLocked());
        assertNotNull(user.getLockedUntil());
        assertFalse(user.canAuthenticate(Instant.now()));
        verify(auditService).record(eq("USER"), eq(userId.toString()), eq("ACCOUNT_LOCKED"), eq(null),
                org.mockito.ArgumentMatchers.anyMap());

        userService.unlockUser(userId);

        assertFalse(user.isLocked());
        assertTrue(user.canAuthenticate(Instant.now()));
    }

    @Test
    void staleUserUpdateReturnsConcurrentModification() {
        final UUID userId = UUID.randomUUID();
        final Role role = new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true);
        final User user = new User("cashier", "Cashier", "hash", role);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        final IdentityException exception = assertThrows(IdentityException.class, () -> userService.updateUser(userId,
                new UserUpdateRequest("Updated", 1L, true, 99L)));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        assertEquals("Cashier", user.getDisplayName());
    }

    @Test
    void resetPasswordSetsMustChangePasswordAndRevokesSessions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = new User("cashier", "Cashier", "old-hash",
                new Role("CASHIER", "Cashier", Map.of("invoice.create", true), true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("new-hash");
        final var authentication = new UsernamePasswordAuthenticationToken("admin", null);
        final User admin = new User("admin", "Admin", "hash", new Role("OWNER", "Owner", Map.of(), true));
        final UUID adminId = UUID.randomUUID();
        setId(admin, adminId);
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));

        final ResetPasswordResponse response = userService.resetPassword(userId, authentication);

        assertEquals(userId, response.userId());
        assertNotNull(response.temporaryPassword());
        assertTrue(response.temporaryPassword().length() >= 8);
        assertEquals("new-hash", user.getPasswordHash());
        verify(authService).revokeUserSessions(eq(userId), eq("PASSWORD_RESET"));
        verify(auditService).record(eq("USER"), eq(userId.toString()), eq("USER_PASSWORD_RESET"), eq(adminId),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private void setId(final User user, final UUID id) throws Exception {
        final java.lang.reflect.Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}

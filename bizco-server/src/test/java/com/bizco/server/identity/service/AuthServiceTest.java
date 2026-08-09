package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.LoginHistory;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final LoginHistoryRepository loginHistoryRepository = mock(LoginHistoryRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final TokenService tokenService = mock(TokenService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuthService authService = new AuthService(userRepository, sessionRepository,
            loginHistoryRepository, passwordEncoder, tokenService, permissionService, auditService, 1);

    @Test
    void secAuth001LoginReturnsOpaqueTokenHashOnlyAndEffectivePermissions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = withId(activeUser("Correct1!"), userId);
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of());
        when(tokenService.createToken()).thenReturn("plain-token");
        when(tokenService.hash("plain-token")).thenReturn("hashed-token");
        when(permissionService.effectivePermissions(userId)).thenReturn(Set.of("user.read"));

        final LoginResponse response = authService.login(
                new LoginRequest("admin", "Correct1!", "client-1"), "127.0.0.1");

        assertEquals("plain-token", response.data().sessionToken());
        assertEquals("admin", response.data().user().username());
        assertEquals(Set.of("user.read"), response.data().user().effectivePermissions());
        assertNotNull(response.data().expiresAt());
        final ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository).save(session.capture());
        assertEquals("hashed-token", session.getValue().getTokenHash());
        verify(loginHistoryRepository).save(any());
    }

    @Test
    void secAuth002InvalidPasswordIncrementsAttemptsAndRecordsFailure() {
        final User user = activeUser("Correct1!");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        final IdentityException exception = assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("admin", "Wrong1!", "client-1"), "127.0.0.1"));

        assertEquals(ApiErrorCode.AUTH_INVALID_CREDENTIALS, exception.getCode());
        assertEquals(1, user.getFailedLoginAttempts());
        verify(loginHistoryRepository).save(any(LoginHistory.class));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void secAuth003UnknownUserRecordsAttemptedUsernameWithNullUserId() {
        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        final ArgumentCaptor<LoginHistory> loginHistory = ArgumentCaptor.forClass(LoginHistory.class);

        final IdentityException exception = assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("missing", "Password1!", "client-1"), "127.0.0.1"));

        assertEquals(ApiErrorCode.AUTH_INVALID_CREDENTIALS, exception.getCode());
        verify(loginHistoryRepository).save(loginHistory.capture());
        assertNull(loginHistory.getValue().getUserId());
        assertEquals("missing", loginHistory.getValue().getUsername());
        assertFalse(loginHistory.getValue().isSuccess());
    }

    @Test
    void secAuth004FifthInvalidPasswordLocksAccountForThirtyMinutes() {
        final User user = activeUser("Correct1!");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IdentityException.class, () -> authService.login(
                    new LoginRequest("admin", "Wrong1!", "client-1"), "127.0.0.1"));
        }

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        verify(auditService).record(eq("USER"), anyString(), eq("ACCOUNT_LOCKED"), any(), any());
        assertEquals(ApiErrorCode.AUTH_ACCOUNT_LOCKED, assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("admin", "Correct1!", "client-1"), "127.0.0.1")).getCode());
    }

    @Test
    void secAuth005ExpiredLockAutoUnlocksOnValidLogin() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = withId(activeUser("Correct1!"), userId);
        user.lockUntil(Instant.now().minusSeconds(1));
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of());
        when(tokenService.createToken()).thenReturn("plain-token");
        when(tokenService.hash("plain-token")).thenReturn("hashed-token");
        when(permissionService.effectivePermissions(userId)).thenReturn(Set.of("user.read"));

        authService.login(new LoginRequest("admin", "Correct1!", "client-1"), "127.0.0.1");

        assertFalse(user.isLocked());
    }

    @Test
    void secAuth007InactiveUserIsRejected() {
        final User user = activeUser("Correct1!");
        user.update("Administrator", user.getPrimaryRole(), false);
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        final IdentityException exception = assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("admin", "Correct1!", "client-1"), "127.0.0.1"));

        assertEquals(ApiErrorCode.AUTH_ACCOUNT_INACTIVE, exception.getCode());
    }

    @Test
    void secSession002LogoutRevokesMatchingSession() {
        final UserSession session = new UserSession(activeUser("Password1!"), "hashed-token", "client-1",
                Instant.now().plusSeconds(60));
        when(tokenService.hash("plain-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(session));

        authService.logout("plain-token");

        assertFalse(session.activeAt(Instant.now()));
        assertEquals("LOGOUT", session.getRevokedReason());
    }

    @Test
    void secSession003ConcurrentSessionLimitRejectsExtraLogin() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = withId(activeUser("Correct1!"), userId);
        final UserSession activeSession = new UserSession(user, "existing-token", "client-1",
                Instant.now().plusSeconds(60));
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(activeSession));

        final IdentityException exception = assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("admin", "Correct1!", "client-2"), "127.0.0.1"));

        assertEquals(ApiErrorCode.AUTH_CONCURRENT_SESSION_LIMIT, exception.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void secSession004ForceLogoutRevokesActiveUserSessions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = withId(activeUser("Password1!"), userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1",
                Instant.now().plusSeconds(60));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(session));

        authService.revokeUserSessions(userId, "FORCE_LOGOUT");

        assertFalse(session.activeAt(Instant.now()));
        assertEquals("FORCE_LOGOUT", session.getRevokedReason());
    }

    @Test
    void changePasswordUpdatesHashEnforcesPolicyAndRevokesActiveSessions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = withId(activeUser("OldPass1!"), userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1", Instant.now().plusSeconds(60));
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(session));

        authService.changePassword("admin", new ChangePasswordRequest("OldPass1!", "NewPass1!"));

        assertFalse(passwordEncoder.matches("OldPass1!", user.getPasswordHash()));
        assertFalse(session.activeAt(Instant.now()));
        assertEquals("PASSWORD_CHANGED", session.getRevokedReason());
    }

    @Test
    void changePasswordRejectsWeakPassword() {
        final User user = activeUser("OldPass1!");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        final IdentityException exception = assertThrows(IdentityException.class, () -> authService.changePassword(
                "admin", new ChangePasswordRequest("OldPass1!", "weak")));

        assertEquals(ApiErrorCode.VALIDATION_FAILED, exception.getCode());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        final User user = activeUser("OldPass1!");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        assertThrows(IdentityException.class, () -> authService.changePassword(
                "admin", new ChangePasswordRequest("WrongPass1!", "NewPass1!")));

        verify(sessionRepository, never()).findByUserIdAndRevokedAtIsNull(any());
    }

    private User activeUser(final String rawPassword) {
        final Role role = new Role("SUPER_ADMIN", "Super administrator", Map.of("user.read", true), true);
        return new User("admin", "Administrator", passwordEncoder.encode(rawPassword), role);
    }

    private User withId(final User user, final UUID id) throws Exception {
        final Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }
}

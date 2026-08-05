package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final LoginHistoryRepository loginHistoryRepository = mock(LoginHistoryRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final TokenService tokenService = mock(TokenService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuthService authService = new AuthService(userRepository, sessionRepository,
            loginHistoryRepository, passwordEncoder, tokenService, permissionService);

    @Test
    void loginReturnsTokenAndEffectivePermissions() {
        final User user = activeUser("correct-password");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(tokenService.createToken()).thenReturn("plain-token");
        when(tokenService.hash("plain-token")).thenReturn("hashed-token");
        when(permissionService.effectivePermissions(isNull())).thenReturn(Set.of("identity.user.read"));

        final LoginResponse response = authService.login(
                new LoginRequest("admin", "correct-password", "client-1"), "127.0.0.1");

        assertEquals("plain-token", response.token());
        assertEquals("admin", response.username());
        assertEquals(Set.of("identity.user.read"), response.permissions());
        assertNotNull(response.expiresAt());
        verify(sessionRepository).save(any(UserSession.class));
        verify(loginHistoryRepository).save(any());
    }

    @Test
    void invalidPasswordLocksAccountAfterFiveAttempts() {
        final User user = activeUser("correct-password");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IdentityException.class, () -> authService.login(
                    new LoginRequest("admin", "wrong-password", "client-1"), "127.0.0.1"));
        }

        assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("admin", "correct-password", "client-1"), "127.0.0.1"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void unknownUserRecordsFailedLogin() {
        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());

        assertThrows(IdentityException.class, () -> authService.login(
                new LoginRequest("missing", "password", "client-1"), "127.0.0.1"));

        verify(loginHistoryRepository).save(any());
    }

    @Test
    void logoutRevokesMatchingSession() {
        final UserSession session = new UserSession(activeUser("password"), "hashed-token", "client-1",
                Instant.now().plusSeconds(60));
        when(tokenService.hash("plain-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(session));

        authService.logout("plain-token");

        assertFalse(session.activeAt(Instant.now()));
    }

    @Test
    void changePasswordUpdatesHashAndRevokesActiveSessions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = activeUser("old-password");
        setId(user, userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1", Instant.now().plusSeconds(60));
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(session));

        authService.changePassword("admin", new ChangePasswordRequest("old-password", "new-password"));

        assertFalse(passwordEncoder.matches("old-password", user.getPasswordHash()));
        assertFalse(session.activeAt(Instant.now()));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        final User user = activeUser("old-password");
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        assertThrows(IdentityException.class, () -> authService.changePassword(
                "admin", new ChangePasswordRequest("wrong-password", "new-password")));

        verify(sessionRepository, never()).findByUserIdAndRevokedAtIsNull(any());
    }

    private User activeUser(final String rawPassword) {
        final Role role = new Role("ADMIN", "Administrator", Map.of("identity.user.read", true), true);
        return new User("admin", "Administrator", passwordEncoder.encode(rawPassword), role);
    }

    private void setId(final User user, final UUID id) throws Exception {
        final Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}

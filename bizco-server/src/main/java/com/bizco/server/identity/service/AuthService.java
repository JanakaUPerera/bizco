package com.bizco.server.identity.service;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.identity.AuthResponses.AuthenticatedUser;
import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
import com.bizco.common.dto.identity.AuthResponses.CurrentSessionResponse;
import com.bizco.common.dto.identity.AuthResponses.LoginData;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.LoginHistory;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final int maxConcurrentSessions;

    public AuthService(final UserRepository userRepository, final UserSessionRepository sessionRepository,
                       final LoginHistoryRepository loginHistoryRepository, final PasswordEncoder passwordEncoder,
                       final TokenService tokenService, final PermissionService permissionService,
                       final AuditService auditService,
                       @Value("${bizco.security.max-concurrent-sessions:3}") final int maxConcurrentSessions) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public LoginResponse login(final LoginRequest request, final String ipAddress) {
        final Instant now = Instant.now();
        final User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> recordAndReject(request, ipAddress, ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!user.isActive()) {
            recordLogin(user, request, ipAddress, false, "Account inactive");
            throw identity(ApiErrorCode.AUTH_ACCOUNT_INACTIVE, HttpStatus.UNAUTHORIZED, "Account is inactive");
        }
        if (user.isLocked() && !user.lockExpired(now)) {
            recordLogin(user, request, ipAddress, false, "Account locked");
            throw identity(ApiErrorCode.AUTH_ACCOUNT_LOCKED, HttpStatus.UNAUTHORIZED, "Account is locked");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now.plus(LOCK_DURATION));
            recordLogin(user, request, ipAddress, false, "Invalid credentials");
            if (user.isLocked()) {
                auditService.record("USER", userEntityId(user, request.username()), "ACCOUNT_LOCKED", user.getId(),
                        Map.of("username", user.getUsername(), "reason", "FAILED_LOGIN_LIMIT"));
            }
            throw identity(ApiErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (activeSessionCount(user.getId(), now) >= maxConcurrentSessions) {
            recordLogin(user, request, ipAddress, false, "Concurrent session limit");
            throw identity(ApiErrorCode.AUTH_CONCURRENT_SESSION_LIMIT, HttpStatus.TOO_MANY_REQUESTS,
                    "Concurrent session limit reached");
        }

        user.recordSuccessfulLogin(now);
        final String token = tokenService.createToken();
        final Instant expiresAt = now.plus(IDLE_TIMEOUT);
        sessionRepository.save(new UserSession(user, tokenService.hash(token), request.clientId(), ipAddress, expiresAt));
        recordLogin(user, request, ipAddress, true, null);
        return new LoginResponse(new LoginData(token, expiresAt, authenticatedUser(user)));
    }

    @Transactional
    public void logout(final String token) {
        sessionRepository.findByTokenHash(tokenService.hash(token))
                .ifPresent(session -> {
                    session.revoke("LOGOUT");
                    auditService.record("USER_SESSION", userEntityId(session.getUser(), session.getTokenHash()),
                            "AUTH_LOGOUT", session.getUser().getId(), Map.of("reason", "LOGOUT"));
                });
    }

    @Transactional(readOnly = true)
    public CurrentSessionResponse currentSession(final String token) {
        final Instant now = Instant.now();
        final UserSession session = sessionRepository.findByTokenHash(tokenService.hash(token))
                .orElseThrow(() -> identity(ApiErrorCode.AUTH_SESSION_INVALID, HttpStatus.UNAUTHORIZED,
                        "Session is invalid"));
        if (!session.activeAt(now)) {
            throw identity(session.getRevokedAt() == null ? ApiErrorCode.AUTH_SESSION_EXPIRED : ApiErrorCode.AUTH_SESSION_INVALID,
                    HttpStatus.UNAUTHORIZED, "Session is expired or revoked");
        }
        final User user = session.getUser();
        final Set<String> permissions = permissionService.effectivePermissions(user.getId());
        return new CurrentSessionResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPrimaryRole() == null ? null : user.getPrimaryRole().getCode(),
                session.getExpiresAt(), permissions, user.isMustChangePassword());
    }

    @Transactional
    public void revokeUserSessions(final UUID userId, final String reason) {
        sessionRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(session -> session.activeAt(Instant.now()))
                .forEach(session -> {
                    final String revocationReason = reason == null || reason.isBlank() ? "FORCE_LOGOUT" : reason;
                    session.revoke(revocationReason);
                    auditService.record("USER_SESSION", userId.toString(), "AUTH_SESSION_REVOKED",
                            userId, Map.of("reason", revocationReason));
                });
    }

    @Transactional
    public void changePassword(final String username, final ChangePasswordRequest request) {
        final User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IdentityException("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw identity(ApiErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Current password is incorrect");
        }
        validatePasswordPolicy(request.newPassword());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        sessionRepository.findByUserIdAndRevokedAtIsNull(user.getId())
                .forEach(session -> session.revoke("PASSWORD_CHANGED"));
        auditService.record("USER", user.getId().toString(), "AUTH_PASSWORD_CHANGED", user.getId(),
                Map.of("username", user.getUsername()));
    }

    private IdentityException recordAndReject(final LoginRequest request, final String ipAddress,
                                              final ApiErrorCode code, final HttpStatus status, final String reason) {
        recordLogin(null, request, ipAddress, false, reason);
        return identity(code, status, reason);
    }

    private void recordLogin(final User user, final LoginRequest request, final String ipAddress,
                             final boolean success, final String reason) {
        loginHistoryRepository.save(new LoginHistory(user == null ? null : user.getId(),
                request.username(), request.clientId(), ipAddress, success, reason));
        final Map<String, Object> details = new HashMap<>();
        details.put("attemptedUsername", request.username());
        details.put("success", success);
        details.put("reason", reason);
        auditService.record("LOGIN_HISTORY", user == null ? request.username() : userEntityId(user, request.username()),
                success ? "AUTH_LOGIN_SUCCESS" : "AUTH_LOGIN_FAILED",
                user == null ? null : user.getId(), details, Map.of(), ipAddress, request.clientId());
    }

    private int activeSessionCount(final UUID userId, final Instant now) {
        final var sessions = sessionRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (sessions == null) {
            return 0;
        }
        return (int) sessions.stream()
                .filter(session -> session.activeAt(now))
                .count();
    }

    private AuthenticatedUser authenticatedUser(final User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPrimaryRole() == null ? null : user.getPrimaryRole().getCode(),
                permissionService.effectivePermissions(user.getId()), user.isMustChangePassword());
    }

    private void validatePasswordPolicy(final String password) {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Password does not meet policy");
        }
    }

    private IdentityException identity(final ApiErrorCode code, final HttpStatus status, final String message) {
        return new IdentityException(code, status, message);
    }

    private String userEntityId(final User user, final String fallback) {
        return user.getId() == null ? fallback : user.getId().toString();
    }
}

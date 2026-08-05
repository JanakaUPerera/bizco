package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.server.identity.entity.LoginHistory;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration SESSION_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PermissionService permissionService;

    public AuthService(final UserRepository userRepository, final UserSessionRepository sessionRepository,
                       final LoginHistoryRepository loginHistoryRepository, final PasswordEncoder passwordEncoder,
                       final TokenService tokenService, final PermissionService permissionService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
    }

    @Transactional
    public LoginResponse login(final LoginRequest request, final String ipAddress) {
        final Instant now = Instant.now();
        final User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> recordAndReject(request, ipAddress, "Invalid credentials"));
        if (!user.canAuthenticate(now)) {
            recordLogin(user, request, ipAddress, false, "Account locked or disabled");
            throw new IdentityException("Account locked or disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now.plus(LOCK_DURATION));
            recordLogin(user, request, ipAddress, false, "Invalid credentials");
            throw new IdentityException("Invalid credentials");
        }

        user.recordSuccessfulLogin(now);
        final String token = tokenService.createToken();
        final Instant expiresAt = now.plus(SESSION_DURATION);
        sessionRepository.save(new UserSession(user, tokenService.hash(token), request.clientId(), expiresAt));
        recordLogin(user, request, ipAddress, true, null);
        return new LoginResponse(token, expiresAt, user.getId(), user.getUsername(),
                user.getDisplayName(), permissionService.effectivePermissions(user.getId()));
    }

    @Transactional
    public void logout(final String token) {
        sessionRepository.findByTokenHash(tokenService.hash(token)).ifPresent(UserSession::revoke);
    }

    @Transactional
    public void changePassword(final String username, final ChangePasswordRequest request) {
        final User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IdentityException("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IdentityException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        sessionRepository.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(UserSession::revoke);
    }

    private IdentityException recordAndReject(final LoginRequest request, final String ipAddress, final String reason) {
        recordLogin(null, request, ipAddress, false, reason);
        return new IdentityException(reason);
    }

    private void recordLogin(final User user, final LoginRequest request, final String ipAddress,
                             final boolean success, final String reason) {
        loginHistoryRepository.save(new LoginHistory(user == null ? null : user.getId(),
                request.username(), request.clientId(), ipAddress, success, reason));
    }
}

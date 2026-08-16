package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.ResetPasswordResponse;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String TEMP_PASSWORD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_PASSWORD_LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String TEMP_PASSWORD_DIGITS = "23456789";
    private static final String TEMP_PASSWORD_SYMBOLS = "!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AuthService authService;

    public UserService(final UserRepository userRepository, final RoleRepository roleRepository,
                       final PasswordEncoder passwordEncoder, final AuditService auditService,
                       final AuthService authService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(final UUID userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User was not found"));
    }

    @Transactional
    public UserResponse createUser(final UserCreateRequest request) {
        userRepository.findByUsernameIgnoreCase(request.username()).ifPresent(user -> {
            throw new IdentityException("Username already exists");
        });
        final Role role = roleRepository.findById(request.primaryRoleId())
                .orElseThrow(() -> new IdentityException("Primary role not found"));
        final User user = new User(request.username(), request.displayName(),
                passwordEncoder.encode(request.password()), role);
        final User saved = userRepository.save(user);
        auditService.record("USER", entityId(saved, request.username()), "USER_CREATED",
                null, Map.of("username", saved.getUsername()));
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(final UUID userId, final UserUpdateRequest request) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        verifyVersion("User", user.getVersion(), request.version());
        final Role role = roleRepository.findById(request.primaryRoleId())
                .orElseThrow(() -> new IdentityException("Primary role not found"));
        final Map<String, Object> changedFields = new HashMap<>();
        changedFields.put("displayName", user.getDisplayName());
        changedFields.put("primaryRoleId", user.getPrimaryRole() == null ? null : user.getPrimaryRole().getId());
        changedFields.put("active", user.isActive());
        user.update(request.displayName(), role, request.active());
        auditService.record("USER", entityId(user, userId.toString()), "USER_UPDATED", null,
                Map.of("username", user.getUsername()), changedFields, null, null);
        return toResponse(user);
    }

    @Transactional
    public void lockUser(final UUID userId) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        user.lockUntil(Instant.now().plus(Duration.ofMinutes(30)));
        auditService.record("USER", entityId(user, userId.toString()), "ACCOUNT_LOCKED", null,
                Map.of("username", user.getUsername()));
    }

    @Transactional
    public void unlockUser(final UUID userId) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        user.unlock();
        auditService.record("USER", entityId(user, userId.toString()), "ACCOUNT_UNLOCKED", null,
                Map.of("username", user.getUsername()));
    }

    @Transactional
    public ResetPasswordResponse resetPassword(final UUID userId, final Authentication authentication) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found"));
        final String temporaryPassword = generateTemporaryPassword();
        user.resetPassword(passwordEncoder.encode(temporaryPassword));
        authService.revokeUserSessions(userId, "PASSWORD_RESET");
        auditService.record("USER", entityId(user, userId.toString()), "USER_PASSWORD_RESET", actor(authentication),
                Map.of("username", user.getUsername()));
        return new ResetPasswordResponse(userId, temporaryPassword);
    }

    UserResponse toResponse(final User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPrimaryRole().getId(), user.getStatus().name(), user.isActive(), user.getLastLoginAt(),
                user.getVersion());
    }

    private void verifyVersion(final String entityName, final long currentVersion, final long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    entityName + " was modified by another user");
        }
    }

    private String entityId(final User user, final String fallback) {
        return user.getId() == null ? fallback : user.getId().toString();
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(User::getId).orElse(null);
    }

    /**
     * Builds a random temporary password that already satisfies {@code AuthService}'s password
     * policy (length 8+, upper, lower, digit, symbol), so the user can log in and is then forced
     * to choose their own password via {@code mustChangePassword}.
     */
    private String generateTemporaryPassword() {
        final List<Character> characters = new ArrayList<>();
        characters.add(TEMP_PASSWORD_UPPER.charAt(RANDOM.nextInt(TEMP_PASSWORD_UPPER.length())));
        characters.add(TEMP_PASSWORD_LOWER.charAt(RANDOM.nextInt(TEMP_PASSWORD_LOWER.length())));
        characters.add(TEMP_PASSWORD_DIGITS.charAt(RANDOM.nextInt(TEMP_PASSWORD_DIGITS.length())));
        characters.add(TEMP_PASSWORD_SYMBOLS.charAt(RANDOM.nextInt(TEMP_PASSWORD_SYMBOLS.length())));
        final String pool = TEMP_PASSWORD_UPPER + TEMP_PASSWORD_LOWER + TEMP_PASSWORD_DIGITS + TEMP_PASSWORD_SYMBOLS;
        for (int i = 0; i < 8; i++) {
            characters.add(pool.charAt(RANDOM.nextInt(pool.length())));
        }
        Collections.shuffle(characters, RANDOM);
        final StringBuilder password = new StringBuilder(characters.size());
        characters.forEach(password::append);
        return password.toString();
    }
}


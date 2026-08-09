package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(final UserRepository userRepository, final RoleRepository roleRepository,
                       final PasswordEncoder passwordEncoder, final AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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
}


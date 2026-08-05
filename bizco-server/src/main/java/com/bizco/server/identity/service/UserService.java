package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(final UserRepository userRepository, final RoleRepository roleRepository,
                       final PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
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
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(final UUID userId, final UserUpdateRequest request) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new IdentityException("User not found"));
        final Role role = roleRepository.findById(request.primaryRoleId())
                .orElseThrow(() -> new IdentityException("Primary role not found"));
        user.update(request.displayName(), role, request.active());
        return toResponse(user);
    }

    UserResponse toResponse(final User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPrimaryRole().getId(), user.getStatus().name(), user.isActive(), user.getLastLoginAt());
    }
}


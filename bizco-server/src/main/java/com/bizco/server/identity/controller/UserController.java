package com.bizco.server.identity.controller;

import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.EffectivePermissionsResponse;
import com.bizco.common.dto.identity.UserResponses.ResetPasswordResponse;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.server.identity.service.AuthService;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.identity.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/identity/users", "/api/v1/users"})
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final AuthService authService;

    public UserController(final UserService userService, final PermissionService permissionService,
                          final AuthService authService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user.create')")
    UserResponse createUser(@RequestBody final UserCreateRequest request) {
        return userService.createUser(request);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user.read')")
    UserResponse getUser(@PathVariable final UUID userId) {
        return userService.getUser(userId);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('user.update')")
    UserResponse updateUser(@PathVariable final UUID userId, @RequestBody final UserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }

    @GetMapping("/{userId}/effective-permissions")
    @PreAuthorize("hasAuthority('user.read')")
    EffectivePermissionsResponse effectivePermissions(@PathVariable final UUID userId) {
        return new EffectivePermissionsResponse(userId, permissionService.effectivePermissions(userId));
    }

    @PostMapping("/{userId}/sessions/revoke")
    @PreAuthorize("hasAuthority('user.session.revoke')")
    void revokeSessions(@PathVariable final UUID userId) {
        authService.revokeUserSessions(userId, "FORCE_LOGOUT");
    }

    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasAuthority('user.lock')")
    void lockUser(@PathVariable final UUID userId) {
        userService.lockUser(userId);
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasAuthority('user.unlock')")
    void unlockUser(@PathVariable final UUID userId) {
        userService.unlockUser(userId);
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('user.reset_password')")
    ResetPasswordResponse resetPassword(@PathVariable final UUID userId, final Authentication authentication) {
        return userService.resetPassword(userId, authentication);
    }
}

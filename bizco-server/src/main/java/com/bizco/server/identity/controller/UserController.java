package com.bizco.server.identity.controller;

import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.EffectivePermissionsResponse;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.identity.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/identity/users")
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;

    public UserController(final UserService userService, final PermissionService permissionService) {
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.user.read')")
    List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('identity.user.write')")
    UserResponse createUser(@RequestBody final UserCreateRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('identity.user.write')")
    UserResponse updateUser(@PathVariable final UUID userId, @RequestBody final UserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }

    @GetMapping("/{userId}/effective-permissions")
    @PreAuthorize("hasAuthority('identity.user.read')")
    EffectivePermissionsResponse effectivePermissions(@PathVariable final UUID userId) {
        return new EffectivePermissionsResponse(userId, permissionService.effectivePermissions(userId));
    }
}

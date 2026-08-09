package com.bizco.server.identity.controller;

import com.bizco.common.dto.identity.PermissionResponses.PermissionResponse;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.server.identity.service.RoleService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/identity", "/api/v1"})
public class RoleController {

    private final RoleService roleService;

    public RoleController(final RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role.read')")
    List<RoleResponse> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role.read')")
    List<PermissionResponse> listPermissions() {
        return roleService.listPermissions();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role.create')")
    RoleResponse createRole(@RequestBody final RoleUpsertRequest request) {
        return roleService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role.update')")
    RoleResponse updateRole(@PathVariable final Long roleId, @RequestBody final RoleUpsertRequest request) {
        return roleService.updateRole(roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role.delete')")
    ResponseEntity<Void> deleteRole(@PathVariable final Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/users/{userId}/secondary-roles", "/users/{userId}/roles"})
    @PreAuthorize("hasAuthority('user.grant_role')")
    SecondaryRoleResponse grantSecondaryRole(@PathVariable final UUID userId,
                                             @RequestBody final SecondaryRoleGrantRequest request) {
        return roleService.grantSecondaryRole(userId, request);
    }

    @GetMapping({"/users/{userId}/secondary-roles", "/users/{userId}/roles"})
    @PreAuthorize("hasAuthority('user.read')")
    List<SecondaryRoleResponse> listSecondaryRoles(@PathVariable final UUID userId) {
        return roleService.listSecondaryRoles(userId);
    }

    @DeleteMapping({"/secondary-roles/{grantId}", "/users/{userId}/roles/{grantId}"})
    @PreAuthorize("hasAuthority('user.revoke_role')")
    ResponseEntity<Void> revokeSecondaryRole(@PathVariable final UUID grantId) {
        roleService.revokeSecondaryRole(grantId);
        return ResponseEntity.noContent().build();
    }
}

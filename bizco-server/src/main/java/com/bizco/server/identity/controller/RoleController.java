package com.bizco.server.identity.controller;

import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.server.identity.service.RoleService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/identity")
public class RoleController {

    private final RoleService roleService;

    public RoleController(final RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    List<RoleResponse> listRoles() {
        return roleService.listRoles();
    }

    @PostMapping("/roles")
    RoleResponse createRole(@RequestBody final RoleUpsertRequest request) {
        return roleService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    RoleResponse updateRole(@PathVariable final UUID roleId, @RequestBody final RoleUpsertRequest request) {
        return roleService.updateRole(roleId, request);
    }

    @PostMapping("/users/{userId}/secondary-roles")
    SecondaryRoleResponse grantSecondaryRole(@PathVariable final UUID userId,
                                             @RequestBody final SecondaryRoleGrantRequest request) {
        return roleService.grantSecondaryRole(userId, request);
    }

    @GetMapping("/users/{userId}/secondary-roles")
    List<SecondaryRoleResponse> listSecondaryRoles(@PathVariable final UUID userId) {
        return roleService.listSecondaryRoles(userId);
    }

    @DeleteMapping("/secondary-roles/{grantId}")
    ResponseEntity<Void> revokeSecondaryRole(@PathVariable final UUID grantId) {
        roleService.revokeSecondaryRole(grantId);
        return ResponseEntity.noContent().build();
    }
}


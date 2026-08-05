package com.bizco.server.system;

import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.common.dto.system.SystemResponses.BusinessProfileResponse;
import com.bizco.server.system.service.BusinessProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/business")
public class BusinessProfileController {

    private final BusinessProfileService service;

    public BusinessProfileController(final BusinessProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.role.read')")
    ResponseEntity<BusinessProfileResponse> getProfile() {
        return service.findProfile().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('identity.role.write')")
    BusinessProfileResponse saveProfile(@RequestBody final BusinessProfileRequest request) {
        return service.saveProfile(request);
    }
}

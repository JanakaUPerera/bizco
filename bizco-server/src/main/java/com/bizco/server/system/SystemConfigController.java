package com.bizco.server.system;

import com.bizco.common.dto.system.SystemRequests.SystemConfigUpsertRequest;
import com.bizco.common.dto.system.SystemResponses.SystemConfigEntryResponse;
import com.bizco.common.dto.system.SystemResponses.SystemConfigListResponse;
import com.bizco.server.system.service.SystemConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/config")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(final SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system.config.read')")
    SystemConfigListResponse list() {
        return service.list();
    }

    @GetMapping("/{configKey}")
    @PreAuthorize("hasAuthority('system.config.read')")
    SystemConfigEntryResponse get(@PathVariable final String configKey) {
        return service.get(configKey);
    }

    @PutMapping("/{configKey}")
    @PreAuthorize("hasAuthority('system.config')")
    SystemConfigEntryResponse upsert(@PathVariable final String configKey,
                                     @RequestBody final SystemConfigUpsertRequest request,
                                     final Authentication authentication) {
        return service.upsert(configKey, request, authentication);
    }
}

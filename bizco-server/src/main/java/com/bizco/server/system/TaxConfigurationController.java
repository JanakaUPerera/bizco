package com.bizco.server.system;

import com.bizco.common.dto.system.SystemRequests.TaxConfigurationRequest;
import com.bizco.common.dto.system.SystemResponses.TaxConfigurationResponse;
import com.bizco.server.system.service.TaxConfigurationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/settings/tax", "/api/v1/settings/tax", "/api/v1/system/tax"})
public class TaxConfigurationController {

    private final TaxConfigurationService service;

    public TaxConfigurationController(final TaxConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system.config.read')")
    TaxConfigurationResponse getConfiguration() {
        return service.getConfiguration();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system.config')")
    TaxConfigurationResponse saveConfiguration(@RequestBody final TaxConfigurationRequest request) {
        return service.saveConfiguration(request);
    }
}

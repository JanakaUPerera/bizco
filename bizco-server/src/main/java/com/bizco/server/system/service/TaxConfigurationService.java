package com.bizco.server.system.service;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.system.SystemRequests.TaxConfigurationRequest;
import com.bizco.common.dto.system.SystemResponses.TaxConfigurationResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.system.entity.TaxConfiguration;
import com.bizco.server.system.repository.TaxConfigurationRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxConfigurationService {

    private final TaxConfigurationRepository repository;
    private final AuditService auditService;

    public TaxConfigurationService(final TaxConfigurationRepository repository, final AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TaxConfigurationResponse getConfiguration() {
        return repository.findById((short) 1)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(new TaxConfiguration(false, new BigDecimal("18.0000"))));
    }

    @Transactional
    public TaxConfigurationResponse saveConfiguration(final TaxConfigurationRequest request) {
        final TaxConfiguration configuration = repository.findById((short) 1)
                .orElseGet(() -> new TaxConfiguration(request.vatEnabled(), request.vatRate()));
        if (configuration.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Tax configuration was modified by another user");
        }
        final Map<String, Object> changedFields = Map.of(
                "vatEnabled", configuration.isVatEnabled(),
                "vatRate", configuration.getVatRate());
        configuration.update(request.vatEnabled(), request.vatRate());
        final TaxConfiguration saved = repository.save(configuration);
        auditService.record("TAX_CONFIGURATION", saved.getId().toString(), "TAX_CONFIGURATION_UPDATED",
                null, Map.of("vatEnabled", saved.isVatEnabled(), "vatRate", saved.getVatRate()),
                changedFields, null, null);
        return toResponse(saved);
    }

    private TaxConfigurationResponse toResponse(final TaxConfiguration configuration) {
        return new TaxConfigurationResponse(configuration.getId(), configuration.isVatEnabled(),
                configuration.getVatRate(), configuration.getVersion());
    }
}

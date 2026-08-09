package com.bizco.server.system.service;

import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.common.dto.system.SystemResponses.BusinessProfileResponse;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.system.entity.BusinessProfile;
import com.bizco.server.system.repository.BusinessProfileRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final BusinessProfileRepository repository;
    private final AuditService auditService;

    public BusinessProfileService(final BusinessProfileRepository repository, final AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<BusinessProfileResponse> findProfile() {
        return repository.findAll().stream()
                .min(Comparator.comparing(BusinessProfile::getId))
                .map(this::toResponse);
    }

    @Transactional
    public BusinessProfileResponse saveProfile(final BusinessProfileRequest request) {
        if (request.businessName() == null || request.businessName().isBlank()) {
            throw new IllegalArgumentException("Business name is required");
        }
        final BusinessProfile profile = repository.findAll().stream()
                .min(Comparator.comparing(BusinessProfile::getId))
                .orElseGet(() -> new BusinessProfile(request.businessName().trim()));
        verifyVersion(profile.getVersion(), request.version());
        final Map<String, Object> changedFields = new HashMap<>();
        changedFields.put("businessName", profile.getBusinessName());
        changedFields.put("vatRegistrationNumber", profile.getVatRegistrationNumber());
        changedFields.put("phone", profile.getPhone());
        changedFields.put("email", profile.getEmail());
        profile.update(trim(request.businessName()), trim(request.legalName()), trim(request.vatRegistrationNumber()),
                trim(request.phone()), trim(request.email()), trim(request.addressLine1()), trim(request.addressLine2()),
                trim(request.city()), trim(request.countryCode()), trim(request.currencyCode()), trim(request.timezone()));
        final BusinessProfile saved = repository.save(profile);
        auditService.record("BUSINESS_PROFILE", saved.getId().toString(), "BUSINESS_PROFILE_UPDATED",
                null, Map.of("businessName", saved.getBusinessName()), changedFields, null, null);
        return toResponse(saved);
    }

    private BusinessProfileResponse toResponse(final BusinessProfile profile) {
        return new BusinessProfileResponse(profile.getId(), profile.getBusinessName(), profile.getLegalName(),
                profile.getVatRegistrationNumber(), profile.getPhone(), profile.getEmail(), profile.getAddressLine1(),
                profile.getAddressLine2(), profile.getCity(), profile.getCountryCode(), profile.getCurrencyCode(),
                profile.getTimezone(), profile.getVersion());
    }

    private String trim(final String value) {
        return value == null ? null : value.trim();
    }

    private void verifyVersion(final long currentVersion, final long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Business profile was modified by another user");
        }
    }
}

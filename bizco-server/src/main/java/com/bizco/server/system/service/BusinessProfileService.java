package com.bizco.server.system.service;

import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.common.dto.system.SystemResponses.BusinessProfileResponse;
import com.bizco.server.system.entity.BusinessProfile;
import com.bizco.server.system.repository.BusinessProfileRepository;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final BusinessProfileRepository repository;

    public BusinessProfileService(final BusinessProfileRepository repository) {
        this.repository = repository;
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
        profile.update(trim(request.businessName()), trim(request.legalName()), trim(request.vatRegistrationNumber()),
                trim(request.phone()), trim(request.email()), trim(request.addressLine1()), trim(request.addressLine2()),
                trim(request.city()), trim(request.countryCode()), trim(request.currencyCode()), trim(request.timezone()));
        return toResponse(repository.save(profile));
    }

    private BusinessProfileResponse toResponse(final BusinessProfile profile) {
        return new BusinessProfileResponse(profile.getId(), profile.getBusinessName(), profile.getLegalName(),
                profile.getVatRegistrationNumber(), profile.getPhone(), profile.getEmail(), profile.getAddressLine1(),
                profile.getAddressLine2(), profile.getCity(), profile.getCountryCode(), profile.getCurrencyCode(),
                profile.getTimezone());
    }

    private String trim(final String value) {
        return value == null ? null : value.trim();
    }
}

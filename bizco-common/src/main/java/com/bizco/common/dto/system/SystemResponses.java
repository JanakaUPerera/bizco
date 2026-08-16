package com.bizco.common.dto.system;

import java.time.Instant;
import java.util.List;

public final class SystemResponses {

    private SystemResponses() {
    }

    public record BusinessProfileResponse(
            Short id,
            String businessName,
            String legalName,
            String vatRegistrationNumber,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String countryCode,
            String currencyCode,
            String timezone,
            long version
    ) {
    }

    public record TaxConfigurationResponse(
            Short id,
            boolean vatEnabled,
            java.math.BigDecimal vatRate,
            long version
    ) {
    }

    public record SystemConfigEntryResponse(
            String configKey,
            Object configValue,
            String description,
            Instant updatedAt,
            long version
    ) {
    }

    public record SystemConfigListResponse(List<SystemConfigEntryResponse> data) {
    }
}

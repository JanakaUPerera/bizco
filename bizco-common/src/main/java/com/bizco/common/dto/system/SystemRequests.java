package com.bizco.common.dto.system;

public final class SystemRequests {

    private SystemRequests() {
    }

    public record BusinessProfileRequest(
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

    public record TaxConfigurationRequest(
            boolean vatEnabled,
            java.math.BigDecimal vatRate,
            long version
    ) {
    }

    public record SystemConfigUpsertRequest(
            Object configValue,
            String description,
            long version
    ) {
    }
}

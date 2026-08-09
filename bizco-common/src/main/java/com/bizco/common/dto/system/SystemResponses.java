package com.bizco.common.dto.system;

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
}

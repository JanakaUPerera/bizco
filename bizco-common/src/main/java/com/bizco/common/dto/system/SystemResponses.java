package com.bizco.common.dto.system;

import java.util.UUID;

public final class SystemResponses {

    private SystemResponses() {
    }

    public record BusinessProfileResponse(
            UUID id,
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
            String timezone
    ) {
    }
}

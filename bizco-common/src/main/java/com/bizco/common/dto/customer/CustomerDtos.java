package com.bizco.common.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerCreateRequest(
            String name,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String nicNumber,
            String brNumber,
            String category,
            BigDecimal creditLimit,
            boolean consentMarketing,
            boolean consentDataSharing
    ) {
    }

    public record CustomerUpdateRequest(
            String name,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String nicNumber,
            String brNumber,
            String category,
            BigDecimal creditLimit,
            boolean consentMarketing,
            boolean consentDataSharing,
            long version
    ) {
    }

    public record CustomerSummaryResponse(
            UUID customerId,
            String customerCode,
            String name,
            String phone,
            String category,
            String status,
            BigDecimal creditLimit,
            boolean anonymized,
            long version
    ) {
    }

    public record CustomerDetailResponse(
            UUID customerId,
            String customerCode,
            String name,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String nicNumber,
            String brNumber,
            boolean piiRevealed,
            String category,
            String status,
            BigDecimal creditLimit,
            boolean consentMarketing,
            boolean consentDataSharing,
            Instant consentDate,
            boolean anonymized,
            Instant anonymizedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }

    public record CustomerCreditSummaryResponse(
            UUID customerId,
            BigDecimal creditLimit,
            BigDecimal outstandingReceivable,
            BigDecimal availableCredit,
            int oldestOutstandingDays,
            String eligibility,
            AgingBuckets aging
    ) {
    }

    public record AgingBuckets(
            BigDecimal days0To30,
            BigDecimal days31To60,
            BigDecimal days61To90,
            BigDecimal days91Plus
    ) {
    }

    public record CustomerSearchResponse(
            List<CustomerSummaryResponse> data,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}

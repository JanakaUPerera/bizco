package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierCreateRequest(String supplierCode, String name, String contactPerson, String address,
                                        String phone, String email, String tinNumber, String paymentTerms,
                                        BigDecimal openingBalance) {
    }

    public record SupplierUpdateRequest(String supplierCode, String name, String contactPerson, String address,
                                        String phone, String email, String tinNumber, String paymentTerms,
                                        BigDecimal openingBalance, String status, long version) {
    }

    public record SupplierSummaryResponse(UUID supplierId, String supplierCode, String name, String contactPerson,
                                          String phone, String email, BigDecimal openingBalance, String status,
                                          long version) {
    }

    public record SupplierDetailResponse(UUID supplierId, String supplierCode, String name, String contactPerson,
                                         String address, String phone, String email, String tinNumber,
                                         String paymentTerms, BigDecimal openingBalance, String status,
                                         Instant createdAt, Instant updatedAt, long version) {
    }

    public record SupplierSearchResponse(List<SupplierSummaryResponse> data, int page, int size,
                                         long totalElements, int totalPages) {
    }
}

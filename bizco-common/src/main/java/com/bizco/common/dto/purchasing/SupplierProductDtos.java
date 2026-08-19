package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupplierProductDtos {

    private SupplierProductDtos() {
    }

    public record SupplierProductCreateRequest(UUID supplierId, UUID productId, String supplierSku,
                                                BigDecimal purchasePrice, BigDecimal minOrderQty,
                                                Integer leadTimeDays, boolean preferred) {
    }

    public record SupplierProductUpdateRequest(String supplierSku, BigDecimal purchasePrice, BigDecimal minOrderQty,
                                                Integer leadTimeDays, boolean preferred, long version) {
    }

    public record SupplierProductResponse(UUID supplierProductId, UUID supplierId, String supplierName,
                                          UUID productId, String sku, String productName, String supplierSku,
                                          BigDecimal purchasePrice, BigDecimal minOrderQty, Integer leadTimeDays,
                                          BigDecimal lastPurchasePrice, boolean preferred, Instant createdAt,
                                          Instant updatedAt, long version) {
    }

    public record SupplierProductSearchResponse(List<SupplierProductResponse> data, int page, int size,
                                                long totalElements, int totalPages) {
    }
}

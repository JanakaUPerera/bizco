package com.bizco.common.dto.manufacturing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillOfMaterialsDtos {

    private BillOfMaterialsDtos() {
    }

    public record CreateBomRequest(UUID finishedVariantId, String name) {
    }

    public record UpdateBomRequest(String name, boolean active, long version) {
    }

    public record BomItemRequest(UUID componentVariantId, BigDecimal quantity, BigDecimal wastageQty) {
    }

    public record BomItemResponse(UUID bomItemId, UUID componentVariantId, String componentSku,
                                  String componentName, BigDecimal quantity, BigDecimal wastageQty,
                                  BigDecimal componentCostPrice, BigDecimal estimatedCost) {
    }

    public record BomDetailResponse(UUID bomId, UUID finishedVariantId, String finishedVariantSku,
                                    String finishedVariantName, String name, boolean active,
                                    BigDecimal totalEstimatedCost, Instant createdAt, Instant updatedAt,
                                    long version, List<BomItemResponse> items) {
    }

    public record BomSummaryResponse(UUID bomId, UUID finishedVariantId, String finishedVariantSku,
                                     String finishedVariantName, String name, boolean active,
                                     BigDecimal totalEstimatedCost, long version) {
    }

    public record BomSearchResponse(List<BomSummaryResponse> data, int page, int size, long totalElements,
                                    int totalPages) {
    }
}

package com.bizco.common.dto.manufacturing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductionOrderDtos {

    private ProductionOrderDtos() {
    }

    /** {@code productionMode} is {@code "STOCKED"} or {@code "MADE_TO_ORDER"} (SRS.md
     *  &sect;6.4.11.3). */
    public record ProduceRequest(UUID bomId, BigDecimal quantityToProduce, String productionMode, String notes) {
    }

    public record ProductionOrderItemResponse(UUID productionOrderItemId, UUID componentVariantId,
                                              String componentSku, String componentName,
                                              BigDecimal quantityConsumed, BigDecimal unitCostAtProduction,
                                              BigDecimal totalCost) {
    }

    public record ProductionOrderResponse(UUID productionOrderId, String productionNumber, UUID bomId,
                                          String bomName, UUID finishedVariantId, String finishedVariantSku,
                                          String finishedVariantName, BigDecimal quantityProduced,
                                          String productionMode, BigDecimal totalComponentCost, String notes,
                                          UUID createdBy, Instant createdAt,
                                          List<ProductionOrderItemResponse> items) {
    }

    public record ProductionOrderSummaryResponse(UUID productionOrderId, String productionNumber,
                                                 UUID finishedVariantId, String finishedVariantSku,
                                                 String finishedVariantName, BigDecimal quantityProduced,
                                                 String productionMode, BigDecimal totalComponentCost,
                                                 Instant createdAt) {
    }

    public record ProductionOrderSearchResponse(List<ProductionOrderSummaryResponse> data, int page, int size,
                                                long totalElements, int totalPages) {
    }
}

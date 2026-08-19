package com.bizco.common.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StockDtos {

    private StockDtos() {
    }

    public record StockMovementResponse(UUID stockMovementId, UUID productId, String sku, String productName,
                                        String movementType, BigDecimal quantity, String referenceType,
                                        UUID referenceId, UUID sourceLineId, String notes, UUID createdBy,
                                        Instant createdAt) {
    }

    public record StockMovementSearchResponse(List<StockMovementResponse> data, int page, int size,
                                              long totalElements, int totalPages) {
    }

    public record StockLevelResponse(UUID productId, String sku, String name, BigDecimal reorderPoint,
                                     BigDecimal physicalStock, BigDecimal reservedStock, BigDecimal availableStock,
                                     boolean lowStock) {
    }

    public record StockLevelSearchResponse(List<StockLevelResponse> data, int page, int size, long totalElements,
                                           int totalPages) {
    }

    public record LowStockSummaryResponse(long lowStockCount) {
    }

    public record CreateStockAdjustmentRequest(UUID productId, String adjustmentType, BigDecimal quantity,
                                                String reason) {
    }

    public record DecideStockAdjustmentRequest(String decisionReason, long version) {
    }

    public record StockAdjustmentResponse(UUID stockAdjustmentId, UUID productId, String sku, String productName,
                                          String adjustmentType, BigDecimal quantity, String reason, String status,
                                          UUID createdBy, Instant createdAt, UUID decidedBy, Instant decidedAt,
                                          String decisionReason, UUID reversesAdjustmentId, long version) {
    }

    public record StockAdjustmentSearchResponse(List<StockAdjustmentResponse> data, int page, int size,
                                                long totalElements, int totalPages) {
    }
}

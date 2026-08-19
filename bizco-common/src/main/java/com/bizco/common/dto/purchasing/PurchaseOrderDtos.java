package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record CreatePurchaseOrderRequest(UUID supplierId, LocalDate poDate, LocalDate expectedDate,
                                              LocalDate validUntil, String notes) {
    }

    public record UpdatePurchaseOrderHeaderRequest(LocalDate poDate, LocalDate expectedDate, LocalDate validUntil,
                                                    String notes, long version) {
    }

    public record AddPurchaseOrderItemRequest(UUID productId, BigDecimal quantityOrdered, BigDecimal unitPrice) {
    }

    public record DecidePurchaseOrderRequest(long version) {
    }

    public record CancelPurchaseOrderRequest(String reason, long version) {
    }

    public record PurchaseOrderItemResponse(UUID purchaseOrderItemId, int lineNumber, UUID productId, String sku,
                                            String productName, BigDecimal quantityOrdered, BigDecimal unitPrice,
                                            BigDecimal lineTotal) {
    }

    public record PurchaseOrderDetailResponse(UUID purchaseOrderId, String poNumber, UUID supplierId,
                                              String supplierName, LocalDate poDate, LocalDate expectedDate,
                                              LocalDate validUntil, String status, BigDecimal subtotal,
                                              BigDecimal totalAmount, UUID approvedBy, Instant approvedAt,
                                              String notes, Instant createdAt, Instant updatedAt, long version,
                                              List<PurchaseOrderItemResponse> items) {
    }

    public record PurchaseOrderSummaryResponse(UUID purchaseOrderId, String poNumber, UUID supplierId,
                                               String supplierName, LocalDate poDate, String status,
                                               BigDecimal totalAmount, long version) {
    }

    public record PurchaseOrderSearchResponse(List<PurchaseOrderSummaryResponse> data, int page, int size,
                                              long totalElements, int totalPages) {
    }
}

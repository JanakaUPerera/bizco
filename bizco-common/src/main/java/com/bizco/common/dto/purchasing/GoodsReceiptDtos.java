package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GoodsReceiptDtos {

    private GoodsReceiptDtos() {
    }

    public record CreateGoodsReceiptRequest(UUID purchaseOrderId, UUID supplierId, String supplierReference,
                                             LocalDate receiptDate, String notes) {
    }

    public record AddGoodsReceiptItemRequest(UUID purchaseOrderItemId, UUID productId, BigDecimal quantityReceived,
                                              BigDecimal quantityDamaged, BigDecimal quantityRejected,
                                              BigDecimal unitCost) {
    }

    public record PostGoodsReceiptRequest(long version) {
    }

    public record GoodsReceiptItemResponse(UUID goodsReceiptItemId, int lineNumber, UUID purchaseOrderItemId,
                                           UUID productId, String sku, String productName,
                                           BigDecimal quantityReceived, BigDecimal quantityDamaged,
                                           BigDecimal quantityRejected, BigDecimal usableQuantity,
                                           BigDecimal unitCost, BigDecimal totalCost) {
    }

    public record GoodsReceiptDetailResponse(UUID goodsReceiptId, String receiptNumber, UUID purchaseOrderId,
                                             String poNumber, UUID supplierId, String supplierName,
                                             String supplierReference, LocalDate receiptDate, String status,
                                             BigDecimal totalAmount, String notes, UUID createdBy, Instant createdAt,
                                             Instant postedAt, long version, List<GoodsReceiptItemResponse> items) {
    }

    public record GoodsReceiptSummaryResponse(UUID goodsReceiptId, String receiptNumber, UUID supplierId,
                                              String supplierName, LocalDate receiptDate, String status,
                                              BigDecimal totalAmount, long version) {
    }

    public record GoodsReceiptSearchResponse(List<GoodsReceiptSummaryResponse> data, int page, int size,
                                             long totalElements, int totalPages) {
    }

    public record ProductCostHistoryResponse(UUID productCostHistoryId, UUID productId, UUID goodsReceiptItemId,
                                             BigDecimal unitCost, Instant effectiveAt) {
    }

    public record ProductCostHistorySearchResponse(List<ProductCostHistoryResponse> data, int page, int size,
                                                   long totalElements, int totalPages) {
    }

    /** One row of {@code v_goods_receipt_outstanding} (DatabaseDesign.md &sect;18, Week 15 task
     *  15.5) - the supplier statement/outstanding-balance view. */
    public record GoodsReceiptOutstandingResponse(UUID goodsReceiptId, String receiptNumber, UUID supplierId,
                                                   String supplierName, LocalDate receiptDate, BigDecimal totalAmount,
                                                   BigDecimal returnedAmount, BigDecimal paidAmount,
                                                   BigDecimal outstandingAmount) {
    }

    public record GoodsReceiptOutstandingSearchResponse(List<GoodsReceiptOutstandingResponse> data, int page,
                                                         int size, long totalElements, int totalPages) {
    }
}

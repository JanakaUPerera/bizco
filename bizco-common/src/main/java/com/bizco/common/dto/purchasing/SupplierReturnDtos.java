package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupplierReturnDtos {

    private SupplierReturnDtos() {
    }

    /** {@code unitCost} is deliberately not client-supplied - it is read from the goods receipt
     *  line's own recorded {@code unit_cost}, the same "trust the ledger" rule the rest of this
     *  module follows. */
    public record CreateSupplierReturnItemRequest(UUID goodsReceiptItemId, BigDecimal quantityReturned) {
    }

    public record CreateSupplierReturnRequest(UUID supplierId, UUID goodsReceiptId, String reason,
                                               List<CreateSupplierReturnItemRequest> items) {
    }

    public record SupplierReturnItemResponse(UUID supplierReturnItemId, UUID goodsReceiptItemId, UUID productId,
                                              String sku, String productName, BigDecimal quantityReturned,
                                              BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record SupplierReturnResponse(UUID supplierReturnId, String returnNumber, UUID supplierId,
                                         String supplierName, UUID goodsReceiptId, String receiptNumber,
                                         BigDecimal totalAmount, String reason, UUID createdBy, Instant createdAt,
                                         List<SupplierReturnItemResponse> items) {
    }

    public record SupplierReturnSearchResponse(List<SupplierReturnResponse> data, int page, int size,
                                               long totalElements, int totalPages) {
    }
}

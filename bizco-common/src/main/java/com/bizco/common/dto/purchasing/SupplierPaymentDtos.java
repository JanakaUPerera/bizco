package com.bizco.common.dto.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupplierPaymentDtos {

    private SupplierPaymentDtos() {
    }

    public record SupplierPaymentAllocationRequest(UUID goodsReceiptId, BigDecimal amount) {
    }

    /** {@code allocations} may be empty - an unallocated payment is simply a supplier-account
     *  credit/prepayment (DatabaseDesign.md &sect;17.11). */
    public record RecordSupplierPaymentRequest(UUID supplierId, Instant paymentDate, String paymentMethod,
                                               BigDecimal amount, String referenceNumber, String notes,
                                               List<SupplierPaymentAllocationRequest> allocations) {
    }

    public record SupplierPaymentAllocationResponse(UUID goodsReceiptId, String receiptNumber, BigDecimal amount,
                                                     BigDecimal outstandingAfter) {
    }

    public record SupplierPaymentResponse(UUID supplierPaymentId, UUID supplierId, String supplierName,
                                          Instant paymentDate, String paymentMethod, BigDecimal amount,
                                          String referenceNumber, String notes, UUID paidBy, Instant createdAt,
                                          List<SupplierPaymentAllocationResponse> allocations) {
    }

    public record SupplierPaymentSearchResponse(List<SupplierPaymentResponse> data) {
    }
}

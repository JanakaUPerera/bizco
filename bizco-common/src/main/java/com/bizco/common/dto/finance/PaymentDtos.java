package com.bizco.common.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** ApiContracts.md &sect;17.1: one payment allocated in full to a single already-POSTED invoice. */
    public record RecordInvoicePaymentRequest(
            Instant paymentDate,
            String paymentMethod,
            BigDecimal amount,
            String referenceNumber,
            String notes
    ) {
    }

    public record PaymentAllocationRequest(
            UUID invoiceId,
            BigDecimal amount
    ) {
    }

    /** ApiContracts.md &sect;17.2: one payment split across several invoices for the same customer. */
    public record RecordCustomerPaymentRequest(
            UUID customerId,
            Instant paymentDate,
            String paymentMethod,
            BigDecimal amount,
            String referenceNumber,
            String notes,
            List<PaymentAllocationRequest> allocations
    ) {
    }

    public record PaymentAllocationResponse(
            UUID invoiceId,
            BigDecimal amount,
            BigDecimal invoiceBalanceAfter
    ) {
    }

    public record CustomerPaymentResponse(
            UUID customerPaymentId,
            UUID customerId,
            Instant paymentDate,
            String paymentMethod,
            BigDecimal amount,
            String referenceNumber,
            UUID receivedBy,
            List<PaymentAllocationResponse> allocations
    ) {
    }

    public record CustomerPaymentSearchResponse(
            List<CustomerPaymentResponse> data
    ) {
    }
}

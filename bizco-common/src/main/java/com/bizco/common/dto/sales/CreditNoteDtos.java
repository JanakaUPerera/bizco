package com.bizco.common.dto.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CreditNoteDtos {

    private CreditNoteDtos() {
    }

    public record CreditNoteLineRequest(
            UUID invoiceLineId,
            BigDecimal quantityReturned,
            boolean restock
    ) {
    }

    /** {@code type}: APPLY_TO_BALANCE | REFUND | CUSTOMER_CREDIT (ApiContracts.md &sect;18.2). */
    public record SettlementRequest(
            String type,
            String paymentMethod,
            UUID originalCustomerPaymentId
    ) {
    }

    public record CreateCreditNoteRequest(
            UUID originalInvoiceId,
            String reason,
            List<CreditNoteLineRequest> lines,
            SettlementRequest settlement
    ) {
    }

    public record CreditNoteLineResponse(
            UUID creditNoteLineId,
            UUID originalInvoiceLineId,
            BigDecimal quantityReturned,
            BigDecimal unitPriceSnapshot,
            BigDecimal taxableAmount,
            BigDecimal vatRateSnapshot,
            BigDecimal vatAmount,
            BigDecimal lineTotal,
            boolean restock
    ) {
    }

    public record CreditNoteResponse(
            UUID creditNoteId,
            String creditNoteNumber,
            UUID originalInvoiceId,
            UUID customerId,
            String status,
            String reason,
            BigDecimal subtotal,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            Instant issuedAt,
            UUID issuedBy,
            Instant appliedAt,
            List<CreditNoteLineResponse> lines
    ) {
    }

    public record CreditNoteSearchResponse(
            List<CreditNoteResponse> data
    ) {
    }

    public record ReturnEligibilityLineResponse(
            UUID invoiceLineId,
            String description,
            BigDecimal quantitySold,
            BigDecimal quantityAlreadyReturned,
            BigDecimal quantityRemaining,
            String taxCategory,
            BigDecimal vatRateSnapshot,
            boolean restockEligible
    ) {
    }

    public record ReturnEligibilityResponse(
            UUID invoiceId,
            LocalDate invoiceDate,
            LocalDate returnDeadline,
            boolean withinWindow,
            List<ReturnEligibilityLineResponse> lines
    ) {
    }
}

package com.bizco.common.dto.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record DiscountRequest(String type, BigDecimal value) {
        public static final DiscountRequest NONE = new DiscountRequest("NONE", BigDecimal.ZERO);
    }

    public record CreateDraftInvoiceRequest(
            LocalDate invoiceDate,
            LocalDate dueDate,
            String invoiceType,
            UUID customerId,
            String notes
    ) {
    }

    public record UpdateInvoiceHeaderRequest(
            LocalDate invoiceDate,
            LocalDate dueDate,
            String invoiceType,
            UUID customerId,
            DiscountRequest discount,
            String notes,
            long version
    ) {
    }

    public record AddInvoiceLineRequest(
            String lineType,
            UUID productId,
            UUID serviceId,
            String description,
            BigDecimal quantity,
            BigDecimal requestedUnitPrice,
            String taxCategory,
            DiscountRequest discount
    ) {
    }

    public record UpdateInvoiceLineRequest(
            BigDecimal quantity,
            BigDecimal requestedUnitPrice,
            DiscountRequest discount,
            long version
    ) {
    }

    public record InvoiceLineResponse(
            UUID invoiceLineId,
            int lineNumber,
            String lineType,
            UUID productId,
            UUID serviceId,
            String skuSnapshot,
            String descriptionSnapshot,
            String uomSnapshot,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            String taxCategorySnapshot,
            BigDecimal vatRateSnapshot,
            BigDecimal taxableAmount,
            BigDecimal vatAmount,
            BigDecimal lineTotalInclVat
    ) {
    }

    public record InvoiceDetailResponse(
            UUID invoiceId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String invoiceType,
            String status,
            UUID customerId,
            UUID cashierId,
            BigDecimal subtotal,
            String discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal vatRateSnapshot,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            String notes,
            Instant createdAt,
            Instant postedAt,
            long version,
            List<InvoiceLineResponse> lines
    ) {
    }

    public record InvoiceSummaryResponse(
            UUID invoiceId,
            String invoiceNumber,
            LocalDate invoiceDate,
            String status,
            UUID customerId,
            BigDecimal totalAmount,
            long version
    ) {
    }

    public record InvoiceSearchResponse(
            List<InvoiceSummaryResponse> data,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}

package com.bizco.common.dto.sales;

import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class HeldSaleDtos {

    private HeldSaleDtos() {
    }

    public record HeldSaleItemRequest(
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            DiscountRequest discount
    ) {
    }

    public record HoldSaleRequest(
            UUID customerId,
            List<HeldSaleItemRequest> items,
            String notes
    ) {
    }

    public record UpdateHeldSaleRequest(
            UUID customerId,
            List<HeldSaleItemRequest> items,
            String notes,
            long version
    ) {
    }

    public record HeldSaleItemResponse(
            UUID heldSaleItemId,
            UUID productId,
            String sku,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPriceSnapshot,
            String discountType,
            BigDecimal discountValue,
            BigDecimal estimatedLineTotal
    ) {
    }

    public record HeldSaleDetailResponse(
            UUID heldSaleId,
            String heldNumber,
            UUID customerId,
            UUID cashierId,
            String status,
            Instant heldAt,
            Instant expiresAt,
            UUID convertedInvoiceId,
            String notes,
            long version,
            List<HeldSaleItemResponse> items
    ) {
    }

    public record HeldSaleSummaryResponse(
            UUID heldSaleId,
            String heldNumber,
            UUID customerId,
            UUID cashierId,
            String status,
            Instant heldAt,
            Instant expiresAt,
            int itemCount,
            BigDecimal estimatedTotal
    ) {
    }

    public record HeldSaleSearchResponse(
            List<HeldSaleSummaryResponse> data
    ) {
    }
}

package com.bizco.server.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** StateMachines.md &sect;17: PENDING -&gt; APPROVED/REJECTED, terminal once decided. */
class StockAdjustmentTest {

    private final UUID productId = UUID.randomUUID();
    private final UUID requester = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    @Test
    void createsPending() {
        final StockAdjustment adjustment = new StockAdjustment(UUID.randomUUID(), productId, UUID.randomUUID(), AdjustmentType.POSITIVE,
                new BigDecimal("5.000"), "Stock count correction", requester, null);

        assertThat(adjustment.getStatus()).isEqualTo(StockAdjustmentStatus.PENDING);
        assertThat(adjustment.getDecidedBy()).isNull();
        assertThat(adjustment.getDecidedAt()).isNull();
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> new StockAdjustment(UUID.randomUUID(), productId, UUID.randomUUID(), AdjustmentType.POSITIVE,
                BigDecimal.ZERO, "reason", requester, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockAdjustment(UUID.randomUUID(), productId, UUID.randomUUID(), AdjustmentType.NEGATIVE,
                new BigDecimal("-1"), "reason", requester, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> new StockAdjustment(UUID.randomUUID(), productId, UUID.randomUUID(), AdjustmentType.POSITIVE,
                BigDecimal.ONE, "  ", requester, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stkAdj002ApproveSetsDecisionFields() {
        final StockAdjustment adjustment = pending(AdjustmentType.POSITIVE, "3.000");

        adjustment.approve(approver, "Confirmed by count", Instant.now());

        assertThat(adjustment.getStatus()).isEqualTo(StockAdjustmentStatus.APPROVED);
        assertThat(adjustment.getDecidedBy()).isEqualTo(approver);
        assertThat(adjustment.getDecidedAt()).isNotNull();
    }

    @Test
    void stkAdj003RejectLeavesNoStockEffectIntent() {
        final StockAdjustment adjustment = pending(AdjustmentType.NEGATIVE, "3.000");

        adjustment.reject(approver, "Not warranted", Instant.now());

        assertThat(adjustment.getStatus()).isEqualTo(StockAdjustmentStatus.REJECTED);
    }

    @Test
    void stkAdj004DoubleApprovalIsRejected() {
        final StockAdjustment adjustment = pending(AdjustmentType.POSITIVE, "3.000");
        adjustment.approve(approver, "first", Instant.now());

        assertThatThrownBy(() -> adjustment.approve(approver, "second", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectAfterApprovalIsRejected() {
        final StockAdjustment adjustment = pending(AdjustmentType.POSITIVE, "3.000");
        adjustment.approve(approver, "first", Instant.now());

        assertThatThrownBy(() -> adjustment.reject(approver, "too late", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signedQuantityIsPositiveOnlyForPositiveType() {
        assertThat(pending(AdjustmentType.POSITIVE, "4.000").signedQuantity()).isEqualByComparingTo("4.000");
        assertThat(pending(AdjustmentType.NEGATIVE, "4.000").signedQuantity()).isEqualByComparingTo("-4.000");
        assertThat(pending(AdjustmentType.DAMAGE, "4.000").signedQuantity()).isEqualByComparingTo("-4.000");
    }

    private StockAdjustment pending(final AdjustmentType type, final String quantity) {
        return new StockAdjustment(UUID.randomUUID(), productId, UUID.randomUUID(), type, new BigDecimal(quantity), "test reason",
                requester, null);
    }
}

package com.bizco.server.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A manual stock correction request (DatabaseDesign.md &sect;16.1, StateMachines.md &sect;17).
 * {@code inventory.adjustment.create} only ever produces {@code PENDING}; a separate
 * {@code inventory.adjustment.approve} decision moves it to {@code APPROVED} (posting exactly one
 * signed {@code ADJUSTMENT} movement) or {@code REJECTED} (no stock effect) - segregation of duties
 * so the requester cannot also be the approver of their own correction. Terminal once decided
 * (STK-ADJ-004): correcting an already-{@code APPROVED} adjustment requires a fresh adjustment
 * linked back via {@code reversesAdjustmentId}, not a state rollback.
 */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment {

    @Id
    @GeneratedValue
    @Column(name = "stock_adjustment_id")
    private UUID id;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private AdjustmentType adjustmentType;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockAdjustmentStatus status = StockAdjustmentStatus.PENDING;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "reverses_adjustment_id")
    private UUID reversesAdjustmentId;

    @Version
    private long version;

    protected StockAdjustment() {
    }

    public StockAdjustment(final UUID requestId, final UUID productId, final AdjustmentType adjustmentType,
                           final BigDecimal quantity, final String reason, final UUID createdBy,
                           final UUID reversesAdjustmentId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Adjustment quantity must be greater than zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Adjustment reason is required");
        }
        this.requestId = requestId;
        this.productId = productId;
        this.adjustmentType = adjustmentType;
        this.quantity = quantity;
        this.reason = reason.trim();
        this.createdBy = createdBy;
        this.reversesAdjustmentId = reversesAdjustmentId;
    }

    /** StateMachines.md &sect;17.4: PENDING -&gt; APPROVED. Throws {@link IllegalStateException} if
     *  not PENDING (STK-ADJ-004 double-approval guard). */
    public void approve(final UUID decidedBy, final String decisionReason, final Instant decidedAt) {
        requirePending();
        this.status = StockAdjustmentStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decisionReason = decisionReason;
        this.decidedAt = decidedAt;
    }

    /** StateMachines.md &sect;17.5: PENDING -&gt; REJECTED. No stock movement results. */
    public void reject(final UUID decidedBy, final String decisionReason, final Instant decidedAt) {
        requirePending();
        this.status = StockAdjustmentStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decisionReason = decisionReason;
        this.decidedAt = decidedAt;
    }

    private void requirePending() {
        if (status != StockAdjustmentStatus.PENDING) {
            throw new IllegalStateException("Stock adjustment is " + status + " and already decided");
        }
    }

    /** The signed effect on stock: POSITIVE adds, NEGATIVE/DAMAGE both subtract. */
    public BigDecimal signedQuantity() {
        return adjustmentType == AdjustmentType.POSITIVE ? quantity : quantity.negate();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getProductId() {
        return productId;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public StockAdjustmentStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public UUID getReversesAdjustmentId() {
        return reversesAdjustmentId;
    }

    public long getVersion() {
        return version;
    }
}

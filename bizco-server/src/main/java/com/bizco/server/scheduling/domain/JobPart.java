package com.bizco.server.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A part consumed on a {@link JobCard} (DomainModel.md &sect;14.4, StateMachines.md &sect;13).
 * {@code productId} is a plain foreign key, not a JPA association, same rule as
 * {@link com.bizco.server.sales.domain.InvoiceLine}. Immutable once created - "a posted JobPart
 * must not be silently edited/deleted" (StateMachines.md &sect;13) - so no mutator methods exist.
 *
 * <p>Consuming a part posts a {@code JOB_PART} stock movement keyed by this row's id
 * ({@code JobCardService#addPart}, DatabaseDesign.md &sect;20.3). A part later charged to the
 * customer is added to the invoice via {@code InvoiceService#addProductLineFromJobPart}, which
 * marks the generated {@code InvoiceLine} with {@code sourceJobPartId} so posting that invoice
 * skips a second, duplicate {@code SALE} deduction for stock this row already consumed.
 */
@Entity
@Table(name = "job_parts")
public class JobPart {

    @Id
    @GeneratedValue
    @Column(name = "job_part_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    /** Phase 6 Week 18 (DatabaseDesign.md §56.3 Step 4): drives stock locking/posting for this
     *  part's consumption; {@code productId} stays populated for display. */
    @Column(name = "product_variant_id")
    private UUID productVariantId;
    @Column(name = "quantity_used", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityUsed;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;
    @Column(name = "cost_price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal costPriceSnapshot;
    @Column(name = "is_warranty_covered", nullable = false)
    private boolean warrantyCovered;
    @Column(name = "posted_at", insertable = false, updatable = false)
    private Instant postedAt;

    protected JobPart() {
    }

    public JobPart(final UUID requestId, final UUID productId, final UUID productVariantId,
                   final BigDecimal quantityUsed, final BigDecimal unitPriceSnapshot,
                   final BigDecimal costPriceSnapshot, final boolean warrantyCovered) {
        this.requestId = requestId;
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.quantityUsed = quantityUsed;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.costPriceSnapshot = costPriceSnapshot;
        this.warrantyCovered = warrantyCovered;
    }

    void assignTo(final JobCard jobCard) {
        this.jobCard = jobCard;
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

    public UUID getProductVariantId() {
        return productVariantId;
    }

    public BigDecimal getQuantityUsed() {
        return quantityUsed;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public BigDecimal getCostPriceSnapshot() {
        return costPriceSnapshot;
    }

    public boolean isWarrantyCovered() {
        return warrantyCovered;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}

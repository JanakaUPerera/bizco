package com.bizco.server.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One posted line of the stock ledger (DatabaseDesign.md &sect;15, STK-LEDGER-001). Every physical
 * stock change - sale, sale void, GRN, customer return, supplier return, job part consumption,
 * adjustment - is exactly one signed row here; {@code SUM(quantity)} per product is the
 * authoritative on-hand figure (see the {@code v_stock_on_hand} view), never a denormalized counter
 * on {@code products}. Deliberately has no mutator beyond construction (STK-LEDGER-002 "movement
 * immutability") - a correction is always a new, oppositely-signed row (e.g. {@code SALE_VOID}
 * against a {@code SALE}, {@code ADJUSTMENT_REVERSAL} against an {@code ADJUSTMENT}), never an
 * update/delete of a posted one.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue
    @Column(name = "stock_movement_id")
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private StockReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    /** The precise source row - invoice_line_id, grn_item_id, credit_note_line_id,
     *  supplier_return_item_id, job_part_id, or stock_adjustment_id. Plain UUID, not a JPA
     *  association: it is polymorphic across six source tables (DatabaseDesign.md &sect;15.1). */
    @Column(name = "source_line_id")
    private UUID sourceLineId;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(final UUID productId, final MovementType movementType, final BigDecimal quantity,
                         final StockReferenceType referenceType, final UUID referenceId, final UUID sourceLineId,
                         final String notes, final UUID createdBy) {
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("Stock movement quantity must be non-zero");
        }
        this.productId = productId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sourceLineId = sourceLineId;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public StockReferenceType getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID getSourceLineId() {
        return sourceLineId;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

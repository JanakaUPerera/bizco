package com.bizco.server.manufacturing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One recipe line (DatabaseDesign.md &sect;57.1): {@code quantity} of {@code componentVariantId}
 *  required per unit of the parent {@link BillOfMaterials}'s finished variant, plus optional
 *  {@code wastageQty} - expected loss/offcut also physically consumed per unit produced but
 *  excluded from {@link #getEstimatedCost()} (SRS.md &sect;6.4.11.4's formula is cost_price ×
 *  quantity only; the gap between this estimate and a production's actual captured cost is
 *  exactly the wastage). */
@Entity
@Table(name = "bom_items")
public class BomItem {

    @Id
    @GeneratedValue
    @Column(name = "bom_item_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id")
    private BillOfMaterials billOfMaterials;

    @Column(name = "component_variant_id", nullable = false)
    private UUID componentVariantId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "wastage_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal wastageQty = BigDecimal.ZERO;

    @Column(name = "estimated_cost", precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    protected BomItem() {
    }

    public BomItem(final UUID componentVariantId, final BigDecimal quantity, final BigDecimal wastageQty) {
        this.componentVariantId = componentVariantId;
        applyQuantities(quantity, wastageQty);
    }

    /** Task 20.2: quantity/wastage are the only editable fields of an existing line - the
     *  component itself is immutable once added (remove and re-add instead, {@code
     *  BillOfMaterialsService.updateItem}'s contract). */
    public void update(final BigDecimal quantity, final BigDecimal wastageQty) {
        applyQuantities(quantity, wastageQty);
    }

    /** Task 20.3 cost roll-up: {@code BillOfMaterialsService} recomputes and overwrites this field
     *  from the component's *current* cost price on every read, so it is never stale - "cached at
     *  write time, corrected at read time", not a value this entity keeps in sync on its own. */
    public void applyEstimatedCost(final BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    void attachTo(final BillOfMaterials billOfMaterials) {
        this.billOfMaterials = billOfMaterials;
    }

    private void applyQuantities(final BigDecimal quantity, final BigDecimal wastageQty) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("BOM item quantity must be greater than zero");
        }
        final BigDecimal wastage = wastageQty == null ? BigDecimal.ZERO : wastageQty;
        if (wastage.signum() < 0) {
            throw new IllegalArgumentException("BOM item wastage quantity cannot be negative");
        }
        this.quantity = quantity;
        this.wastageQty = wastage;
    }

    public UUID getId() { return id; }
    public BillOfMaterials getBillOfMaterials() { return billOfMaterials; }
    public UUID getComponentVariantId() { return componentVariantId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getWastageQty() { return wastageQty; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
}

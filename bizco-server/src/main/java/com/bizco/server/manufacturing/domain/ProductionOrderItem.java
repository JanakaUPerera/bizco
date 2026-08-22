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

/** One component's actual consumption on a posted {@link ProductionOrder} (SRS.md &sect;6.4.11.4):
 *  {@code quantityConsumed} already includes the {@code BomItem}'s wastage allowance, and
 *  {@code unitCostAtProduction} is the component's cost price at the moment of posting - both
 *  captured here, never recomputed later, so this row stays this production's permanent audit
 *  trail even if the component's cost price or the BOM's recipe later changes. */
@Entity
@Table(name = "production_order_items")
public class ProductionOrderItem {

    @Id
    @GeneratedValue
    @Column(name = "production_order_item_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id")
    private ProductionOrder productionOrder;

    @Column(name = "component_variant_id", nullable = false)
    private UUID componentVariantId;

    @Column(name = "quantity_consumed", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityConsumed;

    @Column(name = "unit_cost_at_production", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCostAtProduction;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    protected ProductionOrderItem() {
    }

    public ProductionOrderItem(final UUID componentVariantId, final BigDecimal quantityConsumed,
                               final BigDecimal unitCostAtProduction, final BigDecimal totalCost) {
        this.componentVariantId = componentVariantId;
        this.quantityConsumed = quantityConsumed;
        this.unitCostAtProduction = unitCostAtProduction;
        this.totalCost = totalCost;
    }

    void attachTo(final ProductionOrder productionOrder) {
        this.productionOrder = productionOrder;
    }

    public UUID getId() { return id; }
    public ProductionOrder getProductionOrder() { return productionOrder; }
    public UUID getComponentVariantId() { return componentVariantId; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public BigDecimal getUnitCostAtProduction() { return unitCostAtProduction; }
    public BigDecimal getTotalCost() { return totalCost; }
}

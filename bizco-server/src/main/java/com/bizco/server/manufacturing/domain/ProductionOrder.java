package com.bizco.server.manufacturing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One posted Produce transaction (SRS.md &sect;6.4.11.2): unlike {@code GoodsReceipt} this has no
 *  DRAFT state - a row only ever exists already-posted, since the whole lock/validate/consume
 *  sequence is one atomic step with no intermediate editable document (Task 9). */
@Entity
@Table(name = "production_orders")
public class ProductionOrder {

    @Id
    @GeneratedValue
    @Column(name = "production_order_id")
    private UUID id;

    @Column(name = "production_number", unique = true)
    private String productionNumber;

    @Column(name = "bom_id", nullable = false)
    private UUID bomId;

    @Column(name = "finished_variant_id", nullable = false)
    private UUID finishedVariantId;

    @Column(name = "quantity_produced", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityProduced;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_mode", nullable = false, length = 20)
    private ProductionMode productionMode;

    @Column(name = "total_component_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalComponentCost = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "productionOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id asc")
    private final List<ProductionOrderItem> items = new ArrayList<>();

    protected ProductionOrder() {
    }

    public ProductionOrder(final String productionNumber, final UUID bomId, final UUID finishedVariantId,
                           final BigDecimal quantityProduced, final ProductionMode productionMode,
                           final String notes, final UUID createdBy) {
        this.productionNumber = productionNumber;
        this.bomId = bomId;
        this.finishedVariantId = finishedVariantId;
        this.quantityProduced = quantityProduced;
        this.productionMode = productionMode;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void addItem(final ProductionOrderItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void applyTotalComponentCost(final BigDecimal totalComponentCost) {
        this.totalComponentCost = totalComponentCost;
    }

    public UUID getId() { return id; }
    public String getProductionNumber() { return productionNumber; }
    public UUID getBomId() { return bomId; }
    public UUID getFinishedVariantId() { return finishedVariantId; }
    public BigDecimal getQuantityProduced() { return quantityProduced; }
    public ProductionMode getProductionMode() { return productionMode; }
    public BigDecimal getTotalComponentCost() { return totalComponentCost; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ProductionOrderItem> getItems() { return items; }
}

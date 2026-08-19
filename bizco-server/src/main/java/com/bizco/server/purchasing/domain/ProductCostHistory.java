package com.bizco.server.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One posted cost point for a product (DatabaseDesign.md &sect;17.7) - the audit trail behind
 *  {@code Product.recordPurchaseCost}'s "current default" field. Immutable by construction, the
 *  same rule {@code StockMovement} follows: never updated or deleted, only appended to. */
@Entity
@Table(name = "product_cost_history")
public class ProductCostHistory {

    @Id
    @GeneratedValue
    @Column(name = "product_cost_history_id")
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "goods_receipt_item_id", nullable = false, unique = true)
    private UUID goodsReceiptItemId;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "effective_at", insertable = false, updatable = false)
    private Instant effectiveAt;

    protected ProductCostHistory() {
    }

    public ProductCostHistory(final UUID productId, final UUID goodsReceiptItemId, final BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or greater");
        }
        this.productId = productId;
        this.goodsReceiptItemId = goodsReceiptItemId;
        this.unitCost = unitCost;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getGoodsReceiptItemId() {
        return goodsReceiptItemId;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}

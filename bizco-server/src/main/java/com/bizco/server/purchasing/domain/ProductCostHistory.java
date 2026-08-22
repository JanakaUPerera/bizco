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

    /** Phase 6 Week 18 (DatabaseDesign.md §56.3 Step 4): included even though it's not on
     *  DatabaseDesign.md §56.3's literal table list — this is a 1:1 audit trail derived from
     *  {@code goods_receipt_items} (which *is* on the list), and leaving it product-level would
     *  blend cost history across variants of the same product once variants diverge in price. */
    @Column(name = "product_variant_id")
    private UUID productVariantId;

    @Column(name = "goods_receipt_item_id", nullable = false, unique = true)
    private UUID goodsReceiptItemId;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "effective_at", insertable = false, updatable = false)
    private Instant effectiveAt;

    protected ProductCostHistory() {
    }

    public ProductCostHistory(final UUID productId, final UUID productVariantId, final UUID goodsReceiptItemId,
                              final BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or greater");
        }
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.goodsReceiptItemId = goodsReceiptItemId;
        this.unitCost = unitCost;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductVariantId() {
        return productVariantId;
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

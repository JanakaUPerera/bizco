package com.bizco.server.purchasing.domain;

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

/** A line on a {@link SupplierReturn} (DatabaseDesign.md &sect;17.9): one product quantity sent
 *  back against a specific {@code goods_receipt_item_id}. */
@Entity
@Table(name = "supplier_return_items")
public class SupplierReturnItem {

    @Id
    @GeneratedValue
    @Column(name = "supplier_return_item_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_id")
    private SupplierReturn supplierReturn;
    @Column(name = "goods_receipt_item_id", nullable = false)
    private UUID goodsReceiptItemId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    /** Phase 6 Week 18 (DatabaseDesign.md §56.3 Step 4): sourced from the originating
     *  {@code GoodsReceiptItem} at return time — drives the return's stock deduction. */
    @Column(name = "product_variant_id")
    private UUID productVariantId;
    @Column(name = "quantity_returned", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityReturned;
    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    protected SupplierReturnItem() {
    }

    public SupplierReturnItem(final UUID goodsReceiptItemId, final UUID productId, final UUID productVariantId,
                              final BigDecimal quantityReturned, final BigDecimal unitCost) {
        if (goodsReceiptItemId == null || productId == null) {
            throw new IllegalArgumentException("goodsReceiptItemId and productId are required");
        }
        if (quantityReturned == null || quantityReturned.signum() <= 0) {
            throw new IllegalArgumentException("Quantity returned must be greater than zero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or greater");
        }
        this.goodsReceiptItemId = goodsReceiptItemId;
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.quantityReturned = quantityReturned;
        this.unitCost = unitCost;
        this.lineTotal = quantityReturned.multiply(unitCost);
    }

    void assignTo(final SupplierReturn supplierReturn) {
        this.supplierReturn = supplierReturn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoodsReceiptItemId() {
        return goodsReceiptItemId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductVariantId() {
        return productVariantId;
    }

    public BigDecimal getQuantityReturned() {
        return quantityReturned;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

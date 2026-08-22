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

/** A line on a {@link PurchaseOrder}. {@code productId} is a plain foreign key, not a JPA
 *  association - the same rule {@code InvoiceLine} and {@code JobPart} already follow. */
@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue
    @Column(name = "purchase_order_item_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    /** Phase 6 Week 18 (DatabaseDesign.md §56.3 Step 4): recorded for consistency across the
     *  purchasing pipeline; POs don't post stock, so nothing locks/aggregates on this field yet. */
    @Column(name = "product_variant_id")
    private UUID productVariantId;
    @Column(name = "quantity_ordered", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityOrdered;
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(final UUID productId, final UUID productVariantId, final BigDecimal quantityOrdered,
                             final BigDecimal unitPrice) {
        if (quantityOrdered == null || quantityOrdered.signum() <= 0) {
            throw new IllegalArgumentException("Quantity ordered must be greater than zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must be zero or greater");
        }
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.quantityOrdered = quantityOrdered;
        this.unitPrice = unitPrice;
        this.lineTotal = quantityOrdered.multiply(unitPrice);
    }

    void assignTo(final PurchaseOrder purchaseOrder, final int lineNumber) {
        this.purchaseOrder = purchaseOrder;
        this.lineNumber = lineNumber;
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductVariantId() {
        return productVariantId;
    }

    public BigDecimal getQuantityOrdered() {
        return quantityOrdered;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

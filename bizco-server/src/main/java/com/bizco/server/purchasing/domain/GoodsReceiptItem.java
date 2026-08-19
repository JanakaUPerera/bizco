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

/** A line on a {@link GoodsReceipt} (DatabaseDesign.md &sect;17.6). {@code productId}/
 *  {@code purchaseOrderItemId} are plain foreign keys - the latter optional, since a receipt line
 *  need not trace back to a PO line at all (an ad-hoc purchase). */
@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue
    @Column(name = "goods_receipt_item_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt goodsReceipt;
    @Column(name = "purchase_order_item_id")
    private UUID purchaseOrderItemId;
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "quantity_received", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityReceived;
    @Column(name = "quantity_damaged", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityDamaged = BigDecimal.ZERO;
    @Column(name = "quantity_rejected", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityRejected = BigDecimal.ZERO;
    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;
    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    protected GoodsReceiptItem() {
    }

    public GoodsReceiptItem(final UUID purchaseOrderItemId, final UUID productId, final BigDecimal quantityReceived,
                            final BigDecimal quantityDamaged, final BigDecimal quantityRejected,
                            final BigDecimal unitCost) {
        if (quantityReceived == null || quantityReceived.signum() <= 0) {
            throw new IllegalArgumentException("Quantity received must be greater than zero");
        }
        final BigDecimal damaged = quantityDamaged == null ? BigDecimal.ZERO : quantityDamaged;
        final BigDecimal rejected = quantityRejected == null ? BigDecimal.ZERO : quantityRejected;
        if (damaged.signum() < 0 || rejected.signum() < 0) {
            throw new IllegalArgumentException("Damaged/rejected quantity must be zero or greater");
        }
        if (damaged.add(rejected).compareTo(quantityReceived) > 0) {
            throw new IllegalArgumentException("Damaged plus rejected quantity cannot exceed quantity received");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or greater");
        }
        this.purchaseOrderItemId = purchaseOrderItemId;
        this.productId = productId;
        this.quantityReceived = quantityReceived;
        this.quantityDamaged = damaged;
        this.quantityRejected = rejected;
        this.unitCost = unitCost;
        this.totalCost = quantityReceived.multiply(unitCost);
    }

    void assignTo(final GoodsReceipt goodsReceipt, final int lineNumber) {
        this.goodsReceipt = goodsReceipt;
        this.lineNumber = lineNumber;
    }

    /** The only quantity that ever posts a stock movement - DatabaseDesign.md &sect;17.6. */
    public BigDecimal usableQuantity() {
        return quantityReceived.subtract(quantityDamaged).subtract(quantityRejected);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPurchaseOrderItemId() {
        return purchaseOrderItemId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public BigDecimal getQuantityDamaged() {
        return quantityDamaged;
    }

    public BigDecimal getQuantityRejected() {
        return quantityRejected;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }
}

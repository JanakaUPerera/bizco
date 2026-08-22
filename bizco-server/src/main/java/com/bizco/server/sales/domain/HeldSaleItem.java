package com.bizco.server.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A cart line on a {@link HeldSale} (DatabaseDesign.md &sect;12.2). Unlike {@link InvoiceLine},
 * this only ever represents a PRODUCT - held sales are a pre-checkout cart snapshot, not a tax
 * document, so there is no SKU/UOM/tax-category snapshot here; those are captured fresh when the
 * held sale is converted into a real DRAFT invoice line.
 */
@Entity
@Table(name = "held_sale_items")
public class HeldSaleItem {

    @Id
    @GeneratedValue
    @Column(name = "held_sale_item_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "held_sale_id")
    private HeldSale heldSale;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    /** Phase 6 Week 18 (DatabaseDesign.md §56.3 Step 4): drives reservation locking/availability
     *  ({@code v_reserved_stock}); {@code productId} stays populated for display. */
    @Column(name = "product_variant_id")
    private UUID productVariantId;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.NONE;
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    protected HeldSaleItem() {
    }

    public HeldSaleItem(final UUID productId, final UUID productVariantId, final BigDecimal quantity,
                        final BigDecimal unitPriceSnapshot, final DiscountType discountType,
                        final BigDecimal discountValue) {
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.discountType = discountType == null ? DiscountType.NONE : discountType;
        this.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    }

    void assignTo(final HeldSale heldSale) {
        this.heldSale = heldSale;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }
}

package com.bizco.server.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A supplier's catalog entry for one product (DatabaseDesign.md &sect;17.2, SRS.md &sect;6.9.4).
 * {@code supplierId}/{@code productId} are plain foreign keys, matching the boundary rule every
 * other cross-module reference in this codebase already follows.
 */
@Entity
@Table(name = "supplier_products")
public class SupplierProduct {

    @Id
    @GeneratedValue
    @Column(name = "supplier_product_id")
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_sku", length = 50)
    private String supplierSku;

    @Column(name = "purchase_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "min_order_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal minOrderQty = BigDecimal.ONE;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "last_purchase_price", precision = 15, scale = 2)
    private BigDecimal lastPurchasePrice;

    @Column(name = "is_preferred", nullable = false)
    private boolean preferred;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected SupplierProduct() {
    }

    public SupplierProduct(final UUID supplierId, final UUID productId, final String supplierSku,
                           final BigDecimal purchasePrice, final BigDecimal minOrderQty, final Integer leadTimeDays,
                           final boolean preferred) {
        update(supplierSku, purchasePrice, minOrderQty, leadTimeDays, preferred);
        this.supplierId = supplierId;
        this.productId = productId;
    }

    public void update(final String supplierSku, final BigDecimal purchasePrice, final BigDecimal minOrderQty,
                       final Integer leadTimeDays, final boolean preferred) {
        if (purchasePrice == null || purchasePrice.signum() < 0) {
            throw new IllegalArgumentException("Purchase price must be zero or greater");
        }
        if (minOrderQty == null || minOrderQty.signum() <= 0) {
            throw new IllegalArgumentException("Minimum order quantity must be greater than zero");
        }
        if (leadTimeDays != null && leadTimeDays < 0) {
            throw new IllegalArgumentException("Lead time days must be zero or greater");
        }
        this.supplierSku = blankToNull(supplierSku);
        this.purchasePrice = purchasePrice;
        this.minOrderQty = minOrderQty;
        this.leadTimeDays = leadTimeDays;
        this.preferred = preferred;
        this.updatedAt = Instant.now();
    }

    /** Called by goods-receipt posting (Week 14) - the "posting service writes the derived field"
     *  pattern {@code products.cost_price} already follows. */
    public void recordPurchase(final BigDecimal unitCost) {
        this.lastPurchasePrice = unitCost;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public BigDecimal getMinOrderQty() {
        return minOrderQty;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public BigDecimal getLastPurchasePrice() {
        return lastPurchasePrice;
    }

    public boolean isPreferred() {
        return preferred;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

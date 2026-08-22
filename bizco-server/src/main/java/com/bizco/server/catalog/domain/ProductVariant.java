package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Phase 6 Week 17 (DatabaseDesign.md §56.1/§56.2): every {@link Product} gets exactly one
 *  variant, even one with no real variation (its "default variant" -- {@link #isDefault()}) --
 *  there is no special-casing between simple and varianted products anywhere downstream. This
 *  entity mirrors {@code Product}'s own sku/barcode/pricing/reorder shape because those columns
 *  are the ones moving off {@code products} and onto {@code product_variants}; {@code products}
 *  keeps them readable until the Week 18 cutover (DatabaseDesign.md §56.3 Step 6) drops them. */
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue
    @Column(name = "product_variant_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 20)
    private String sku;

    @Column(length = 50)
    private String barcode;

    @Column(name = "variant_label", length = 200)
    private String variantLabel;

    @Column(name = "cost_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "selling_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "wholesale_price", precision = 15, scale = 2)
    private BigDecimal wholesalePrice;

    @Column(name = "reorder_point", nullable = false, precision = 15, scale = 3)
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "is_default", nullable = false)
    private boolean defaultVariant;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected ProductVariant() {
    }

    public ProductVariant(final Product product, final String sku, final String barcode, final String variantLabel,
                          final BigDecimal costPrice, final BigDecimal sellingPrice, final BigDecimal wholesalePrice,
                          final BigDecimal reorderPoint, final String imagePath, final boolean defaultVariant) {
        this.product = product;
        this.defaultVariant = defaultVariant;
        apply(sku, barcode, variantLabel, costPrice, sellingPrice, wholesalePrice, reorderPoint, true, imagePath);
    }

    public void update(final String sku, final String barcode, final String variantLabel, final BigDecimal costPrice,
                       final BigDecimal sellingPrice, final BigDecimal wholesalePrice, final BigDecimal reorderPoint,
                       final boolean active, final String imagePath) {
        apply(sku, barcode, variantLabel, costPrice, sellingPrice, wholesalePrice, reorderPoint, active, imagePath);
    }

    /** Phase 6 Week 18: goods-receipt posting now writes the variant-level cost, mirroring
     *  {@code Product.recordPurchaseCost}'s "posting service writes the derived field" pattern
     *  exactly — the full price-history audit trail still lives in {@code product_cost_history},
     *  not on this field. */
    public void recordPurchaseCost(final BigDecimal unitCost) {
        this.costPrice = unitCost;
        this.updatedAt = Instant.now();
    }

    private void apply(final String sku, final String barcode, final String variantLabel,
                       final BigDecimal costPrice, final BigDecimal sellingPrice, final BigDecimal wholesalePrice,
                       final BigDecimal reorderPoint, final boolean active, final String imagePath) {
        this.sku = sku.trim();
        this.barcode = blankToNull(barcode);
        this.variantLabel = blankToNull(variantLabel);
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.wholesalePrice = wholesalePrice;
        this.reorderPoint = reorderPoint;
        this.active = active;
        this.imagePath = blankToNull(imagePath);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getVariantLabel() { return variantLabel; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public BigDecimal getReorderPoint() { return reorderPoint; }
    public boolean isActive() { return active; }
    public String getImagePath() { return imagePath; }
    public boolean isDefault() { return defaultVariant; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

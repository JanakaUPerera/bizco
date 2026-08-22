package com.bizco.server.catalog.domain;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue
    @Column(name = "product_id")
    private UUID id;

    @Column(nullable = false, length = 20)
    private String sku;

    @Column(length = 50)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id")
    private Uom uom;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.INVENTORY;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category", nullable = false, length = 20)
    private TaxCategory taxCategory = TaxCategory.STANDARD;

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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Product() {
    }

    public Product(final String sku, final String barcode, final String name, final String description,
                   final ProductCategory category, final Brand brand, final Uom uom, final ProductType productType,
                   final TaxCategory taxCategory, final BigDecimal costPrice, final BigDecimal sellingPrice,
                   final BigDecimal wholesalePrice, final BigDecimal reorderPoint, final String imagePath) {
        apply(sku, barcode, name, description, category, brand, uom, productType, taxCategory, costPrice,
                sellingPrice, wholesalePrice, reorderPoint, true, imagePath);
    }

    public void update(final String sku, final String barcode, final String name, final String description,
                       final ProductCategory category, final Brand brand, final Uom uom, final ProductType productType,
                       final TaxCategory taxCategory, final BigDecimal costPrice, final BigDecimal sellingPrice,
                       final BigDecimal wholesalePrice, final BigDecimal reorderPoint, final boolean active,
                       final String imagePath) {
        apply(sku, barcode, name, description, category, brand, uom, productType, taxCategory, costPrice,
                sellingPrice, wholesalePrice, reorderPoint, active, imagePath);
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /** Called by goods-receipt posting (DevelopmentPlan.md Week 14): the latest purchase cost
     *  becomes the product's default cost price, the same "posting service writes the derived
     *  field" pattern {@code SupplierProduct.recordPurchase} also follows. A full price-history
     *  audit trail lives in {@code product_cost_history}, not on this field. */
    public void recordPurchaseCost(final BigDecimal unitCost) {
        this.costPrice = unitCost;
        this.updatedAt = Instant.now();
    }

    private void apply(final String sku, final String barcode, final String name, final String description,
                       final ProductCategory category, final Brand brand, final Uom uom, final ProductType productType,
                       final TaxCategory taxCategory, final BigDecimal costPrice, final BigDecimal sellingPrice,
                       final BigDecimal wholesalePrice, final BigDecimal reorderPoint, final boolean active,
                       final String imagePath) {
        this.sku = sku.trim();
        this.barcode = blankToNull(barcode);
        this.name = name.trim();
        this.description = blankToNull(description);
        this.category = category;
        this.brand = brand;
        this.uom = uom;
        this.productType = productType;
        this.taxCategory = taxCategory;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.wholesalePrice = wholesalePrice;
        this.reorderPoint = reorderPoint;
        this.active = active;
        this.imagePath = blankToNull(imagePath);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProductCategory getCategory() { return category; }
    public Brand getBrand() { return brand; }
    public Uom getUom() { return uom; }
    public ProductType getProductType() { return productType; }
    public TaxCategory getTaxCategory() { return taxCategory; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public BigDecimal getReorderPoint() { return reorderPoint; }
    public boolean isActive() { return active; }
    public String getImagePath() { return imagePath; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

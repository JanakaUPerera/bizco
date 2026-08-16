package com.bizco.server.sales.domain;

import com.bizco.server.catalog.domain.TaxCategory;
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
import java.time.Instant;
import java.util.UUID;

/**
 * A line on an {@link Invoice}. {@code productId}/{@code serviceId} are plain foreign keys, not
 * JPA associations to Catalog entities: lines snapshot the SKU/description/UOM/price/tax category
 * they were created with (AR-012, DomainModel.md &sect;9.3) precisely so they never need to
 * re-read the live product/service after the fact.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue
    @Column(name = "invoice_line_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private LineType lineType;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "sku_snapshot", length = 50)
    private String skuSnapshot;
    @Column(name = "description_snapshot", nullable = false, length = 250)
    private String descriptionSnapshot;
    @Column(name = "uom_snapshot", length = 20)
    private String uomSnapshot;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.NONE;
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category_snapshot", nullable = false, length = 20)
    private TaxCategory taxCategorySnapshot;
    @Column(name = "vat_rate_snapshot", nullable = false, precision = 7, scale = 4)
    private BigDecimal vatRateSnapshot = BigDecimal.ZERO;
    @Column(name = "taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(name = "line_total_incl_vat", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotalInclVat;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected InvoiceLine() {
    }

    public InvoiceLine(final LineType lineType, final UUID productId, final UUID serviceId,
                       final String skuSnapshot, final String descriptionSnapshot, final String uomSnapshot,
                       final BigDecimal quantity, final BigDecimal unitPrice, final TaxCategory taxCategorySnapshot,
                       final BigDecimal vatRateSnapshot) {
        this.lineType = lineType;
        this.productId = productId;
        this.serviceId = serviceId;
        this.skuSnapshot = skuSnapshot;
        this.descriptionSnapshot = descriptionSnapshot;
        this.uomSnapshot = uomSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.taxCategorySnapshot = taxCategorySnapshot;
        this.vatRateSnapshot = vatRateSnapshot;
    }

    void assignTo(final Invoice invoice, final int lineNumber) {
        this.invoice = invoice;
        this.lineNumber = lineNumber;
    }

    public void applyDiscount(final DiscountType discountType, final BigDecimal discountValue) {
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public void applyCalculation(final BigDecimal discountAmount, final BigDecimal taxableAmount,
                                 final BigDecimal vatAmount, final BigDecimal lineTotalInclVat) {
        this.discountAmount = discountAmount;
        this.taxableAmount = taxableAmount;
        this.vatAmount = vatAmount;
        this.lineTotalInclVat = lineTotalInclVat;
    }

    public void changeQuantity(final BigDecimal quantity) {
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public LineType getLineType() {
        return lineType;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public String getDescriptionSnapshot() {
        return descriptionSnapshot;
    }

    public String getUomSnapshot() {
        return uomSnapshot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public TaxCategory getTaxCategorySnapshot() {
        return taxCategorySnapshot;
    }

    public BigDecimal getVatRateSnapshot() {
        return vatRateSnapshot;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getLineTotalInclVat() {
        return lineTotalInclVat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

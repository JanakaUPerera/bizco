package com.bizco.server.sales.domain;

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

/** A returned quantity against one original {@link InvoiceLine} (DatabaseDesign.md &sect;14.2). */
@Entity
@Table(name = "credit_note_lines")
public class CreditNoteLine {

    @Id
    @GeneratedValue
    @Column(name = "credit_note_line_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id")
    private CreditNote creditNote;
    @Column(name = "original_invoice_line_id", nullable = false)
    private UUID originalInvoiceLineId;
    @Column(name = "quantity_returned", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityReturned;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;
    @Column(name = "taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount;
    @Column(name = "vat_rate_snapshot", nullable = false, precision = 7, scale = 4)
    private BigDecimal vatRateSnapshot;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;
    @Column(nullable = false)
    private boolean restock = true;

    protected CreditNoteLine() {
    }

    public CreditNoteLine(final UUID originalInvoiceLineId, final BigDecimal quantityReturned,
                          final BigDecimal unitPriceSnapshot, final BigDecimal taxableAmount,
                          final BigDecimal vatRateSnapshot, final BigDecimal vatAmount, final BigDecimal lineTotal,
                          final boolean restock) {
        this.originalInvoiceLineId = originalInvoiceLineId;
        this.quantityReturned = quantityReturned;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.taxableAmount = taxableAmount;
        this.vatRateSnapshot = vatRateSnapshot;
        this.vatAmount = vatAmount;
        this.lineTotal = lineTotal;
        this.restock = restock;
    }

    void assignTo(final CreditNote creditNote) {
        this.creditNote = creditNote;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginalInvoiceLineId() {
        return originalInvoiceLineId;
    }

    public BigDecimal getQuantityReturned() {
        return quantityReturned;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getVatRateSnapshot() {
        return vatRateSnapshot;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public boolean isRestock() {
        return restock;
    }
}

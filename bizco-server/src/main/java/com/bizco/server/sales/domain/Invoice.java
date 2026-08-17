package com.bizco.server.sales.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sales invoice aggregate root (DomainModel.md &sect;9.2). {@code customerId}/{@code cashierId}
 * are plain foreign keys, not JPA associations to Customer/Identity entities - Sales does not
 * take a hard dependency on those modules' JPA graphs, and the invoice records its own
 * business/customer name/address/TIN snapshots at posting time rather than re-reading live data.
 *
 * <p>Only DRAFT invoices are mutable (StateMachines.md &sect;4, &sect;9.4 "DRAFT: editable ...
 * no VAT/reporting effect"). Every mutating method here throws if the invoice is not DRAFT,
 * enforcing SALE-DRAFT-004 ("Posted invoice cannot be edited") at the domain layer rather than
 * relying on callers to remember to check.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue
    @Column(name = "invoice_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;
    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    private InvoiceType invoiceType = InvoiceType.SALES;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;
    @Column(name = "business_name_snapshot", length = 200)
    private String businessNameSnapshot;
    @Column(name = "business_address_snapshot", length = 500)
    private String businessAddressSnapshot;
    @Column(name = "business_tin_snapshot", length = 30)
    private String businessTinSnapshot;
    @Column(name = "customer_name_snapshot", length = 200)
    private String customerNameSnapshot;
    @Column(name = "customer_address_snapshot", length = 500)
    private String customerAddressSnapshot;
    @Column(name = "customer_tin_snapshot", length = 30)
    private String customerTinSnapshot;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.NONE;
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(name = "vat_rate_snapshot", nullable = false, precision = 7, scale = 4)
    private BigDecimal vatRateSnapshot = BigDecimal.ZERO;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "posted_at")
    private Instant postedAt;
    @Column(name = "voided_at")
    private Instant voidedAt;
    @Column(name = "voided_by")
    private UUID voidedBy;
    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;
    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
    @OrderBy("lineNumber asc")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(final LocalDate invoiceDate, final LocalDate dueDate, final InvoiceType invoiceType,
                   final UUID customerId, final UUID cashierId, final String notes) {
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.invoiceType = invoiceType == null ? InvoiceType.SALES : invoiceType;
        this.customerId = customerId;
        this.cashierId = cashierId;
        this.notes = notes;
    }

    public void updateHeader(final LocalDate invoiceDate, final LocalDate dueDate, final InvoiceType invoiceType,
                             final UUID customerId, final DiscountType discountType, final BigDecimal discountValue,
                             final String notes) {
        assertDraft();
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.invoiceType = invoiceType == null ? this.invoiceType : invoiceType;
        this.customerId = customerId;
        this.discountType = discountType == null ? DiscountType.NONE : discountType;
        this.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
        this.notes = notes;
    }

    public InvoiceLine addLine(final InvoiceLine line) {
        assertDraft();
        line.assignTo(this, nextLineNumber());
        lines.add(line);
        return line;
    }

    public void removeLine(final UUID lineId) {
        assertDraft();
        final boolean removed = lines.removeIf(line -> line.getId() != null && line.getId().equals(lineId));
        if (!removed) {
            throw new IllegalArgumentException("Invoice line was not found on this invoice");
        }
        renumberLines();
    }

    public InvoiceLine line(final UUID lineId) {
        return lines.stream().filter(line -> line.getId() != null && line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invoice line was not found on this invoice"));
    }

    public void assertDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Invoice is not DRAFT and cannot be modified");
        }
    }

    /**
     * DRAFT -&gt; POSTED (StateMachines.md &sect;4.4): freezes the header snapshots, assigns the
     * official number, and makes the invoice immutable. Caller is responsible for everything the
     * transition also implies outside this aggregate - stock movements, payment/receivable rows,
     * cashbook entries, and audit - none of which this method touches.
     */
    public void post(final UUID requestId, final String invoiceNumber, final String businessNameSnapshot,
                     final String businessAddressSnapshot, final String businessTinSnapshot,
                     final String customerNameSnapshot, final String customerAddressSnapshot,
                     final String customerTinSnapshot, final Instant postedAt) {
        assertDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("Invoice must have at least one line to post");
        }
        this.requestId = requestId;
        this.invoiceNumber = invoiceNumber;
        this.businessNameSnapshot = businessNameSnapshot;
        this.businessAddressSnapshot = businessAddressSnapshot;
        this.businessTinSnapshot = businessTinSnapshot;
        this.customerNameSnapshot = customerNameSnapshot;
        this.customerAddressSnapshot = customerAddressSnapshot;
        this.customerTinSnapshot = customerTinSnapshot;
        this.postedAt = postedAt;
        this.status = InvoiceStatus.POSTED;
    }

    /**
     * POSTED -&gt; VOIDED (StateMachines.md &sect;4.5): historical totals/snapshots are left exactly
     * as posted - voiding never edits them, only marks the invoice as no longer economically
     * active. {@code v_customer_receivables} already excludes VOIDED invoices, so this alone
     * removes the invoice from the customer's outstanding balance; the caller is responsible for
     * anything beyond that (this method does not reverse existing payments/cashbook entries - see
     * {@code InvoiceVoidService}'s Javadoc for the documented scope decision).
     */
    public void voidInvoice(final UUID voidedBy, final String voidReason, final Instant voidedAt) {
        if (status != InvoiceStatus.POSTED) {
            throw new IllegalStateException("Only a POSTED invoice can be voided");
        }
        if (voidReason == null || voidReason.isBlank()) {
            throw new IllegalArgumentException("A void reason is required");
        }
        this.status = InvoiceStatus.VOIDED;
        this.voidedBy = voidedBy;
        this.voidReason = voidReason;
        this.voidedAt = voidedAt;
    }

    /** Applies the freshly-computed {@link com.bizco.server.sales.domain.InvoicePricingCalculator} totals. */
    public void applyCalculatedTotals(final BigDecimal subtotal, final BigDecimal discountAmount,
                                      final BigDecimal taxableAmount, final BigDecimal vatAmount,
                                      final BigDecimal totalAmount) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.taxableAmount = taxableAmount;
        this.vatAmount = vatAmount;
        this.totalAmount = totalAmount;
    }

    private int nextLineNumber() {
        return lines.stream().mapToInt(InvoiceLine::getLineNumber).max().orElse(0) + 1;
    }

    private void renumberLines() {
        int number = 1;
        for (final InvoiceLine line : lines) {
            line.assignTo(this, number++);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceType getInvoiceType() {
        return invoiceType;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getBusinessNameSnapshot() {
        return businessNameSnapshot;
    }

    public String getBusinessAddressSnapshot() {
        return businessAddressSnapshot;
    }

    public String getBusinessTinSnapshot() {
        return businessTinSnapshot;
    }

    public String getCustomerNameSnapshot() {
        return customerNameSnapshot;
    }

    public String getCustomerAddressSnapshot() {
        return customerAddressSnapshot;
    }

    public String getCustomerTinSnapshot() {
        return customerTinSnapshot;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCashierId() {
        return cashierId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
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

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getVatRateSnapshot() {
        return vatRateSnapshot;
    }

    public void setVatRateSnapshot(final BigDecimal vatRateSnapshot) {
        this.vatRateSnapshot = vatRateSnapshot;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getVoidedBy() {
        return voidedBy;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public long getVersion() {
        return version;
    }

    public List<InvoiceLine> getLines() {
        return List.copyOf(lines);
    }
}

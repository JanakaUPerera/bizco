package com.bizco.server.purchasing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Goods Receipt aggregate root (DatabaseDesign.md &sect;17.5/17.6, DevelopmentPlan.md Week 14) -
 * the document that actually increases stock, unlike the {@link PurchaseOrder} it may optionally
 * reference. {@code purchaseOrderId} is nullable: a small/ad-hoc purchase can be received directly
 * with no formal PO, matching how Bizco's SME scenarios (SC-01/SC-03) actually buy day to day.
 *
 * <p>Only DRAFT is mutable. {@code receipt_number} is allocated once, at {@link #post} - the same
 * single-allocation-point pattern {@code Invoice.post}/{@code PurchaseOrder.approve}/{@code .send}
 * already use.
 */
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    @Id
    @GeneratedValue
    @Column(name = "goods_receipt_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "receipt_number", length = 30)
    private String receiptNumber;
    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "supplier_reference", length = 100)
    private String supplierReference;
    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsReceiptStatus status = GoodsReceiptStatus.DRAFT;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "posted_at")
    private Instant postedAt;
    @Version
    private long version;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber asc")
    private List<GoodsReceiptItem> items = new ArrayList<>();

    protected GoodsReceipt() {
    }

    public GoodsReceipt(final UUID purchaseOrderId, final UUID supplierId, final String supplierReference,
                        final LocalDate receiptDate, final String notes, final UUID createdBy) {
        this.purchaseOrderId = purchaseOrderId;
        this.supplierId = supplierId;
        this.supplierReference = blankToNull(supplierReference);
        this.receiptDate = receiptDate;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public GoodsReceiptItem addItem(final GoodsReceiptItem item) {
        assertDraft();
        item.assignTo(this, nextLineNumber());
        items.add(item);
        return item;
    }

    public void removeItem(final UUID itemId) {
        assertDraft();
        final boolean removed = items.removeIf(item -> item.getId() != null && item.getId().equals(itemId));
        if (!removed) {
            throw new IllegalArgumentException("Goods receipt item was not found on this receipt");
        }
        renumberItems();
    }

    public void assertDraft() {
        if (status != GoodsReceiptStatus.DRAFT) {
            throw new IllegalStateException("Goods receipt is not DRAFT and cannot be modified");
        }
    }

    public void applyCalculatedTotal(final BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    /** DRAFT -&gt; POSTED. Caller is responsible for everything the transition also implies outside
     *  this aggregate - stock movements, cost history, payable effect, and the linked PO's status -
     *  none of which this method touches (mirrors {@code Invoice.post}'s Javadoc). */
    public void post(final UUID requestId, final String receiptNumber, final Instant postedAt) {
        assertDraft();
        if (items.isEmpty()) {
            throw new IllegalStateException("Goods receipt must have at least one item to post");
        }
        this.requestId = requestId;
        this.receiptNumber = receiptNumber;
        this.postedAt = postedAt;
        this.status = GoodsReceiptStatus.POSTED;
    }

    private int nextLineNumber() {
        return items.stream().mapToInt(GoodsReceiptItem::getLineNumber).max().orElse(0) + 1;
    }

    private void renumberItems() {
        int number = 1;
        for (final GoodsReceiptItem item : items) {
            item.assignTo(this, number++);
        }
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public GoodsReceiptStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<GoodsReceiptItem> getItems() {
        return List.copyOf(items);
    }
}

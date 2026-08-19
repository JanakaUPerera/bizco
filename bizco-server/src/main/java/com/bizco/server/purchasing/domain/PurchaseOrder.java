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
 * Purchase order aggregate root (DatabaseDesign.md &sect;17.3/17.4, DevelopmentPlan.md Week 13).
 * {@code supplierId}/{@code approvedBy}/{@code createdBy} are plain foreign keys, the same
 * cross-module boundary rule {@link com.bizco.server.sales.domain.Invoice} already follows.
 *
 * <p>Only DRAFT is mutable (add/remove items). {@code po_number} is allocated once, at whichever
 * transition first takes the order out of DRAFT ({@link #approve} or a direct {@link #send} for a
 * below-threshold order) - the same single-allocation-point pattern {@code Invoice.post} uses for
 * {@code invoice_number}.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue
    @Column(name = "purchase_order_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "po_number", length = 30)
    private String poNumber;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "po_date", nullable = false)
    private LocalDate poDate;
    @Column(name = "expected_date")
    private LocalDate expectedDate;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber asc")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(final UUID supplierId, final LocalDate poDate, final LocalDate expectedDate,
                         final LocalDate validUntil, final String notes, final UUID createdBy) {
        this.supplierId = supplierId;
        this.poDate = poDate;
        this.expectedDate = expectedDate;
        this.validUntil = validUntil;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void updateHeader(final LocalDate poDate, final LocalDate expectedDate, final LocalDate validUntil,
                             final String notes) {
        assertDraft();
        this.poDate = poDate;
        this.expectedDate = expectedDate;
        this.validUntil = validUntil;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public PurchaseOrderItem addItem(final PurchaseOrderItem item) {
        assertDraft();
        item.assignTo(this, nextLineNumber());
        items.add(item);
        this.updatedAt = Instant.now();
        return item;
    }

    public void removeItem(final UUID itemId) {
        assertDraft();
        final boolean removed = items.removeIf(item -> item.getId() != null && item.getId().equals(itemId));
        if (!removed) {
            throw new IllegalArgumentException("Purchase order item was not found on this order");
        }
        renumberItems();
        this.updatedAt = Instant.now();
    }

    public PurchaseOrderItem item(final UUID itemId) {
        return items.stream().filter(item -> item.getId() != null && item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Purchase order item was not found on this order"));
    }

    public void assertDraft() {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Purchase order is not DRAFT and cannot be modified");
        }
    }

    public void applyCalculatedTotals(final BigDecimal subtotal, final BigDecimal totalAmount) {
        this.subtotal = subtotal;
        this.totalAmount = totalAmount;
    }

    /** DRAFT -&gt; APPROVED. Required before {@link #send} for a PO at or above the value threshold
     *  (checked by the application service, not this method - the threshold is business
     *  configuration, not a domain invariant). */
    public void approve(final UUID requestId, final String poNumber, final UUID approvedBy, final Instant approvedAt) {
        assertDraft();
        requireItems();
        this.requestId = requestId;
        this.poNumber = poNumber;
        this.status = PurchaseOrderStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.updatedAt = approvedAt;
    }

    /** DRAFT -&gt; SENT (below-threshold order, no approval needed) or APPROVED -&gt; SENT. */
    public void send(final UUID requestId, final String poNumber, final Instant sentAt) {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.APPROVED) {
            throw new IllegalStateException("Purchase order must be DRAFT or APPROVED to send");
        }
        requireItems();
        if (this.poNumber == null) {
            this.requestId = requestId;
            this.poNumber = poNumber;
        }
        this.status = PurchaseOrderStatus.SENT;
        this.updatedAt = sentAt;
    }

    /** Any non-terminal, not-yet-received state -&gt; CANCELLED. */
    public void cancel(final String reason, final Instant cancelledAt) {
        if (status == PurchaseOrderStatus.CLOSED || status == PurchaseOrderStatus.CANCELLED
                || status == PurchaseOrderStatus.PARTIALLY_RECEIVED || status == PurchaseOrderStatus.FULLY_RECEIVED) {
            throw new IllegalStateException("Purchase order is " + status + " and cannot be cancelled");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }
        this.status = PurchaseOrderStatus.CANCELLED;
        this.notes = (this.notes == null ? "" : this.notes + " | ") + "Cancelled: " + reason;
        this.updatedAt = cancelledAt;
    }

    /**
     * Writes off the still-outstanding balance of a PO that has already received some, but will
     * never receive the rest (supplier can't fulfil, item discontinued, etc.) - DevelopmentPlan.md
     * Week 14 task 14.4 "remaining balance cancel". Distinct from {@link #cancel}: this PO already
     * has real, posted receipt/cost/payable effects, so it closes rather than cancels - "cancelled"
     * would misleadingly suggest nothing happened.
     */
    public void closeRemainingBalance(final String reason, final Instant at) {
        if (status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Only a PARTIALLY_RECEIVED purchase order has a remaining balance to close");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to close the remaining balance");
        }
        this.status = PurchaseOrderStatus.CLOSED;
        this.notes = (this.notes == null ? "" : this.notes + " | ") + "Remaining balance closed: " + reason;
        this.updatedAt = at;
    }

    /**
     * Called by {@code GoodsReceiptService} after a posted receipt updates this PO's cumulative
     * received quantities (DevelopmentPlan.md Week 14 task 14.4). {@code fullyReceived} auto-closes
     * the order - MVP.md &sect;8.1 "a PO auto-closes once total received qty across all its receipts
     * reaches the ordered qty" - rather than leaving it sitting in {@link PurchaseOrderStatus#FULLY_RECEIVED}
     * awaiting a separate manual close; that status stays in the enum for schema completeness and
     * any future manual-review workflow, but this method never stops there.
     */
    public void recordReceiptProgress(final boolean fullyReceived, final Instant at) {
        if (status != PurchaseOrderStatus.SENT && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Purchase order must be SENT or PARTIALLY_RECEIVED to record a receipt");
        }
        this.status = fullyReceived ? PurchaseOrderStatus.CLOSED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        this.updatedAt = at;
    }

    private void requireItems() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Purchase order must have at least one item");
        }
    }

    private int nextLineNumber() {
        return items.stream().mapToInt(PurchaseOrderItem::getLineNumber).max().orElse(0) + 1;
    }

    private void renumberItems() {
        int number = 1;
        for (final PurchaseOrderItem item : items) {
            item.assignTo(this, number++);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public LocalDate getPoDate() {
        return poDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<PurchaseOrderItem> getItems() {
        return List.copyOf(items);
    }
}

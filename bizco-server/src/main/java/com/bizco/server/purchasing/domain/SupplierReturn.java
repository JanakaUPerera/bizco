package com.bizco.server.purchasing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A return of goods against a single POSTED {@link GoodsReceipt} (DatabaseDesign.md &sect;17.8/
 * 17.9, DevelopmentPlan.md Week 15). One-shot document, like {@code CreditNote}: created and
 * settled - stock deducted, payable reduced - in a single idempotency-wrapped request, so there is
 * no DRAFT/POSTED state machine here (see the migration's header comment for why {@code requestId}
 * is {@code NOT NULL} rather than nullable).
 */
@Entity
@Table(name = "supplier_returns")
public class SupplierReturn {

    @Id
    @GeneratedValue
    @Column(name = "supplier_return_id")
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "return_number", nullable = false, length = 30)
    private String returnNumber;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "goods_receipt_id", nullable = false)
    private UUID goodsReceiptId;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SupplierReturnItem> items = new ArrayList<>();

    protected SupplierReturn() {
    }

    public SupplierReturn(final UUID requestId, final String returnNumber, final UUID supplierId,
                          final UUID goodsReceiptId, final String reason, final UUID createdBy) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A return reason is required");
        }
        this.requestId = requestId;
        this.returnNumber = returnNumber;
        this.supplierId = supplierId;
        this.goodsReceiptId = goodsReceiptId;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public SupplierReturnItem addItem(final SupplierReturnItem item) {
        item.assignTo(this);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
        return item;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public UUID getGoodsReceiptId() {
        return goodsReceiptId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SupplierReturnItem> getItems() {
        return List.copyOf(items);
    }
}

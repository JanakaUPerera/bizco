package com.bizco.server.purchasing.domain;

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

/** How much of a {@link SupplierPayment} was applied against one {@code goods_receipt}
 *  (DatabaseDesign.md &sect;17.11). A single payment can span several goods receipts, mirroring
 *  {@code CustomerPaymentAllocation} on the sales side. */
@Entity
@Table(name = "supplier_payment_allocations")
public class SupplierPaymentAllocation {

    @Id
    @GeneratedValue
    @Column(name = "supplier_payment_allocation_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_payment_id")
    private SupplierPayment supplierPayment;
    @Column(name = "goods_receipt_id", nullable = false)
    private UUID goodsReceiptId;
    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    protected SupplierPaymentAllocation() {
    }

    public SupplierPaymentAllocation(final UUID goodsReceiptId, final BigDecimal allocatedAmount) {
        if (goodsReceiptId == null) {
            throw new IllegalArgumentException("goodsReceiptId is required");
        }
        if (allocatedAmount == null || allocatedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Allocated amount must be greater than zero");
        }
        this.goodsReceiptId = goodsReceiptId;
        this.allocatedAmount = allocatedAmount;
    }

    void assignTo(final SupplierPayment supplierPayment) {
        this.supplierPayment = supplierPayment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoodsReceiptId() {
        return goodsReceiptId;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}

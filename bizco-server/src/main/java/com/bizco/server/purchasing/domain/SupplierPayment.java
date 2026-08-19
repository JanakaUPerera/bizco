package com.bizco.server.purchasing.domain;

import com.bizco.server.finance.domain.PaymentMethod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A payment made to a supplier, optionally allocated across one or more of that supplier's
 * outstanding goods receipts (DatabaseDesign.md &sect;17.10/17.11, DevelopmentPlan.md Week 15).
 * One-shot: recorded and allocated in a single request, mirroring {@code SupplierReturn} and
 * {@code CustomerPayment}. Unlike {@code purchase_orders}/{@code goods_receipts}, there is no
 * formal document number - only the optional {@code referenceNumber} an operator can note (a
 * cheque number, a bank transfer reference), the same shape {@code CustomerPayment} already uses.
 * An unallocated remainder is simply money paid to the supplier ahead of any specific receipt.
 */
@Entity
@Table(name = "supplier_payments")
public class SupplierPayment {

    @Id
    @GeneratedValue
    @Column(name = "supplier_payment_id")
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "payment_date", nullable = false)
    private Instant paymentDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "paid_by", nullable = false)
    private UUID paidBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "supplierPayment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SupplierPaymentAllocation> allocations = new ArrayList<>();

    protected SupplierPayment() {
    }

    public SupplierPayment(final UUID requestId, final UUID supplierId, final Instant paymentDate,
                           final PaymentMethod paymentMethod, final BigDecimal amount, final String referenceNumber,
                           final UUID paidBy, final String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        this.requestId = requestId;
        this.supplierId = supplierId;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.referenceNumber = blankToNull(referenceNumber);
        this.notes = notes;
        this.paidBy = paidBy;
    }

    public void allocateTo(final UUID goodsReceiptId, final BigDecimal allocatedAmount) {
        final SupplierPaymentAllocation allocation = new SupplierPaymentAllocation(goodsReceiptId, allocatedAmount);
        allocation.assignTo(this);
        allocations.add(allocation);
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

    public UUID getSupplierId() {
        return supplierId;
    }

    public Instant getPaymentDate() {
        return paymentDate;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SupplierPaymentAllocation> getAllocations() {
        return List.copyOf(allocations);
    }
}

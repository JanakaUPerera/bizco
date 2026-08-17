package com.bizco.server.finance.domain;

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
 * One customer payment, immutable once created, allocated across one or more invoices
 * (DatabaseDesign.md &sect;13.2-13.3). {@code requestId} is the idempotency binding for the
 * posting command that created it (usually the same invoice-posting request). Walk-in immediate
 * payments have {@code customerId = null} but must still be allocated to the invoice being paid.
 */
@Entity
@Table(name = "customer_payments")
public class CustomerPayment {

    @Id
    @GeneratedValue
    @Column(name = "customer_payment_id")
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "payment_date", nullable = false)
    private Instant paymentDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    @Column(name = "received_by", nullable = false)
    private UUID receivedBy;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerPaymentAllocation> allocations = new ArrayList<>();

    protected CustomerPayment() {
    }

    public CustomerPayment(final UUID requestId, final UUID customerId, final Instant paymentDate,
                           final PaymentMethod paymentMethod, final BigDecimal amount,
                           final String referenceNumber, final UUID receivedBy, final String notes) {
        this.requestId = requestId;
        this.customerId = customerId;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.referenceNumber = referenceNumber;
        this.receivedBy = receivedBy;
        this.notes = notes;
    }

    public CustomerPaymentAllocation allocateTo(final UUID invoiceId, final BigDecimal allocatedAmount) {
        final CustomerPaymentAllocation allocation = new CustomerPaymentAllocation(invoiceId, allocatedAmount);
        allocation.assignTo(this);
        allocations.add(allocation);
        return allocation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getCustomerId() {
        return customerId;
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

    public UUID getReceivedBy() {
        return receivedBy;
    }

    public List<CustomerPaymentAllocation> getAllocations() {
        return List.copyOf(allocations);
    }
}

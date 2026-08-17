package com.bizco.server.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * How much of one {@link CustomerPayment} was applied against one invoice
 * (DatabaseDesign.md &sect;13.3). A payment may be split across several invoices, and the sum of
 * a payment's allocations may be less than its amount — the remainder is customer credit/
 * prepayment (&sect;13.4), not modeled further until receivable views need it.
 */
@Entity
@Table(name = "customer_payment_allocations")
public class CustomerPaymentAllocation {

    @Id
    @GeneratedValue
    @Column(name = "customer_payment_allocation_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_payment_id")
    private CustomerPayment payment;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected CustomerPaymentAllocation() {
    }

    public CustomerPaymentAllocation(final UUID invoiceId, final BigDecimal allocatedAmount) {
        this.invoiceId = invoiceId;
        this.allocatedAmount = allocatedAmount;
    }

    void assignTo(final CustomerPayment payment) {
        this.payment = payment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

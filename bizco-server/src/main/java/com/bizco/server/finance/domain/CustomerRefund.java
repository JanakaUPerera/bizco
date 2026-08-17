package com.bizco.server.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A cash refund settling a {@code CreditNote} (DatabaseDesign.md &sect;14.4). */
@Entity
@Table(name = "customer_refunds")
public class CustomerRefund {

    @Id
    @GeneratedValue
    @Column(name = "customer_refund_id")
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "credit_note_id", nullable = false)
    private UUID creditNoteId;
    @Column(name = "original_customer_payment_id")
    private UUID originalCustomerPaymentId;
    @Column(name = "refund_date", nullable = false)
    private Instant refundDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    @Column(name = "refunded_by", nullable = false)
    private UUID refundedBy;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected CustomerRefund() {
    }

    public CustomerRefund(final UUID requestId, final UUID creditNoteId, final UUID originalCustomerPaymentId,
                          final Instant refundDate, final PaymentMethod paymentMethod, final BigDecimal amount,
                          final String referenceNumber, final UUID refundedBy, final String reason) {
        this.requestId = requestId;
        this.creditNoteId = creditNoteId;
        this.originalCustomerPaymentId = originalCustomerPaymentId;
        this.refundDate = refundDate;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.referenceNumber = referenceNumber;
        this.refundedBy = refundedBy;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getCreditNoteId() {
        return creditNoteId;
    }

    public UUID getOriginalCustomerPaymentId() {
        return originalCustomerPaymentId;
    }

    public Instant getRefundDate() {
        return refundDate;
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

    public UUID getRefundedBy() {
        return refundedBy;
    }

    public String getReason() {
        return reason;
    }
}

package com.bizco.server.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One cash movement (DatabaseDesign.md &sect;21). Immutable once created - a correction is a new
 * entry that references the original via {@code reversedEntryId}, never an edit. A partial unique
 * index on {@code (source_type, reference_id)} (&sect;21.2, enforced in the migration, not here)
 * guarantees a system-generated source (e.g. one {@code CustomerPayment}) can never produce two
 * entries even under a retried request.
 */
@Entity
@Table(name = "cashbook_entries")
public class CashbookEntry {

    @Id
    @GeneratedValue
    @Column(name = "cashbook_entry_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "entry_date", nullable = false)
    private Instant entryDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashDirection direction;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private CashSourceType sourceType;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(length = 100)
    private String category;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(columnDefinition = "TEXT")
    private String reason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "reversed_entry_id")
    private UUID reversedEntryId;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected CashbookEntry() {
    }

    public CashbookEntry(final UUID requestId, final Instant entryDate, final CashDirection direction,
                         final CashSourceType sourceType, final BigDecimal amount, final PaymentMethod paymentMethod,
                         final String category, final UUID referenceId, final String reason, final UUID createdBy) {
        this.requestId = requestId;
        this.entryDate = entryDate;
        this.direction = direction;
        this.sourceType = sourceType;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.category = category;
        this.referenceId = referenceId;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("Cashbook entries are immutable; reverse with a new linked entry");
    }

    public UUID getId() {
        return id;
    }

    public CashDirection getDirection() {
        return direction;
    }

    public CashSourceType getSourceType() {
        return sourceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Instant getEntryDate() {
        return entryDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}

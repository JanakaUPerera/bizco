package com.bizco.server.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a credit note's amount reducing one invoice's balance (DatabaseDesign.md &sect;14.3).
 * {@code creditNoteId}/{@code invoiceId} are plain foreign keys, following Finance's
 * cross-module convention. Immutable, like {@link CashbookEntry} - a ledger entry, not a mutable
 * record.
 */
@Entity
@Table(name = "credit_note_applications")
public class CreditNoteApplication {

    @Id
    @GeneratedValue
    @Column(name = "credit_note_application_id")
    private UUID id;
    @Column(name = "credit_note_id", nullable = false)
    private UUID creditNoteId;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "applied_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal appliedAmount;
    @Column(name = "applied_at")
    private Instant appliedAt = Instant.now();

    protected CreditNoteApplication() {
    }

    public CreditNoteApplication(final UUID creditNoteId, final UUID invoiceId, final BigDecimal appliedAmount) {
        this.creditNoteId = creditNoteId;
        this.invoiceId = invoiceId;
        this.appliedAmount = appliedAmount;
    }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("Credit note applications are immutable");
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreditNoteId() {
        return creditNoteId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}

package com.bizco.server.sales.domain;

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
 * A return/credit against a POSTED {@link Invoice} (DomainModel.md, DatabaseDesign.md &sect;14,
 * StateMachines.md &sect;6). {@code originalInvoiceId}/{@code customerId}/{@code issuedBy} are
 * plain foreign keys, following the same cross-module convention as {@link Invoice}.
 *
 * <p><b>Deliberate MVP simplification:</b> a credit note settles exactly once, in full, at issue
 * time (ApiContracts.md &sect;18.2 folds settlement into the same POST /credit-notes request) -
 * either fully applied to the original invoice's balance or fully refunded. The schema
 * (DatabaseDesign.md &sect;14.3) technically allows partial/multi-invoice application over time,
 * but StateMachines.md &sect;6.4's own precondition ("credit not already applied") reads as
 * exactly this one-shot settlement, and every SALE-CN acceptance scenario is a whole-amount
 * refund/apply. Partial/deferred settlement (the CUSTOMER_CREDIT settlement type) is accepted as
 * a request but simply leaves the note ISSUED and unsettled for now - a later settlement endpoint
 * would be needed to close that gap if it's ever required.
 *
 * <p><b>Known gap</b> (same shape as {@code PostSaleService}'s stock gap): a restockable line is
 * supposed to create a CUSTOMER_RETURN stock movement (SALE-CN-001/003), but the stock ledger
 * (Week 12) does not exist yet, so {@code restock} is recorded but has no physical stock effect.
 */
@Entity
@Table(name = "credit_notes")
public class CreditNote {

    @Id
    @GeneratedValue
    @Column(name = "credit_note_id")
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "credit_note_number", nullable = false, length = 30)
    private String creditNoteNumber;
    @Column(name = "original_invoice_id", nullable = false)
    private UUID originalInvoiceId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditNoteStatus status = CreditNoteStatus.ISSUED;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "issued_at")
    private Instant issuedAt = Instant.now();
    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;
    @Column(name = "applied_at")
    private Instant appliedAt;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CreditNoteLine> lines = new ArrayList<>();

    protected CreditNote() {
    }

    public CreditNote(final UUID requestId, final String creditNoteNumber, final UUID originalInvoiceId,
                      final UUID customerId, final String reason, final UUID issuedBy) {
        this.requestId = requestId;
        this.creditNoteNumber = creditNoteNumber;
        this.originalInvoiceId = originalInvoiceId;
        this.customerId = customerId;
        this.reason = reason;
        this.issuedBy = issuedBy;
    }

    public void addLine(final CreditNoteLine line) {
        line.assignTo(this);
        lines.add(line);
    }

    public void applyTotals(final BigDecimal subtotal, final BigDecimal vatAmount, final BigDecimal totalAmount) {
        this.subtotal = subtotal;
        this.vatAmount = vatAmount;
        this.totalAmount = totalAmount;
    }

    /** StateMachines.md &sect;6.4: settles this note (applied to a balance, or refunded) - see class Javadoc for the one-shot scope decision. */
    public void markSettled(final Instant appliedAt) {
        if (status != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("Credit note is already settled");
        }
        this.status = CreditNoteStatus.APPLIED;
        this.appliedAt = appliedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public UUID getOriginalInvoiceId() {
        return originalInvoiceId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public CreditNoteStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public List<CreditNoteLine> getLines() {
        return List.copyOf(lines);
    }
}

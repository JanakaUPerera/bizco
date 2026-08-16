package com.bizco.server.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence that a manager (or higher) authorized a discount/price-override/below-cost sale
 * (DatabaseDesign.md &sect;11.3). Stores who approved and why - never the approver's password/PIN
 * (ApiContracts.md &sect;14.2 "never persist approver secret"). Bound to one invoice
 * (SALE-DISC-003): {@code PostSaleService} will later verify an approval ID submitted at posting
 * belongs to the invoice being posted before honoring it.
 */
@Entity
@Table(name = "sales_approvals")
public class SalesApproval {

    @Id
    @GeneratedValue
    @Column(name = "sales_approval_id")
    private UUID id;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "invoice_line_id")
    private UUID invoiceLineId;
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 40)
    private SalesApprovalType approvalType;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;
    @Column(columnDefinition = "TEXT")
    private String reason;
    @Column(name = "approved_at")
    private Instant approvedAt = Instant.now();

    protected SalesApproval() {
    }

    public SalesApproval(final UUID invoiceId, final UUID invoiceLineId, final SalesApprovalType approvalType,
                         final UUID requestedBy, final UUID approvedBy, final String reason) {
        this.invoiceId = invoiceId;
        this.invoiceLineId = invoiceLineId;
        this.approvalType = approvalType;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
        this.reason = reason;
    }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("Sales approvals are immutable");
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getInvoiceLineId() {
        return invoiceLineId;
    }

    public SalesApprovalType getApprovalType() {
        return approvalType;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}

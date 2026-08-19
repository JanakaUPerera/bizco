package com.bizco.server.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A versioned estimate on a {@link JobCard} (DomainModel.md &sect;14.5, StateMachines.md
 * &sect;12). "A revised estimate is a new version rather than silently rewriting an accepted/
 * declined historical estimate" (DatabaseDesign.md &sect;20.4) - {@code estimateVersion} is
 * assigned once at construction and never changes.
 */
@Entity
@Table(name = "job_estimates")
public class JobEstimate {

    @Id
    @GeneratedValue
    @Column(name = "job_estimate_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;
    @Column(name = "estimate_version", nullable = false)
    private int estimateVersion;
    @Column(name = "estimated_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedTotal;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_response", nullable = false, length = 20)
    private EstimateResponseStatus customerResponse = EstimateResponseStatus.PENDING;
    @Column(name = "customer_response_at")
    private Instant customerResponseAt;
    @Column(columnDefinition = "TEXT")
    private String notes;

    protected JobEstimate() {
    }

    public JobEstimate(final int estimateVersion, final BigDecimal estimatedTotal, final String description,
                       final UUID createdBy, final String notes) {
        this.estimateVersion = estimateVersion;
        this.estimatedTotal = estimatedTotal;
        this.description = description;
        this.createdBy = createdBy;
        this.notes = notes;
    }

    void assignTo(final JobCard jobCard) {
        this.jobCard = jobCard;
    }

    /** StateMachines.md &sect;12.2. */
    public void accept(final String responseNotes) {
        assertPending();
        customerResponse = EstimateResponseStatus.ACCEPTED;
        customerResponseAt = Instant.now();
        appendNotes(responseNotes);
    }

    public void decline(final String responseNotes) {
        assertPending();
        customerResponse = EstimateResponseStatus.DECLINED;
        customerResponseAt = Instant.now();
        appendNotes(responseNotes);
    }

    private void assertPending() {
        if (customerResponse != EstimateResponseStatus.PENDING) {
            throw new IllegalStateException("Estimate is " + customerResponse + " and cannot be responded to again");
        }
    }

    private void appendNotes(final String responseNotes) {
        if (responseNotes == null || responseNotes.isBlank()) {
            return;
        }
        this.notes = notes == null || notes.isBlank() ? responseNotes : notes + " | " + responseNotes;
    }

    public UUID getId() {
        return id;
    }

    public int getEstimateVersion() {
        return estimateVersion;
    }

    public BigDecimal getEstimatedTotal() {
        return estimatedTotal;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public EstimateResponseStatus getCustomerResponse() {
        return customerResponse;
    }

    public Instant getCustomerResponseAt() {
        return customerResponseAt;
    }

    public String getNotes() {
        return notes;
    }
}

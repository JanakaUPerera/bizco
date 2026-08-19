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
import java.util.UUID;

/**
 * A service line on a {@link JobCard} (DomainModel.md &sect;14.3, StateMachines.md &sect;11).
 * {@code serviceId} is a plain foreign key to {@code service_definitions}, not a JPA association,
 * following the same cross-module boundary rule as {@link com.bizco.server.sales.domain.InvoiceLine}.
 */
@Entity
@Table(name = "job_services")
public class JobService {

    @Id
    @GeneratedValue
    @Column(name = "job_service_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "estimated_cost", precision = 15, scale = 2)
    private BigDecimal estimatedCost;
    @Column(name = "actual_cost", precision = 15, scale = 2)
    private BigDecimal actualCost;
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobServiceStatus status = JobServiceStatus.PENDING;
    @Column(columnDefinition = "TEXT")
    private String notes;

    protected JobService() {
    }

    public JobService(final UUID serviceId, final BigDecimal estimatedCost, final Integer estimatedDurationMinutes,
                      final String notes) {
        this.serviceId = serviceId;
        this.estimatedCost = estimatedCost;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.notes = notes;
    }

    void assignTo(final JobCard jobCard) {
        this.jobCard = jobCard;
    }

    /** StateMachines.md &sect;11.2. */
    public void start() {
        if (status != JobServiceStatus.PENDING) {
            throw new IllegalStateException("Job service is " + status + " and cannot start");
        }
        status = JobServiceStatus.IN_PROGRESS;
    }

    public void complete(final BigDecimal actualCost) {
        if (status != JobServiceStatus.IN_PROGRESS) {
            throw new IllegalStateException("Job service is " + status + " and cannot complete");
        }
        status = JobServiceStatus.COMPLETED;
        this.actualCost = actualCost;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public BigDecimal getActualCost() {
        return actualCost;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public JobServiceStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}

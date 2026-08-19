package com.bizco.server.scheduling.domain;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Job card aggregate (DomainModel.md &sect;14.2, DatabaseDesign.md &sect;20,
 * StateMachines.md &sect;10). {@code customerId}/{@code technicianId}/{@code appointmentId}/
 * {@code serviceInvoiceId} are plain foreign keys, not JPA associations, following the same
 * cross-module boundary rule as {@link com.bizco.server.scheduling.domain.Appointment}.
 *
 * <p>{@code serviceInvoiceId} is not in {@code DatabaseDesign.md}'s illustrative DDL, but
 * StateMachines.md &sect;10.9 requires checking "required linked invoice exists ... payment/
 * authorized credit condition satisfied" before completion, which needs somewhere to record which
 * invoice that is once {@code JobCardService#generateServiceInvoice} creates one.
 */
@Entity
@Table(name = "job_cards")
public class JobCard {

    @Id
    @GeneratedValue
    @Column(name = "job_card_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "job_number", nullable = false, length = 30)
    private String jobNumber;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "technician_id")
    private UUID technicianId;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "service_invoice_id")
    private UUID serviceInvoiceId;

    @Column(name = "device_type", length = 50)
    private String deviceType;
    @Column(length = 100)
    private String brand;
    @Column(length = 100)
    private String model;
    @Column(name = "serial_number", length = 100)
    private String serialNumber;
    @Column(name = "reported_issue", columnDefinition = "TEXT")
    private String reportedIssue;
    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;
    @Column(name = "accessories_received", columnDefinition = "TEXT")
    private String accessoriesReceived;
    @Column(name = "device_condition", columnDefinition = "TEXT")
    private String deviceCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobCardStatus status = JobCardStatus.CREATED;

    @Column(name = "estimated_completion_date")
    private LocalDate estimatedCompletionDate;
    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;
    @Column(name = "pickup_date")
    private LocalDate pickupDate;
    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JobService> services = new ArrayList<>();
    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JobPart> parts = new ArrayList<>();
    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JobEstimate> estimates = new ArrayList<>();

    protected JobCard() {
    }

    public JobCard(final String jobNumber, final UUID appointmentId, final UUID customerId,
                   final UUID technicianId, final UUID createdBy, final String deviceType, final String brand,
                   final String model, final String serialNumber, final String reportedIssue,
                   final String customerNotes, final String accessoriesReceived, final String deviceCondition) {
        this.jobNumber = jobNumber;
        this.appointmentId = appointmentId;
        this.customerId = customerId;
        this.technicianId = technicianId;
        this.createdBy = createdBy;
        this.deviceType = deviceType;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.reportedIssue = reportedIssue;
        this.customerNotes = customerNotes;
        this.accessoriesReceived = accessoriesReceived;
        this.deviceCondition = deviceCondition;
    }

    /** StateMachines.md &sect;10.3: CREATED -&gt; ESTIMATE_PENDING. */
    public void moveToEstimatePending() {
        assertOneOf(JobCardStatus.CREATED);
        status = JobCardStatus.ESTIMATE_PENDING;
        touch();
    }

    /** StateMachines.md &sect;10.4/10.7: CREATED (authorized skip) or ESTIMATE_APPROVED -&gt; IN_PROGRESS. */
    public void start() {
        assertOneOf(JobCardStatus.CREATED, JobCardStatus.ESTIMATE_APPROVED);
        status = JobCardStatus.IN_PROGRESS;
        touch();
    }

    /** StateMachines.md &sect;10.5. */
    public void approveEstimate() {
        assertOneOf(JobCardStatus.ESTIMATE_PENDING);
        status = JobCardStatus.ESTIMATE_APPROVED;
        touch();
    }

    /** StateMachines.md &sect;10.8. */
    public void markReadyForPickup() {
        assertOneOf(JobCardStatus.IN_PROGRESS);
        status = JobCardStatus.READY_FOR_PICKUP;
        touch();
    }

    /** StateMachines.md &sect;10.9. */
    public void completeAndPickup(final LocalDate pickupDate, final LocalDate warrantyEndDate) {
        assertOneOf(JobCardStatus.READY_FOR_PICKUP);
        status = JobCardStatus.COMPLETED;
        this.actualCompletionDate = pickupDate;
        this.pickupDate = pickupDate;
        this.warrantyEndDate = warrantyEndDate;
        touch();
    }

    /** StateMachines.md &sect;10.10: any non-terminal state, reason mandatory (enforced by the caller). */
    public void cancel(final String reason) {
        if (isTerminal()) {
            throw new IllegalStateException("Job card is " + status + " and cannot be cancelled");
        }
        status = JobCardStatus.CANCELLED;
        appendReason(reason);
        touch();
    }

    public void addService(final JobService jobService) {
        jobService.assignTo(this);
        services.add(jobService);
    }

    public void addPart(final JobPart jobPart) {
        jobPart.assignTo(this);
        parts.add(jobPart);
    }

    public void addEstimate(final JobEstimate jobEstimate) {
        jobEstimate.assignTo(this);
        estimates.add(jobEstimate);
    }

    public void assignTechnician(final UUID technicianId) {
        this.technicianId = technicianId;
        touch();
    }

    /** ApiContracts.md &sect;29.4: editable intake details, not permitted once the job is terminal. */
    public void updateDetails(final String deviceType, final String brand, final String model,
                              final String serialNumber, final String reportedIssue, final String customerNotes,
                              final String accessoriesReceived, final String deviceCondition,
                              final UUID technicianId) {
        if (isTerminal()) {
            throw new IllegalStateException("Job card is " + status + " and cannot be updated");
        }
        this.deviceType = deviceType;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.reportedIssue = reportedIssue;
        this.customerNotes = customerNotes;
        this.accessoriesReceived = accessoriesReceived;
        this.deviceCondition = deviceCondition;
        this.technicianId = technicianId;
        touch();
    }

    public void linkInvoice(final UUID invoiceId) {
        this.serviceInvoiceId = invoiceId;
        touch();
    }

    public void setEstimatedCompletionDate(final LocalDate estimatedCompletionDate) {
        this.estimatedCompletionDate = estimatedCompletionDate;
        touch();
    }

    public boolean isTerminal() {
        return status == JobCardStatus.COMPLETED || status == JobCardStatus.CANCELLED;
    }

    private void assertOneOf(final JobCardStatus... allowed) {
        for (final JobCardStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Job card is " + status + " and cannot transition from there");
    }

    private void appendReason(final String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        this.customerNotes = customerNotes == null || customerNotes.isBlank() ? reason : customerNotes + " | " + reason;
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getServiceInvoiceId() {
        return serviceInvoiceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getReportedIssue() {
        return reportedIssue;
    }

    public String getCustomerNotes() {
        return customerNotes;
    }

    public String getAccessoriesReceived() {
        return accessoriesReceived;
    }

    public String getDeviceCondition() {
        return deviceCondition;
    }

    public JobCardStatus getStatus() {
        return status;
    }

    public LocalDate getEstimatedCompletionDate() {
        return estimatedCompletionDate;
    }

    public LocalDate getActualCompletionDate() {
        return actualCompletionDate;
    }

    public LocalDate getPickupDate() {
        return pickupDate;
    }

    public LocalDate getWarrantyEndDate() {
        return warrantyEndDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<JobService> getServices() {
        return List.copyOf(services);
    }

    public List<JobPart> getParts() {
        return List.copyOf(parts);
    }

    public List<JobEstimate> getEstimates() {
        return List.copyOf(estimates);
    }
}

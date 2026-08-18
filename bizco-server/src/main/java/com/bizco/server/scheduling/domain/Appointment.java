package com.bizco.server.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Appointment aggregate (DomainModel.md &sect;13.2, DatabaseDesign.md &sect;19,
 * StateMachines.md &sect;8). {@code customerId}/{@code serviceId}/{@code technicianId} are plain
 * foreign keys, not JPA associations, following the same cross-module boundary rule as
 * {@link com.bizco.server.sales.domain.HeldSale}.
 *
 * <p>{@code blockedUntilAt} snapshots the configured appointment buffer (service end + buffer)
 * at create/reschedule time, so the {@code ex_appointments_technician_overlap} GiST exclusion
 * constraint (DatabaseDesign.md &sect;19.3) stays deterministic even if the buffer configuration
 * changes later. That constraint - not any check performed here - is the final authority on
 * double-booking (DatabaseDesign.md &sect;39); the guards below only produce a clean domain error
 * for the common case instead of a raw entity-state exception.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue
    @Column(name = "appointment_id")
    private UUID id;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "appointment_number", nullable = false, length = 30)
    private String appointmentNumber;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "technician_id")
    private UUID technicianId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    @Column(name = "blocked_until_at", nullable = false)
    private Instant blockedUntilAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "is_walk_in", nullable = false)
    private boolean walkIn;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected Appointment() {
    }

    public Appointment(final String appointmentNumber, final UUID customerId, final UUID serviceId,
                       final UUID technicianId, final Instant startAt, final Instant endAt,
                       final Instant blockedUntilAt, final String notes, final boolean walkIn,
                       final UUID createdBy) {
        this.appointmentNumber = appointmentNumber;
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.technicianId = technicianId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.blockedUntilAt = blockedUntilAt;
        this.notes = notes;
        this.walkIn = walkIn;
        this.createdBy = createdBy;
    }

    /** StateMachines.md &sect;8.9: only legal while the appointment can still be scheduled. */
    public void reschedule(final UUID serviceId, final UUID technicianId, final Instant startAt,
                           final Instant endAt, final Instant blockedUntilAt, final String notes) {
        assertReschedulable();
        this.serviceId = serviceId;
        this.technicianId = technicianId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.blockedUntilAt = blockedUntilAt;
        this.notes = notes;
        touch();
    }

    /** StateMachines.md &sect;8.4. */
    public void confirm() {
        assertOneOf(AppointmentStatus.SCHEDULED);
        status = AppointmentStatus.CONFIRMED;
        touch();
    }

    /** StateMachines.md &sect;8.5. */
    public void start() {
        assertOneOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        status = AppointmentStatus.IN_PROGRESS;
        touch();
    }

    /** StateMachines.md &sect;8.6. */
    public void complete() {
        assertOneOf(AppointmentStatus.IN_PROGRESS);
        status = AppointmentStatus.COMPLETED;
        touch();
    }

    /** StateMachines.md &sect;8.7. */
    public void markNoShow(final String reason) {
        assertOneOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        status = AppointmentStatus.NO_SHOW;
        appendReason(reason);
        touch();
    }

    /** StateMachines.md &sect;8.8. */
    public void cancel(final String reason) {
        assertOneOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        status = AppointmentStatus.CANCELLED;
        appendReason(reason);
        touch();
    }

    private void assertReschedulable() {
        assertOneOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
    }

    private void assertOneOf(final AppointmentStatus... allowed) {
        for (final AppointmentStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Appointment is " + status + " and cannot transition from there");
    }

    private void appendReason(final String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        this.notes = notes == null || notes.isBlank() ? reason : notes + " | " + reason;
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

    public String getAppointmentNumber() {
        return appointmentNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Instant getBlockedUntilAt() {
        return blockedUntilAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isWalkIn() {
        return walkIn;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}

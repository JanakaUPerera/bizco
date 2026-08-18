package com.bizco.common.dto.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ApiContracts.md &sect;27-28. */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /** {@code endAt} is intentionally absent - the server derives it from the service's estimated duration plus the configured buffer (ApiContracts.md &sect;27.3). */
    public record CreateAppointmentRequest(
            UUID customerId,
            UUID serviceId,
            UUID technicianId,
            Instant startAt,
            String notes,
            boolean walkIn
    ) {
    }

    public record RescheduleAppointmentRequest(
            UUID serviceId,
            UUID technicianId,
            Instant startAt,
            String notes,
            long version
    ) {
    }

    /** ApiContracts.md &sect;27.5: server maps {@code targetStatus} to the matching state-machine transition. */
    public record AppointmentStatusRequest(
            String targetStatus,
            String reason,
            long version
    ) {
    }

    public record AppointmentResponse(
            UUID appointmentId,
            String appointmentNumber,
            UUID customerId,
            String customerName,
            UUID serviceId,
            String serviceName,
            UUID technicianId,
            String technicianName,
            Instant startAt,
            Instant endAt,
            Instant blockedUntilAt,
            String status,
            String notes,
            boolean walkIn,
            long version,
            Instant createdAt
    ) {
    }

    public record AppointmentSearchResponse(
            List<AppointmentResponse> data
    ) {
    }

    /**
     * ApiContracts.md &sect;27.2: best-effort UI hint only, computed from currently booked slots.
     * The {@code ex_appointments_technician_overlap} exclusion constraint (DatabaseDesign.md
     * &sect;19.3) remains the final authority - a slot marked available here can still be
     * rejected at create time if another booking committed first.
     */
    public record AvailabilitySlotResponse(
            Instant startAt,
            Instant endAt,
            boolean available
    ) {
    }

    public record AvailabilityResponse(
            List<AvailabilitySlotResponse> slots
    ) {
    }

    public record TechnicianResponse(
            UUID userId,
            String fullName,
            boolean active
    ) {
    }

    public record TechnicianListResponse(
            List<TechnicianResponse> data
    ) {
    }
}

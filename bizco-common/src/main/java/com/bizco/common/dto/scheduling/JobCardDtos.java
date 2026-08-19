package com.bizco.common.dto.scheduling;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ApiContracts.md &sect;29-33. */
public final class JobCardDtos {

    private JobCardDtos() {
    }

    /** ApiContracts.md &sect;29.2: walk-in job, no appointment. */
    public record CreateJobCardRequest(
            UUID customerId,
            UUID technicianId,
            String deviceType,
            String brand,
            String model,
            String serialNumber,
            String reportedIssue,
            String customerNotes,
            String accessoriesReceived,
            String deviceCondition
    ) {
    }

    /** ApiContracts.md &sect;27.7: device intake captured at the point an appointment becomes a job. */
    public record ConvertAppointmentRequest(
            String deviceType,
            String brand,
            String model,
            String serialNumber,
            String reportedIssue,
            String accessoriesReceived,
            String deviceCondition
    ) {
    }

    /** ApiContracts.md &sect;29.4. */
    public record UpdateJobCardRequest(
            String deviceType,
            String brand,
            String model,
            String serialNumber,
            String reportedIssue,
            String customerNotes,
            String accessoriesReceived,
            String deviceCondition,
            UUID technicianId,
            long version
    ) {
    }

    /**
     * {@code overrideEstimateRequirement} backs StateMachines.md &sect;10.4's "authorized manager
     * override rule" for starting work before a mandatory estimate is accepted.
     */
    public record JobCardStatusRequest(
            String targetStatus,
            String reason,
            boolean overrideEstimateRequirement,
            long version
    ) {
    }

    public record AddJobServiceRequest(
            UUID serviceId,
            BigDecimal estimatedCost,
            Integer estimatedDurationMinutes,
            String notes
    ) {
    }

    public record JobServiceStatusRequest(
            String targetStatus,
            BigDecimal actualCost
    ) {
    }

    public record CreateEstimateRequest(
            BigDecimal estimatedTotal,
            String description,
            String notes
    ) {
    }

    public record EstimateResponseRequest(
            String notes
    ) {
    }

    public record AddJobPartRequest(
            UUID productId,
            BigDecimal quantity,
            BigDecimal customerUnitPrice,
            boolean warrantyCovered
    ) {
    }

    public record CustomInvoiceLineRequest(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String taxCategory
    ) {
    }

    /** ApiContracts.md &sect;33. */
    public record GenerateServiceInvoiceRequest(
            boolean includeCompletedServices,
            boolean includeParts,
            List<CustomInvoiceLineRequest> customLines
    ) {
    }

    public record JobServiceResponse(
            UUID jobServiceId,
            UUID serviceId,
            String serviceName,
            BigDecimal estimatedCost,
            BigDecimal actualCost,
            Integer estimatedDurationMinutes,
            String status,
            String notes
    ) {
    }

    public record JobPartResponse(
            UUID jobPartId,
            UUID productId,
            String sku,
            String productName,
            BigDecimal quantityUsed,
            BigDecimal unitPriceSnapshot,
            BigDecimal costPriceSnapshot,
            boolean warrantyCovered,
            Instant postedAt
    ) {
    }

    public record JobEstimateResponse(
            UUID jobEstimateId,
            int estimateVersion,
            BigDecimal estimatedTotal,
            String description,
            String customerResponse,
            Instant customerResponseAt,
            String notes,
            Instant createdAt
    ) {
    }

    public record JobCardResponse(
            UUID jobCardId,
            String jobNumber,
            UUID appointmentId,
            UUID customerId,
            String customerName,
            UUID technicianId,
            String technicianName,
            String deviceType,
            String brand,
            String model,
            String serialNumber,
            String reportedIssue,
            String customerNotes,
            String accessoriesReceived,
            String deviceCondition,
            String status,
            LocalDate estimatedCompletionDate,
            LocalDate actualCompletionDate,
            LocalDate pickupDate,
            LocalDate warrantyEndDate,
            UUID serviceInvoiceId,
            long version,
            Instant createdAt,
            List<JobServiceResponse> services,
            List<JobPartResponse> parts,
            List<JobEstimateResponse> estimates
    ) {
    }

    public record JobCardSummaryResponse(
            UUID jobCardId,
            String jobNumber,
            UUID customerId,
            String customerName,
            UUID technicianId,
            String technicianName,
            String status,
            String deviceType,
            Instant createdAt
    ) {
    }

    public record JobCardSearchResponse(
            List<JobCardSummaryResponse> data
    ) {
    }
}

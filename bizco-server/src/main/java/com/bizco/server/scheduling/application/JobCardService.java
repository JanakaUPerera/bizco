package com.bizco.server.scheduling.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobServiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.ConvertAppointmentRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateEstimateRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CustomInvoiceLineRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.EstimateResponseRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.GenerateServiceInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardSearchResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardSummaryResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobEstimateResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobPartResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.UpdateJobCardRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductType;
import com.bizco.server.catalog.domain.ServiceDefinition;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.catalog.infrastructure.ServiceDefinitionRepository;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.scheduling.domain.Appointment;
import com.bizco.server.scheduling.domain.AppointmentStatus;
import com.bizco.server.scheduling.domain.EstimateResponseStatus;
import com.bizco.server.scheduling.domain.JobCard;
import com.bizco.server.scheduling.domain.JobCardStatus;
import com.bizco.server.scheduling.domain.JobEstimate;
import com.bizco.server.scheduling.domain.JobPart;
import com.bizco.server.scheduling.domain.JobService;
import com.bizco.server.scheduling.domain.JobServiceStatus;
import com.bizco.server.scheduling.infrastructure.AppointmentRepository;
import com.bizco.server.scheduling.infrastructure.JobCardRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job card / repair-service application service (DevelopmentPlan.md Week 11, StateMachines.md
 * &sect;10-13, DomainModel.md &sect;14, ApiContracts.md &sect;29-33).
 *
 * <p><b>Known gap</b> (same shape as {@code HeldSale}'s documented stock gap): {@link #addPart}
 * does not check available stock and does not create a stock movement - the stock ledger
 * ({@code stock_movements}, Phase 5/Week 12) does not exist yet. See {@link JobPart}'s Javadoc.
 */
@Service
public class JobCardService {

    private final JobCardRepository jobCardRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServiceDefinitionRepository serviceDefinitionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IdempotencyService idempotencyService;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public JobCardService(final JobCardRepository jobCardRepository, final AppointmentRepository appointmentRepository,
                          final CustomerRepository customerRepository,
                          final ServiceDefinitionRepository serviceDefinitionRepository,
                          final ProductRepository productRepository, final UserRepository userRepository,
                          final DocumentSequenceRepository documentSequenceRepository,
                          final IdempotencyService idempotencyService, final InvoiceService invoiceService,
                          final AuditService auditService) {
        this.jobCardRepository = jobCardRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.idempotencyService = idempotencyService;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    /** ApiContracts.md &sect;29.2: walk-in job, no appointment. */
    @Transactional
    public JobCardResponse createWalkIn(final CreateJobCardRequest request, final Authentication authentication) {
        requireCustomer(request.customerId());
        final UUID actorId = actor(authentication);
        final String number = nextJobNumber();
        final JobCard job = new JobCard(number, null, request.customerId(), request.technicianId(), actorId,
                request.deviceType(), request.brand(), request.model(), request.serialNumber(),
                request.reportedIssue(), request.customerNotes(), request.accessoriesReceived(),
                request.deviceCondition());
        final JobCard saved = jobCardRepository.save(job);
        auditService.record("JOB_CARD", saved.getId().toString(), "JOB_CARD_CREATED", actorId,
                Map.of("jobNumber", number));
        return toResponse(saved);
    }

    /**
     * ApiContracts.md &sect;27.7: called from {@code AppointmentController}. Idempotent by
     * {@code Idempotency-Key} (JOB-CONV-003) and by natural key - {@code job_cards.appointment_id}
     * is unique, so a duplicate attempt (whether the same key retried or a distinct concurrent
     * caller) always resolves to the one job card that exists for the appointment (JOB-CONV-002).
     */
    @Transactional
    public IdempotentResult<JobCardResponse> convertFromAppointment(final UUID idempotencyKey,
                                                                     final UUID appointmentId,
                                                                     final ConvertAppointmentRequest request,
                                                                     final Authentication authentication) {
        return idempotencyService.execute(idempotencyKey, "appointment.convert_to_job", request,
                JobCardResponse.class, () -> doConvert(appointmentId, request, authentication));
    }

    private JobCardResponse doConvert(final UUID appointmentId, final ConvertAppointmentRequest request,
                                      final Authentication authentication) {
        final Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.APPOINTMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Appointment was not found"));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_NOT_CONVERTIBLE, HttpStatus.CONFLICT,
                    "A cancelled or no-show appointment cannot be converted to a job");
        }
        final Optional<JobCard> existing = jobCardRepository.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        final UUID actorId = actor(authentication);
        final String number = nextJobNumber();
        final JobCard job = new JobCard(number, appointmentId, appointment.getCustomerId(),
                appointment.getTechnicianId(), actorId, request.deviceType(), request.brand(), request.model(),
                request.serialNumber(), request.reportedIssue(), null, request.accessoriesReceived(),
                request.deviceCondition());
        final ServiceDefinition service = serviceDefinitionRepository.findById(appointment.getServiceId()).orElse(null);
        job.addService(new JobService(appointment.getServiceId(), service == null ? null : service.getBasePrice(),
                service == null ? null : service.getEstimatedDurationMinutes(), null));
        maybeMoveToEstimatePending(job);

        final JobCard saved;
        try {
            saved = jobCardRepository.save(job);
            jobCardRepository.flush();
        } catch (final DataIntegrityViolationException ex) {
            // job_cards.appointment_id UNIQUE: a concurrent conversion attempt under a different
            // Idempotency-Key already won for this appointment - JOB-CONV-002 requires exactly one
            // job to ever exist for it, so return that one instead of surfacing a raw conflict.
            return jobCardRepository.findByAppointmentId(appointmentId).map(this::toResponse).orElseThrow(() -> ex);
        }
        auditService.record("JOB_CARD", saved.getId().toString(), "APPOINTMENT_CONVERTED_TO_JOB", actorId,
                Map.of("jobNumber", number, "appointmentId", appointmentId.toString()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public JobCardSearchResponse search(final String q, final String status, final UUID customerId,
                                        final UUID technicianId, final Instant from, final Instant to,
                                        final int page, final int size) {
        final JobCardStatus statusFilter = status == null || status.isBlank() ? null
                : JobCardStatus.valueOf(status.trim().toUpperCase());
        final Page<JobCard> result = jobCardRepository.search(blankToNull(q), statusFilter, customerId, technicianId,
                from, to, PageRequest.of(page, size));
        return new JobCardSearchResponse(result.getContent().stream().map(this::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public JobCardResponse get(final UUID jobCardId) {
        return toResponse(load(jobCardId));
    }

    /** ApiContracts.md &sect;29.4. */
    @Transactional
    public JobCardResponse update(final UUID jobCardId, final UpdateJobCardRequest request,
                                  final Authentication authentication) {
        final JobCard job = load(jobCardId);
        assertVersion(job, request.version());
        try {
            job.updateDetails(request.deviceType(), request.brand(), request.model(), request.serialNumber(),
                    request.reportedIssue(), request.customerNotes(), request.accessoriesReceived(),
                    request.deviceCondition(), request.technicianId());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT, ex.getMessage());
        }
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_CARD_UPDATED", actor(authentication),
                Map.of("jobNumber", job.getJobNumber()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;30.1. */
    @Transactional
    public JobCardResponse addService(final UUID jobCardId, final AddJobServiceRequest request,
                                      final Authentication authentication) {
        final JobCard job = load(jobCardId);
        if (job.isTerminal()) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Job card is " + job.getStatus() + " and cannot receive a new service");
        }
        if (request.serviceId() == null) {
            throw fieldError("serviceId", "REQUIRED", "Service is required.");
        }
        final ServiceDefinition service = serviceDefinitionRepository.findById(request.serviceId()).orElseThrow(
                () -> new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Service was not found"));
        job.addService(new JobService(service.getId(),
                request.estimatedCost() != null ? request.estimatedCost() : service.getBasePrice(),
                request.estimatedDurationMinutes() != null ? request.estimatedDurationMinutes()
                        : service.getEstimatedDurationMinutes(), request.notes()));
        maybeMoveToEstimatePending(job);
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_SERVICE_ADDED", actor(authentication),
                Map.of("serviceId", service.getId().toString()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;30.2, StateMachines.md &sect;11.2. */
    @Transactional
    public JobCardResponse changeServiceStatus(final UUID jobCardId, final UUID jobServiceId,
                                               final JobServiceStatusRequest request,
                                               final Authentication authentication) {
        final JobCard job = load(jobCardId);
        final JobService jobService = findService(job, jobServiceId);
        final String target = request.targetStatus() == null ? "" : request.targetStatus().trim().toUpperCase();
        try {
            switch (target) {
                case "IN_PROGRESS" -> jobService.start();
                case "COMPLETED" -> jobService.complete(request.actualCost());
                default -> throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                        "Unknown target status: " + request.targetStatus());
            }
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT, ex.getMessage());
        }
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_SERVICE_" + target, actor(authentication),
                Map.of("jobServiceId", jobServiceId.toString()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;31.1, StateMachines.md &sect;12: server increments {@code estimateVersion}. */
    @Transactional
    public JobCardResponse createEstimate(final UUID jobCardId, final CreateEstimateRequest request,
                                          final Authentication authentication) {
        final JobCard job = load(jobCardId);
        if (job.isTerminal()) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Job card is " + job.getStatus() + " and cannot receive a new estimate");
        }
        if (request.estimatedTotal() == null || request.estimatedTotal().signum() < 0) {
            throw fieldError("estimatedTotal", "INVALID", "Estimated total must be zero or greater.");
        }
        final int nextVersion = job.getEstimates().stream().mapToInt(JobEstimate::getEstimateVersion).max().orElse(0) + 1;
        final UUID actorId = actor(authentication);
        job.addEstimate(new JobEstimate(nextVersion, request.estimatedTotal(), request.description(), actorId,
                request.notes()));
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_ESTIMATE_CREATED", actorId,
                Map.of("estimateVersion", String.valueOf(nextVersion)));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;31.2, StateMachines.md &sect;10.5/12.2. */
    @Transactional
    public JobCardResponse acceptEstimate(final UUID jobCardId, final UUID estimateId,
                                          final EstimateResponseRequest request, final Authentication authentication) {
        final JobCard job = load(jobCardId);
        final JobEstimate estimate = findEstimate(job, estimateId);
        try {
            estimate.accept(request.notes());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.JOB_ESTIMATE_NOT_PENDING, HttpStatus.CONFLICT, ex.getMessage());
        }
        if (job.getStatus() == JobCardStatus.ESTIMATE_PENDING) {
            job.approveEstimate();
        }
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_ESTIMATE_ACCEPTED", actor(authentication),
                Map.of("estimateId", estimateId.toString()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;31.3, StateMachines.md &sect;10.6/12.2. */
    @Transactional
    public JobCardResponse declineEstimate(final UUID jobCardId, final UUID estimateId,
                                           final EstimateResponseRequest request, final Authentication authentication) {
        final JobCard job = load(jobCardId);
        final JobEstimate estimate = findEstimate(job, estimateId);
        try {
            estimate.decline(request.notes());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.JOB_ESTIMATE_NOT_PENDING, HttpStatus.CONFLICT, ex.getMessage());
        }
        if (!job.isTerminal()) {
            job.cancel(request.notes() == null || request.notes().isBlank() ? "Estimate declined" : request.notes());
        }
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_ESTIMATE_DECLINED", actor(authentication),
                Map.of("estimateId", estimateId.toString()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;29.5. */
    @Transactional
    public JobCardResponse changeStatus(final UUID jobCardId, final JobCardStatusRequest request,
                                        final Authentication authentication) {
        final JobCard job = load(jobCardId);
        assertVersion(job, request.version());
        final String target = request.targetStatus() == null ? "" : request.targetStatus().trim().toUpperCase();
        try {
            switch (target) {
                case "ESTIMATE_PENDING" -> job.moveToEstimatePending();
                case "IN_PROGRESS" -> {
                    assertEstimateSatisfied(job, request.overrideEstimateRequirement());
                    job.start();
                }
                case "READY_FOR_PICKUP" -> {
                    assertAllServicesCompleted(job);
                    job.markReadyForPickup();
                }
                case "COMPLETED" -> {
                    requirePermission(authentication, "jobcard.complete");
                    completeJob(job);
                }
                case "CANCELLED" -> {
                    if (request.reason() == null || request.reason().isBlank()) {
                        throw fieldError("reason", "REQUIRED", "Cancellation reason is required.");
                    }
                    job.cancel(request.reason());
                }
                default -> throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                        "Unknown target status: " + request.targetStatus());
            }
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT, ex.getMessage());
        }
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_CARD_" + target, actor(authentication),
                Map.of("jobNumber", job.getJobNumber()));
        return toResponse(job);
    }

    /** ApiContracts.md &sect;32, StateMachines.md &sect;13. Idempotent (JOB-PART-003). */
    @Transactional
    public IdempotentResult<JobPartResponse> addPart(final UUID idempotencyKey, final UUID jobCardId,
                                                      final AddJobPartRequest request,
                                                      final Authentication authentication) {
        return idempotencyService.execute(idempotencyKey, "jobcard.parts.add", request, JobPartResponse.class,
                () -> doAddPart(idempotencyKey, jobCardId, request, authentication));
    }

    private JobPartResponse doAddPart(final UUID idempotencyKey, final UUID jobCardId, final AddJobPartRequest request,
                                      final Authentication authentication) {
        final JobCard job = load(jobCardId);
        if (job.isTerminal()) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Job card is " + job.getStatus() + " and cannot receive parts");
        }
        if (request.productId() == null) {
            throw fieldError("productId", "REQUIRED", "Product is required.");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw fieldError("quantity", "INVALID", "Quantity must be greater than zero.");
        }
        final Product product = productRepository.findById(request.productId()).orElseThrow(
                () -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product was not found"));
        if (product.getProductType() != ProductType.INVENTORY) {
            throw new IdentityException(ApiErrorCode.JOB_PART_PRODUCT_NOT_INVENTORY, HttpStatus.BAD_REQUEST,
                    "Only inventory products can be consumed as job parts");
        }
        final BigDecimal unitPrice = request.customerUnitPrice() != null ? request.customerUnitPrice()
                : product.getSellingPrice();
        // Known gap (see JobPart's Javadoc): available stock is not checked and no stock movement
        // is created here - the stock ledger (Phase 5/Week 12) does not exist yet.
        final JobPart part = new JobPart(idempotencyKey, product.getId(), request.quantity(), unitPrice,
                product.getCostPrice(), request.warrantyCovered());
        job.addPart(part);
        jobCardRepository.flush();
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_PART_CONSUMED", actor(authentication),
                Map.of("productId", product.getId().toString(), "quantity", request.quantity().toPlainString()));
        return toPartResponse(part);
    }

    /**
     * ApiContracts.md &sect;33, DomainModel.md &sect;14.9. Warranty-covered parts are not charged
     * to the customer, so they are excluded from the generated lines.
     */
    @Transactional
    public InvoiceDetailResponse generateServiceInvoice(final UUID jobCardId, final GenerateServiceInvoiceRequest request,
                                                        final Authentication authentication) {
        final JobCard job = load(jobCardId);
        if (job.isTerminal()) {
            throw new IdentityException(ApiErrorCode.JOB_CARD_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Job card is " + job.getStatus() + " and cannot be invoiced");
        }
        final InvoiceSummaryResponse draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(),
                null, "SERVICE", job.getCustomerId(), "Job " + job.getJobNumber()), authentication);
        if (request.includeCompletedServices()) {
            for (final JobService jobService : job.getServices()) {
                if (jobService.getStatus() != JobServiceStatus.COMPLETED) {
                    continue;
                }
                final BigDecimal price = jobService.getActualCost() != null ? jobService.getActualCost()
                        : jobService.getEstimatedCost();
                invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("SERVICE", null,
                        jobService.getServiceId(), null, BigDecimal.ONE, price, null, DiscountRequest.NONE));
            }
        }
        if (request.includeParts()) {
            for (final JobPart part : job.getParts()) {
                if (part.isWarrantyCovered()) {
                    continue;
                }
                invoiceService.addProductLineFromJobPart(draft.invoiceId(), part.getId(), part.getProductId(),
                        part.getQuantityUsed(), part.getUnitPriceSnapshot());
            }
        }
        if (request.customLines() != null) {
            for (final CustomInvoiceLineRequest custom : request.customLines()) {
                invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("CUSTOM", null, null,
                        custom.description(), custom.quantity(), custom.unitPrice(), custom.taxCategory(),
                        DiscountRequest.NONE));
            }
        }
        job.linkInvoice(draft.invoiceId());
        auditService.record("JOB_CARD", job.getId().toString(), "JOB_CARD_INVOICE_GENERATED", actor(authentication),
                Map.of("invoiceId", draft.invoiceId().toString()));
        return invoiceService.get(draft.invoiceId());
    }

    private void completeJob(final JobCard job) {
        assertAllServicesCompleted(job);
        if (job.getServiceInvoiceId() != null) {
            final InvoiceDetailResponse invoice = invoiceService.get(job.getServiceInvoiceId());
            if (!"POSTED".equals(invoice.status())) {
                throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.CONFLICT,
                        "Linked service invoice must be posted before pickup");
            }
        }
        final LocalDate pickupDate = LocalDate.now();
        final int maxWarrantyDays = job.getServices().stream()
                .map(jobService -> serviceDefinitionRepository.findById(jobService.getServiceId()))
                .flatMap(Optional::stream)
                .mapToInt(ServiceDefinition::getWarrantyDays)
                .max().orElse(0);
        final LocalDate warrantyEndDate = maxWarrantyDays > 0 ? pickupDate.plusDays(maxWarrantyDays) : pickupDate;
        job.completeAndPickup(pickupDate, warrantyEndDate);
    }

    private void assertAllServicesCompleted(final JobCard job) {
        final boolean allDone = job.getServices().stream().allMatch(s -> s.getStatus() == JobServiceStatus.COMPLETED);
        if (!allDone) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.CONFLICT,
                    "All job services must be completed first");
        }
    }

    /** StateMachines.md &sect;10.4: mandatory estimate acceptance, unless a manager override is documented on the request. */
    private void assertEstimateSatisfied(final JobCard job, final boolean override) {
        if (override) {
            return;
        }
        final boolean requiresEstimate = job.getServices().stream()
                .map(jobService -> serviceDefinitionRepository.findById(jobService.getServiceId()))
                .flatMap(Optional::stream)
                .anyMatch(ServiceDefinition::isRequiresEstimate);
        if (!requiresEstimate) {
            return;
        }
        final boolean accepted = job.getEstimates().stream()
                .anyMatch(estimate -> estimate.getCustomerResponse() == EstimateResponseStatus.ACCEPTED);
        if (!accepted) {
            throw new IdentityException(ApiErrorCode.JOB_ESTIMATE_REQUIRED, HttpStatus.CONFLICT,
                    "An accepted estimate is required before starting work");
        }
    }

    /** StateMachines.md &sect;10.3: automatic transition when an estimate-required service is present. */
    private void maybeMoveToEstimatePending(final JobCard job) {
        if (job.getStatus() != JobCardStatus.CREATED) {
            return;
        }
        final boolean requiresEstimate = job.getServices().stream()
                .map(jobService -> serviceDefinitionRepository.findById(jobService.getServiceId()))
                .flatMap(Optional::stream)
                .anyMatch(ServiceDefinition::isRequiresEstimate);
        if (requiresEstimate) {
            job.moveToEstimatePending();
        }
    }

    private void requirePermission(final Authentication authentication, final String permission) {
        final boolean has = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
        if (!has) {
            throw new IdentityException(ApiErrorCode.AUTH_PERMISSION_DENIED, HttpStatus.FORBIDDEN,
                    "Missing required permission: " + permission);
        }
    }

    private JobService findService(final JobCard job, final UUID jobServiceId) {
        return job.getServices().stream().filter(s -> s.getId().equals(jobServiceId)).findFirst()
                .orElseThrow(() -> new IdentityException(ApiErrorCode.JOB_SERVICE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Job service was not found"));
    }

    private JobEstimate findEstimate(final JobCard job, final UUID estimateId) {
        return job.getEstimates().stream().filter(e -> e.getId().equals(estimateId)).findFirst()
                .orElseThrow(() -> new IdentityException(ApiErrorCode.JOB_ESTIMATE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Job estimate was not found"));
    }

    private void requireCustomer(final UUID customerId) {
        if (customerId == null || !customerRepository.existsById(customerId)) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND, "Customer was not found");
        }
    }

    private String nextJobNumber() {
        final LocalDate businessDate = LocalDate.now();
        final long next = documentSequenceRepository.nextDailyValue("JOB", businessDate, "JOB", 4);
        return documentSequenceRepository.formatDaily("JOB", businessDate, next, 4);
    }

    private void assertVersion(final JobCard job, final long expectedVersion) {
        if (job.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Job card was modified by another user");
        }
    }

    private JobCard load(final UUID jobCardId) {
        return jobCardRepository.findById(jobCardId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.JOB_CARD_NOT_FOUND, HttpStatus.NOT_FOUND, "Job card was not found"));
    }

    private ApiValidationException fieldError(final String field, final String code, final String message) {
        return new ApiValidationException(List.of(new FieldError(field, code, message)));
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private JobCardResponse toResponse(final JobCard job) {
        final Customer customer = customerRepository.findById(job.getCustomerId()).orElse(null);
        final User technician = job.getTechnicianId() == null ? null
                : userRepository.findById(job.getTechnicianId()).orElse(null);
        return new JobCardResponse(job.getId(), job.getJobNumber(), job.getAppointmentId(), job.getCustomerId(),
                customer == null ? null : customer.getName(), job.getTechnicianId(),
                technician == null ? null : technician.getDisplayName(), job.getDeviceType(), job.getBrand(),
                job.getModel(), job.getSerialNumber(), job.getReportedIssue(), job.getCustomerNotes(),
                job.getAccessoriesReceived(), job.getDeviceCondition(), job.getStatus().name(),
                job.getEstimatedCompletionDate(), job.getActualCompletionDate(), job.getPickupDate(),
                job.getWarrantyEndDate(), job.getServiceInvoiceId(), job.getVersion(), job.getCreatedAt(),
                job.getServices().stream().map(this::toServiceResponse).toList(),
                job.getParts().stream().map(this::toPartResponse).toList(),
                job.getEstimates().stream().map(this::toEstimateResponse).toList());
    }

    private JobCardSummaryResponse toSummary(final JobCard job) {
        final Customer customer = customerRepository.findById(job.getCustomerId()).orElse(null);
        final User technician = job.getTechnicianId() == null ? null
                : userRepository.findById(job.getTechnicianId()).orElse(null);
        return new JobCardSummaryResponse(job.getId(), job.getJobNumber(), job.getCustomerId(),
                customer == null ? null : customer.getName(), job.getTechnicianId(),
                technician == null ? null : technician.getDisplayName(), job.getStatus().name(), job.getDeviceType(),
                job.getCreatedAt());
    }

    private JobServiceResponse toServiceResponse(final JobService jobService) {
        final ServiceDefinition service = serviceDefinitionRepository.findById(jobService.getServiceId()).orElse(null);
        return new JobServiceResponse(jobService.getId(), jobService.getServiceId(),
                service == null ? null : service.getName(), jobService.getEstimatedCost(), jobService.getActualCost(),
                jobService.getEstimatedDurationMinutes(), jobService.getStatus().name(), jobService.getNotes());
    }

    private JobPartResponse toPartResponse(final JobPart part) {
        final Product product = productRepository.findById(part.getProductId()).orElse(null);
        return new JobPartResponse(part.getId(), part.getProductId(), product == null ? null : product.getSku(),
                product == null ? null : product.getName(), part.getQuantityUsed(), part.getUnitPriceSnapshot(),
                part.getCostPriceSnapshot(), part.isWarrantyCovered(), part.getPostedAt());
    }

    private JobEstimateResponse toEstimateResponse(final JobEstimate estimate) {
        return new JobEstimateResponse(estimate.getId(), estimate.getEstimateVersion(), estimate.getEstimatedTotal(),
                estimate.getDescription(), estimate.getCustomerResponse().name(), estimate.getCustomerResponseAt(),
                estimate.getNotes(), estimate.getCreatedAt());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(User::getId).orElse(null);
    }
}

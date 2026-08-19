package com.bizco.server.scheduling.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobServiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateEstimateRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.EstimateResponseRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.GenerateServiceInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardSearchResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobPartResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.UpdateJobCardRequest;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.scheduling.application.JobCardService;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ApiContracts.md &sect;29-33. */
@RestController
@RequestMapping("/api/v1/job-cards")
public class JobCardController {

    private final JobCardService service;

    public JobCardController(final JobCardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('jobcard.read')")
    JobCardSearchResponse search(@RequestParam(required = false) final String q,
                                 @RequestParam(required = false) final String status,
                                 @RequestParam(required = false) final UUID customerId,
                                 @RequestParam(required = false) final UUID technicianId,
                                 @RequestParam(required = false) final Instant fromDate,
                                 @RequestParam(required = false) final Instant toDate,
                                 @RequestParam(defaultValue = "0") final int page,
                                 @RequestParam(defaultValue = "25") final int size) {
        return service.search(q, status, customerId, technicianId, fromDate, toDate, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('jobcard.create')")
    ResponseEntity<JobCardResponse> create(@RequestBody final CreateJobCardRequest request,
                                           final Authentication authentication) {
        final JobCardResponse created = service.createWalkIn(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/job-cards/" + created.jobCardId())).body(created);
    }

    @GetMapping("/{jobCardId}")
    @PreAuthorize("hasAuthority('jobcard.read')")
    JobCardResponse get(@PathVariable final UUID jobCardId) {
        return service.get(jobCardId);
    }

    @PutMapping("/{jobCardId}")
    @PreAuthorize("hasAuthority('jobcard.update')")
    JobCardResponse update(@PathVariable final UUID jobCardId, @RequestBody final UpdateJobCardRequest request,
                           final Authentication authentication) {
        return service.update(jobCardId, request, authentication);
    }

    /** Permission actually enforced per-target-status inside the service (StateMachines.md &sect;10, ApiContracts.md &sect;29.5). */
    @PostMapping("/{jobCardId}/status")
    @PreAuthorize("hasAuthority('jobcard.status_change') or hasAuthority('jobcard.complete')")
    JobCardResponse changeStatus(@PathVariable final UUID jobCardId, @RequestBody final JobCardStatusRequest request,
                                 final Authentication authentication) {
        return service.changeStatus(jobCardId, request, authentication);
    }

    @PostMapping("/{jobCardId}/services")
    @PreAuthorize("hasAuthority('jobcard.update')")
    JobCardResponse addService(@PathVariable final UUID jobCardId, @RequestBody final AddJobServiceRequest request,
                               final Authentication authentication) {
        return service.addService(jobCardId, request, authentication);
    }

    @PostMapping("/{jobCardId}/services/{jobServiceId}/status")
    @PreAuthorize("hasAuthority('jobcard.update')")
    JobCardResponse changeServiceStatus(@PathVariable final UUID jobCardId, @PathVariable final UUID jobServiceId,
                                        @RequestBody final JobServiceStatusRequest request,
                                        final Authentication authentication) {
        return service.changeServiceStatus(jobCardId, jobServiceId, request, authentication);
    }

    @PostMapping("/{jobCardId}/estimates")
    @PreAuthorize("hasAuthority('jobcard.estimate.create')")
    JobCardResponse createEstimate(@PathVariable final UUID jobCardId, @RequestBody final CreateEstimateRequest request,
                                   final Authentication authentication) {
        return service.createEstimate(jobCardId, request, authentication);
    }

    @PostMapping("/{jobCardId}/estimates/{estimateId}/accept")
    @PreAuthorize("hasAuthority('jobcard.estimate.approve')")
    JobCardResponse acceptEstimate(@PathVariable final UUID jobCardId, @PathVariable final UUID estimateId,
                                   @RequestBody final EstimateResponseRequest request,
                                   final Authentication authentication) {
        return service.acceptEstimate(jobCardId, estimateId, request, authentication);
    }

    @PostMapping("/{jobCardId}/estimates/{estimateId}/decline")
    @PreAuthorize("hasAuthority('jobcard.estimate.approve')")
    JobCardResponse declineEstimate(@PathVariable final UUID jobCardId, @PathVariable final UUID estimateId,
                                    @RequestBody final EstimateResponseRequest request,
                                    final Authentication authentication) {
        return service.declineEstimate(jobCardId, estimateId, request, authentication);
    }

    @PostMapping("/{jobCardId}/parts")
    @PreAuthorize("hasAuthority('jobcard.parts.add')")
    ResponseEntity<JobPartResponse> addPart(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                            @PathVariable final UUID jobCardId,
                                            @RequestBody final AddJobPartRequest request,
                                            final Authentication authentication) {
        final IdempotentResult<JobPartResponse> result = service.addPart(idempotencyKey, jobCardId, request,
                authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{jobCardId}/invoice")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse generateInvoice(@PathVariable final UUID jobCardId,
                                          @RequestBody final GenerateServiceInvoiceRequest request,
                                          final Authentication authentication) {
        return service.generateServiceInvoice(jobCardId, request, authentication);
    }
}

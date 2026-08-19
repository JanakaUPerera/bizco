package com.bizco.server.scheduling.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentSearchResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.AvailabilityResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.ConvertAppointmentRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.scheduling.application.AppointmentService;
import com.bizco.server.scheduling.application.JobCardService;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
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

/** ApiContracts.md &sect;27, including &sect;27.7 convert-to-job (Week 11). */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService service;
    private final JobCardService jobCardService;

    public AppointmentController(final AppointmentService service, final JobCardService jobCardService) {
        this.service = service;
        this.jobCardService = jobCardService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('appointment.read')")
    AppointmentSearchResponse search(@RequestParam(required = false) final Instant from,
                                     @RequestParam(required = false) final Instant to,
                                     @RequestParam(required = false) final UUID technicianId,
                                     @RequestParam(required = false) final UUID customerId,
                                     @RequestParam(required = false) final String status) {
        return service.search(from, to, technicianId, customerId, status);
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('appointment.read')")
    AvailabilityResponse availability(@RequestParam final UUID serviceId,
                                      @RequestParam(required = false) final UUID technicianId,
                                      @RequestParam final LocalDate date) {
        return service.availability(serviceId, technicianId, date);
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAuthority('appointment.read')")
    AppointmentResponse get(@PathVariable final UUID appointmentId) {
        return service.get(appointmentId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('appointment.create')")
    ResponseEntity<AppointmentResponse> create(@RequestBody final CreateAppointmentRequest request,
                                               final Authentication authentication) {
        final AppointmentResponse created = service.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + created.appointmentId())).body(created);
    }

    @PutMapping("/{appointmentId}")
    @PreAuthorize("hasAuthority('appointment.update')")
    AppointmentResponse reschedule(@PathVariable final UUID appointmentId,
                                   @RequestBody final RescheduleAppointmentRequest request,
                                   final Authentication authentication) {
        return service.reschedule(appointmentId, request, authentication);
    }

    @PostMapping("/{appointmentId}/status")
    @PreAuthorize("hasAuthority('appointment.update')")
    AppointmentResponse changeStatus(@PathVariable final UUID appointmentId,
                                     @RequestBody final AppointmentStatusRequest request,
                                     final Authentication authentication) {
        return service.changeStatus(appointmentId, request, authentication);
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAuthority('appointment.cancel')")
    AppointmentResponse cancel(@PathVariable final UUID appointmentId,
                               @RequestParam(required = false) final String reason,
                               final Authentication authentication) {
        return service.cancel(appointmentId, reason, authentication);
    }

    @PostMapping("/{appointmentId}/convert-to-job")
    @PreAuthorize("hasAuthority('appointment.convert_to_job')")
    ResponseEntity<JobCardResponse> convertToJob(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                 @PathVariable final UUID appointmentId,
                                                 @RequestBody final ConvertAppointmentRequest request,
                                                 final Authentication authentication) {
        final IdempotentResult<JobCardResponse> result = jobCardService.convertFromAppointment(idempotencyKey,
                appointmentId, request, authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }
}

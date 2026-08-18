package com.bizco.server.scheduling.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentSearchResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.AvailabilityResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AvailabilitySlotResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.bizco.server.catalog.domain.ServiceDefinition;
import com.bizco.server.catalog.infrastructure.ServiceDefinitionRepository;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.StaffProfileRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.scheduling.domain.Appointment;
import com.bizco.server.scheduling.domain.AppointmentStatus;
import com.bizco.server.scheduling.infrastructure.AppointmentRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import com.bizco.server.audit.service.AuditService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appointment scheduling service (DevelopmentPlan.md Week 9, StateMachines.md &sect;8,
 * ApiContracts.md &sect;27). Create/reschedule both derive {@code endAt}/{@code blockedUntilAt}
 * from the service's estimated duration plus the configured buffer, run an advisory overlap
 * pre-check for a fast, friendly error, and then rely on the
 * {@code ex_appointments_technician_overlap} GiST exclusion constraint as the transactionally
 * authoritative guard (DatabaseDesign.md &sect;39) - see {@link #persist}.
 */
@Service
public class AppointmentService {

    /** DatabaseDesign.md Section 39 / "Today's Appointments": business-local day boundary for availability slots. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServiceDefinitionRepository serviceDefinitionRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final UserRepository userRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final AuditService auditService;
    private final long bufferMinutes;

    public AppointmentService(final AppointmentRepository appointmentRepository,
                              final CustomerRepository customerRepository,
                              final ServiceDefinitionRepository serviceDefinitionRepository,
                              final StaffProfileRepository staffProfileRepository,
                              final UserRepository userRepository,
                              final DocumentSequenceRepository documentSequenceRepository,
                              final AuditService auditService,
                              @Value("${bizco.scheduling.appointment-buffer-minutes:15}") final long bufferMinutes) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.userRepository = userRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.auditService = auditService;
        this.bufferMinutes = bufferMinutes;
    }

    @Transactional
    public AppointmentResponse create(final CreateAppointmentRequest request, final Authentication authentication) {
        final UUID actor = actor(authentication);
        final Customer customer = requireCustomer(request.customerId());
        final ServiceDefinition service = requireActiveService(request.serviceId());
        if (request.technicianId() != null) {
            requireEligibleTechnician(request.technicianId());
        }
        if (request.startAt() == null) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "startAt is required");
        }
        final Instant startAt = request.startAt();
        final Instant endAt = endAt(startAt, service);
        final Instant blockedUntilAt = blockedUntilAt(endAt);
        precheckOverlap(request.technicianId(), startAt, blockedUntilAt, null);

        final LocalDate businessDate = LocalDate.now();
        final long next = documentSequenceRepository.nextDailyValue("APT", businessDate, "APT", 4);
        final String number = documentSequenceRepository.formatDaily("APT", businessDate, next, 4);
        final Appointment appointment = new Appointment(number, customer.getId(), service.getId(),
                request.technicianId(), startAt, endAt, blockedUntilAt, request.notes(), request.walkIn(), actor);
        final Appointment saved = persist(appointment);
        auditService.record("APPOINTMENT", saved.getId().toString(), "APPOINTMENT_CREATED", actor,
                Map.of("appointmentNumber", number));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentSearchResponse search(final Instant from, final Instant to, final UUID technicianId,
                                            final UUID customerId, final String status) {
        final AppointmentStatus statusFilter = status == null || status.isBlank() ? null
                : AppointmentStatus.valueOf(status.trim().toUpperCase());
        return new AppointmentSearchResponse(appointmentRepository
                .search(from, to, technicianId, customerId, statusFilter).stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(final UUID appointmentId) {
        return toResponse(load(appointmentId));
    }

    /**
     * ApiContracts.md &sect;27.2: best-effort free/occupied hint for the UI over the business
     * day's operating hours (09:00-17:00 local, 30-minute steps). The exclusion constraint is
     * still the only thing that can actually reject a conflicting create/reschedule.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(final UUID serviceId, final UUID technicianId, final LocalDate date) {
        final ServiceDefinition service = requireActiveService(serviceId);
        final ZonedDateTime dayStart = date.atStartOfDay(BUSINESS_ZONE).plusHours(9);
        final ZonedDateTime dayEnd = date.atStartOfDay(BUSINESS_ZONE).plusHours(17);
        final List<AvailabilitySlotResponse> slots = new ArrayList<>();
        for (ZonedDateTime slotStart = dayStart; slotStart.plusMinutes(30).compareTo(dayEnd) <= 0;
                slotStart = slotStart.plusMinutes(30)) {
            final Instant start = slotStart.toInstant();
            final Instant end = endAt(start, service);
            final boolean available = technicianId == null
                    || appointmentRepository.countOverlapping(technicianId, start, blockedUntilAt(end), null) == 0;
            slots.add(new AvailabilitySlotResponse(start, end, available));
        }
        return new AvailabilityResponse(slots);
    }

    @Transactional
    public AppointmentResponse reschedule(final UUID appointmentId, final RescheduleAppointmentRequest request,
                                          final Authentication authentication) {
        final Appointment appointment = load(appointmentId);
        assertVersion(appointment, request.version());
        final UUID serviceId = request.serviceId() != null ? request.serviceId() : appointment.getServiceId();
        final ServiceDefinition service = requireActiveService(serviceId);
        final UUID technicianId = request.technicianId();
        if (technicianId != null) {
            requireEligibleTechnician(technicianId);
        }
        if (request.startAt() == null) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "startAt is required");
        }
        final Instant startAt = request.startAt();
        final Instant endAt = endAt(startAt, service);
        final Instant blockedUntilAt = blockedUntilAt(endAt);
        precheckOverlap(technicianId, startAt, blockedUntilAt, appointment.getId());
        try {
            appointment.reschedule(serviceId, technicianId, startAt, endAt, blockedUntilAt, request.notes());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        }
        final Appointment saved = persist(appointment);
        auditService.record("APPOINTMENT", saved.getId().toString(), "APPOINTMENT_RESCHEDULED",
                actor(authentication), Map.of("appointmentNumber", saved.getAppointmentNumber()));
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse changeStatus(final UUID appointmentId, final AppointmentStatusRequest request,
                                            final Authentication authentication) {
        final Appointment appointment = load(appointmentId);
        assertVersion(appointment, request.version());
        final String target = request.targetStatus() == null ? "" : request.targetStatus().trim().toUpperCase();
        try {
            switch (target) {
                case "CONFIRMED" -> appointment.confirm();
                case "IN_PROGRESS" -> appointment.start();
                case "COMPLETED" -> appointment.complete();
                case "NO_SHOW" -> appointment.markNoShow(request.reason());
                case "CANCELLED" -> appointment.cancel(request.reason());
                default -> throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                        "Unknown target status: " + request.targetStatus());
            }
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        }
        appointmentRepository.flush();
        auditService.record("APPOINTMENT", appointment.getId().toString(), "APPOINTMENT_" + target,
                actor(authentication), Map.of("appointmentNumber", appointment.getAppointmentNumber()));
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(final UUID appointmentId, final String reason, final Authentication authentication) {
        final Appointment appointment = load(appointmentId);
        try {
            appointment.cancel(reason);
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        }
        appointmentRepository.flush();
        auditService.record("APPOINTMENT", appointment.getId().toString(), "APPOINTMENT_CANCELLED",
                actor(authentication), Map.of("appointmentNumber", appointment.getAppointmentNumber()));
        return toResponse(appointment);
    }

    /**
     * Persists inside the caller's transaction and forces an immediate flush so a
     * {@code ex_appointments_technician_overlap} violation surfaces here - as a clean 409 - rather
     * than at an unrelated later flush point or at commit.
     */
    private Appointment persist(final Appointment appointment) {
        try {
            final Appointment saved = appointmentRepository.save(appointment);
            appointmentRepository.flush();
            return saved;
        } catch (final DataIntegrityViolationException ex) {
            throw translateConflict(ex);
        }
    }

    private IdentityException translateConflict(final DataIntegrityViolationException ex) {
        final String message = ex.getMostSpecificCause() == null ? null : ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("ex_appointments_technician_overlap")) {
            return new IdentityException(ApiErrorCode.APPOINTMENT_CONFLICT, HttpStatus.CONFLICT,
                    "Technician already has a conflicting appointment in this time range");
        }
        return new IdentityException(ApiErrorCode.APPOINTMENT_CONFLICT, HttpStatus.CONFLICT,
                "Appointment could not be saved due to a conflicting booking");
    }

    private void precheckOverlap(final UUID technicianId, final Instant startAt, final Instant blockedUntilAt,
                                 final UUID excludeId) {
        if (technicianId == null) {
            return;
        }
        if (appointmentRepository.countOverlapping(technicianId, startAt, blockedUntilAt, excludeId) > 0) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_CONFLICT, HttpStatus.CONFLICT,
                    "Technician already has a conflicting appointment in this time range");
        }
    }

    private Instant endAt(final Instant startAt, final ServiceDefinition service) {
        return startAt.plus(service.getEstimatedDurationMinutes(), ChronoUnit.MINUTES);
    }

    private Instant blockedUntilAt(final Instant endAt) {
        return endAt.plus(bufferMinutes, ChronoUnit.MINUTES);
    }

    private Customer requireCustomer(final UUID customerId) {
        return customerRepository.findById(customerId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND, "Customer was not found"));
    }

    private ServiceDefinition requireActiveService(final UUID serviceId) {
        final ServiceDefinition service = serviceDefinitionRepository.findById(serviceId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Service was not found"));
        if (!service.isActive()) {
            throw new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND, HttpStatus.BAD_REQUEST,
                    "Service is not active");
        }
        return service;
    }

    private void requireEligibleTechnician(final UUID technicianId) {
        if (!staffProfileRepository.existsByUserIdAndTechnicianTrue(technicianId)) {
            throw new IdentityException(ApiErrorCode.APPOINTMENT_TECHNICIAN_INELIGIBLE, HttpStatus.BAD_REQUEST,
                    "Assigned user is not an eligible technician");
        }
    }

    private void assertVersion(final Appointment appointment, final long expectedVersion) {
        if (appointment.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Appointment was modified by another user");
        }
    }

    private Appointment load(final UUID appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.APPOINTMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Appointment was not found"));
    }

    private AppointmentResponse toResponse(final Appointment appointment) {
        final Customer customer = customerRepository.findById(appointment.getCustomerId()).orElse(null);
        final ServiceDefinition service = serviceDefinitionRepository.findById(appointment.getServiceId())
                .orElse(null);
        final User technician = appointment.getTechnicianId() == null ? null
                : userRepository.findById(appointment.getTechnicianId()).orElse(null);
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentNumber(),
                appointment.getCustomerId(), customer == null ? null : customer.getName(), appointment.getServiceId(),
                service == null ? null : service.getName(), appointment.getTechnicianId(),
                technician == null ? null : technician.getDisplayName(), appointment.getStartAt(),
                appointment.getEndAt(), appointment.getBlockedUntilAt(), appointment.getStatus().name(),
                appointment.getNotes(), appointment.isWalkIn(), appointment.getVersion(), appointment.getCreatedAt());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(User::getId).orElse(null);
    }
}

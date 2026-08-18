package com.bizco.server.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.StaffProfile;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.StaffProfileRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.scheduling.application.AppointmentService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/** DevelopmentPlan.md Week 9 task 9.8: SCH-APT, SCH-CONFLICT, SCH-STATE acceptance coverage. */
class AppointmentServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private StaffProfileRepository staffProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schApt001CreateAssignsScheduledStatusAndNumber() {
        final UUID customerId = createCustomer();
        final ServiceResponse service = createService(60);
        final UUID technicianId = createTechnician();
        final Instant startAt = futureSlot();

        final AppointmentResponse appointment = appointmentService.create(new CreateAppointmentRequest(customerId,
                service.serviceId(), technicianId, startAt, "first visit", false), auth());

        assertThat(appointment.appointmentNumber()).matches("APT-\\d{8}-\\d{4}");
        assertThat(appointment.status()).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action_code = 'APPOINTMENT_CREATED'",
                Long.class, appointment.appointmentId().toString())).isEqualTo(1L);
    }

    @Test
    void schApt002EndAndBufferAreDerivedFromServiceDuration() {
        final UUID customerId = createCustomer();
        final ServiceResponse service = createService(60);
        final Instant startAt = futureSlot();

        final AppointmentResponse appointment = appointmentService.create(
                new CreateAppointmentRequest(customerId, service.serviceId(), null, startAt, null, false), auth());

        assertThat(appointment.endAt()).isEqualTo(startAt.plus(60, ChronoUnit.MINUTES));
        assertThat(appointment.blockedUntilAt()).isEqualTo(startAt.plus(75, ChronoUnit.MINUTES));
    }

    @Test
    void schConflict001OverlappingAppointmentForSameTechnicianIsRejected() {
        final ServiceResponse service = createService(60);
        final UUID technicianId = createTechnician();
        final Instant startAt = futureSlot();
        appointmentService.create(new CreateAppointmentRequest(createCustomer(), service.serviceId(), technicianId,
                startAt, null, false), auth());

        assertThatThrownBy(() -> appointmentService.create(new CreateAppointmentRequest(createCustomer(),
                service.serviceId(), technicianId, startAt.plus(30, ChronoUnit.MINUTES), null, false), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.APPOINTMENT_CONFLICT);
    }

    @Test
    void schConflict003DifferentTechnicianSameTimeSucceeds() {
        final ServiceResponse service = createService(60);
        final Instant startAt = futureSlot();
        appointmentService.create(new CreateAppointmentRequest(createCustomer(), service.serviceId(),
                createTechnician(), startAt, null, false), auth());

        final AppointmentResponse second = appointmentService.create(new CreateAppointmentRequest(createCustomer(),
                service.serviceId(), createTechnician(), startAt, null, false), auth());

        assertThat(second.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void schConflict004CancelFreesTheSlotForAnotherBooking() {
        final ServiceResponse service = createService(60);
        final UUID technicianId = createTechnician();
        final Instant startAt = futureSlot();
        final AppointmentResponse first = appointmentService.create(new CreateAppointmentRequest(createCustomer(),
                service.serviceId(), technicianId, startAt, null, false), auth());

        appointmentService.cancel(first.appointmentId(), "customer requested", auth());
        final AppointmentResponse second = appointmentService.create(new CreateAppointmentRequest(createCustomer(),
                service.serviceId(), technicianId, startAt, null, false), auth());

        assertThat(second.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void schState001ScheduledToConfirmedSucceeds() {
        final AppointmentResponse appointment = createAppointment();

        final AppointmentResponse confirmed = appointmentService.changeStatus(appointment.appointmentId(),
                new AppointmentStatusRequest("CONFIRMED", null, appointment.version()), auth());

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    }

    /**
     * SCH-STATE-002 exercises "an invalid transition from a terminal appointment is rejected".
     * StateMachines.md &sect;8.10 has no command that returns a terminal appointment to SCHEDULED
     * at all (there is no such transition to attempt), so this drives the appointment to
     * COMPLETED and then attempts CONFIRMED instead - the same class of rejection the acceptance
     * ID is checking for.
     */
    @Test
    void schState002InvalidTransitionFromTerminalStateIsRejected() {
        final AppointmentResponse appointment = createAppointment();
        final AppointmentResponse confirmed = appointmentService.changeStatus(appointment.appointmentId(),
                new AppointmentStatusRequest("CONFIRMED", null, appointment.version()), auth());
        final AppointmentResponse started = appointmentService.changeStatus(appointment.appointmentId(),
                new AppointmentStatusRequest("IN_PROGRESS", null, confirmed.version()), auth());
        final AppointmentResponse completed = appointmentService.changeStatus(appointment.appointmentId(),
                new AppointmentStatusRequest("COMPLETED", null, started.version()), auth());

        assertThatThrownBy(() -> appointmentService.changeStatus(appointment.appointmentId(),
                new AppointmentStatusRequest("CONFIRMED", null, completed.version()), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.APPOINTMENT_INVALID_TRANSITION);
    }

    @Test
    void schResch001RescheduleUsesTheSameExclusionConstraint() {
        final ServiceResponse service = createService(60);
        final UUID technicianId = createTechnician();
        final Instant blockedSlot = futureSlot().plus(1, ChronoUnit.DAYS);
        appointmentService.create(new CreateAppointmentRequest(createCustomer(), service.serviceId(), technicianId,
                blockedSlot, null, false), auth());
        final AppointmentResponse appointment = createAppointment();

        assertThatThrownBy(() -> appointmentService.reschedule(appointment.appointmentId(),
                new RescheduleAppointmentRequest(service.serviceId(), technicianId, blockedSlot,
                        "moved", appointment.version()), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.APPOINTMENT_CONFLICT);
    }

    @Test
    void staleVersionRescheduleIsRejected() {
        final AppointmentResponse appointment = createAppointment();

        assertThatThrownBy(() -> appointmentService.reschedule(appointment.appointmentId(),
                new RescheduleAppointmentRequest(null, null, futureSlot().plus(2, ChronoUnit.DAYS), "late",
                        appointment.version() + 1), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void technicianMustBeEligibleToBeAssigned() {
        final UUID customerId = createCustomer();
        final ServiceResponse service = createService(60);
        final UUID notATechnician = createUser().getId();

        assertThatThrownBy(() -> appointmentService.create(new CreateAppointmentRequest(customerId,
                service.serviceId(), notATechnician, futureSlot(), null, false), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.APPOINTMENT_TECHNICIAN_INELIGIBLE);
    }

    private AppointmentResponse createAppointment() {
        return appointmentService.create(new CreateAppointmentRequest(createCustomer(), createService(60).serviceId(),
                createTechnician(), futureSlot(), null, false), auth());
    }

    private UUID createCustomer() {
        final String suffix = token();
        final CustomerDetailResponse customer = customerService.create(new CustomerCreateRequest("Appt Customer " + suffix,
                "0771234567", null, null, null, null, null, null, "RETAIL", BigDecimal.ZERO, false, false), auth());
        return customer.customerId();
    }

    private ServiceResponse createService(final int durationMinutes) {
        final String suffix = token();
        return catalogService.createService(new ServiceCreateRequest("SVC-" + suffix, "Appt Service " + suffix, null,
                null, new BigDecimal("500.00"), durationMinutes, false, 0), auth());
    }

    private UUID createTechnician() {
        final User user = createUser();
        staffProfileRepository.saveAndFlush(new StaffProfile(user.getId(), true, user.getDisplayName()));
        return user.getId();
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("staff_" + token(), "Staff " + token(), passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private Instant futureSlot() {
        return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** {@code created_by}/audit actor resolution requires a real, existing username (unlike the fake "superadmin" placeholder some other ITs use for calls that don't need an actor). */
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(createUser().getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("appointment.create")));
    }
}

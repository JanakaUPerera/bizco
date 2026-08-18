package com.bizco.server.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCH-CONFLICT-002 (P0): DatabaseDesign.md &sect;39 requires the
 * {@code ex_appointments_technician_overlap} GiST exclusion constraint - not any application-level
 * pre-check - to be the sole authority preventing double-booking when two clients race for the
 * same technician/slot. Mirrors
 * {@code DocumentSequenceRepositoryPostgresIT.concurrentAllocationsOnTheSameDayNeverCollide}: each
 * attempt runs in its own thread and its own database transaction via {@link TransactionTemplate}.
 */
class AppointmentConcurrencyIT extends PostgresIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    @Test
    void schConflict002ExactlyOneConcurrentOverlappingCreateCommits() throws Exception {
        final int attempts = 10;
        final ServiceResponse service = inTransaction(() -> createService(60));
        final UUID technicianId = inTransaction(this::createTechnician);
        final List<UUID> customerIds = IntStream.range(0, attempts)
                .mapToObj(i -> inTransaction(this::createCustomer)).toList();
        final Instant startAt = futureSlot();

        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Callable<Object>> tasks = IntStream.range(0, attempts)
                    .<Callable<Object>>mapToObj(i -> () -> attempt(customerIds.get(i), service.serviceId(),
                            technicianId, startAt))
                    .toList();
            final List<Object> results = pool.invokeAll(tasks).stream().map(this::get).toList();

            final long succeeded = results.stream().filter(AppointmentResponse.class::isInstance).count();
            final long conflicted = results.stream().filter(IdentityException.class::isInstance)
                    .map(IdentityException.class::cast)
                    .filter(ex -> ex.getCode() == ApiErrorCode.APPOINTMENT_CONFLICT).count();

            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicted).isEqualTo(attempts - 1);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Object attempt(final UUID customerId, final UUID serviceId, final UUID technicianId, final Instant startAt) {
        try {
            return inTransaction(() -> appointmentService.create(
                    new CreateAppointmentRequest(customerId, serviceId, technicianId, startAt, null, false), auth()));
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private ServiceResponse createService(final int durationMinutes) {
        final String suffix = token();
        return catalogService.createService(new ServiceCreateRequest("SVC-" + suffix, "Concurrency Service " + suffix,
                null, null, new BigDecimal("500.00"), durationMinutes, false, 0), auth());
    }

    private UUID createTechnician() {
        final User user = createUser();
        staffProfileRepository.saveAndFlush(new StaffProfile(user.getId(), true, user.getDisplayName()));
        return user.getId();
    }

    private UUID createCustomer() {
        final String suffix = token();
        return customerService.create(new CustomerCreateRequest("Concurrency Customer " + suffix, "0771234567", null,
                null, null, null, null, null, "RETAIL", BigDecimal.ZERO, false, false), auth()).customerId();
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("conc_" + token(), "Concurrency Staff " + token(),
                passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private Instant futureSlot() {
        return Instant.now().plus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** {@code created_by} is NOT NULL, so - unlike ITs that only need a permission string - the actor username must resolve to a real user. */
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(createUser().getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("appointment.create")));
    }

    private <T> T inTransaction(final Supplier<T> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private <T> T get(final Future<T> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

package com.bizco.server.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobServiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.ConvertAppointmentRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateEstimateRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.EstimateResponseRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.GenerateServiceInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceStatusRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.StaffProfile;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.StaffProfileRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.scheduling.application.AppointmentService;
import com.bizco.server.scheduling.application.JobCardService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * DevelopmentPlan.md Week 11 task 11.10: JOB-CONV, JOB-EST, JOB-STATE, JOB-CANCEL, JOB-PART
 * acceptance coverage, plus the SC-03 Repair Centre full scenario.
 *
 * <p>JOB-PART-001/002's literal "available stock becomes 4" assertions are not testable yet - the
 * stock ledger (Phase 5/Week 12) does not exist. See {@code JobCardService}'s and
 * {@code JobPart}'s Javadoc for the documented gap (same shape as {@code HeldSale}'s). This class
 * asserts what is actually true today: the part persists with correct snapshots and the add is
 * idempotent.
 */
class JobCardServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private JobCardService jobCardService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private PostSaleService postSaleService;
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
    void jobConv001ConvertCreatesOneJobLinkedToAppointment() {
        final AppointmentResponse appointment = createAppointment(createService(60, false, 0));

        final JobCardResponse job = convert(appointment.appointmentId());

        assertThat(job.jobNumber()).matches("JOB-\\d{8}-\\d{4}");
        assertThat(job.appointmentId()).isEqualTo(appointment.appointmentId());
        assertThat(job.customerId()).isEqualTo(appointment.customerId());
        assertThat(jdbc.queryForObject("select count(*) from job_cards where appointment_id = ?", Long.class,
                appointment.appointmentId())).isEqualTo(1L);
    }

    @Test
    void jobConv002DuplicateConversionDoesNotCreateSecondJob() {
        final AppointmentResponse appointment = createAppointment(createService(60, false, 0));

        final JobCardResponse first = jobCardService.convertFromAppointment(UUID.randomUUID(),
                appointment.appointmentId(), new ConvertAppointmentRequest(null, null, null, null, null, null, null),
                auth()).response();
        final JobCardResponse second = jobCardService.convertFromAppointment(UUID.randomUUID(),
                appointment.appointmentId(), new ConvertAppointmentRequest(null, null, null, null, null, null, null),
                auth()).response();

        assertThat(second.jobCardId()).isEqualTo(first.jobCardId());
        assertThat(jdbc.queryForObject("select count(*) from job_cards where appointment_id = ?", Long.class,
                appointment.appointmentId())).isEqualTo(1L);
    }

    @Test
    void jobConv003IdempotentConversionRetryReturnsSameJob() {
        final AppointmentResponse appointment = createAppointment(createService(60, false, 0));
        final UUID idempotencyKey = UUID.randomUUID();
        final ConvertAppointmentRequest request = new ConvertAppointmentRequest(null, null, null, null, null, null, null);

        final var first = jobCardService.convertFromAppointment(idempotencyKey, appointment.appointmentId(), request, auth());
        final var second = jobCardService.convertFromAppointment(idempotencyKey, appointment.appointmentId(), request, auth());

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().jobCardId()).isEqualTo(first.response().jobCardId());
    }

    @Test
    void jobEst001EstimateRequiredBlocksStartOfWork() {
        final UUID customerId = createCustomer();
        final ServiceResponse service = createService(60, true, 0);
        final JobCardResponse job = createWalkInWithService(customerId, service);

        assertThat(job.status()).isEqualTo("ESTIMATE_PENDING");
        assertThatThrownBy(() -> jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, job.version()), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.JOB_ESTIMATE_REQUIRED);
    }

    @Test
    void jobEst002CreateEstimateIncrementsVersionAndKeepsOriginal() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, true, 0));

        final JobCardResponse afterFirst = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("5000.00"), "Initial", null), auth());
        final JobCardResponse afterSecond = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("7500.00"), "Revised", null), auth());

        assertThat(afterFirst.estimates()).hasSize(1);
        assertThat(afterFirst.estimates().get(0).estimateVersion()).isEqualTo(1);
        assertThat(afterSecond.estimates()).hasSize(2);
        assertThat(afterSecond.estimates()).extracting("estimateVersion").containsExactlyInAnyOrder(1, 2);
        final var original = afterSecond.estimates().stream().filter(e -> e.estimateVersion() == 1).findFirst().orElseThrow();
        assertThat(original.estimatedTotal()).isEqualByComparingTo("5000.00");
        assertThat(original.customerResponse()).isEqualTo("PENDING");
    }

    @Test
    void jobEst003AcceptMovesJobToEstimateApproved() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, true, 0));
        final JobCardResponse withEstimate = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("5000.00"), null, null), auth());
        final UUID estimateId = withEstimate.estimates().get(0).jobEstimateId();

        final JobCardResponse accepted = jobCardService.acceptEstimate(job.jobCardId(), estimateId,
                new EstimateResponseRequest("Customer approved by phone"), auth());

        assertThat(accepted.estimates().get(0).customerResponse()).isEqualTo("ACCEPTED");
        assertThat(accepted.status()).isEqualTo("ESTIMATE_APPROVED");
    }

    @Test
    void jobEst004DeclineFollowsCancellationRule() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, true, 0));
        final JobCardResponse withEstimate = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("5000.00"), null, null), auth());
        final UUID estimateId = withEstimate.estimates().get(0).jobEstimateId();

        final JobCardResponse declined = jobCardService.declineEstimate(job.jobCardId(), estimateId,
                new EstimateResponseRequest("Too expensive"), auth());

        assertThat(declined.estimates().get(0).customerResponse()).isEqualTo("DECLINED");
        assertThat(declined.status()).isEqualTo("CANCELLED");
    }

    @Test
    void jobState002EstimateApprovedToInProgressSucceedsWithAcceptedEstimate() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, true, 0));
        final JobCardResponse withEstimate = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("5000.00"), null, null), auth());
        final JobCardResponse approved = jobCardService.acceptEstimate(job.jobCardId(),
                withEstimate.estimates().get(0).jobEstimateId(), new EstimateResponseRequest(null), auth());

        final JobCardResponse started = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, approved.version()), auth());

        assertThat(started.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void jobState003ReadyForPickupRequiresAllServicesCompleted() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));
        final JobCardResponse started = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, job.version()), auth());

        assertThatThrownBy(() -> jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("READY_FOR_PICKUP", null, false, started.version()), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.DOMAIN_RULE_REJECTED);

        jobCardService.changeServiceStatus(job.jobCardId(), started.services().get(0).jobServiceId(),
                new JobServiceStatusRequest("IN_PROGRESS", null), auth());
        final JobCardResponse withCompletedService = jobCardService.changeServiceStatus(job.jobCardId(),
                started.services().get(0).jobServiceId(), new JobServiceStatusRequest("COMPLETED", new BigDecimal("500.00")),
                auth());
        final JobCardResponse ready = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("READY_FOR_PICKUP", null, false, withCompletedService.version()), auth());

        assertThat(ready.status()).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    void jobState004ReadyToCompletedSetsPickupAndWarrantyDates() {
        final ServiceResponse service = createService(60, false, 14);
        final JobCardResponse ready = driveToReadyForPickup(createWalkInWithService(createCustomer(), service));

        final JobCardResponse completed = jobCardService.changeStatus(ready.jobCardId(),
                new JobCardStatusRequest("COMPLETED", null, false, ready.version()), auth());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.pickupDate()).isEqualTo(LocalDate.now());
        assertThat(completed.warrantyEndDate()).isEqualTo(LocalDate.now().plusDays(14));
    }

    @Test
    void jobState005DirectCompletionFromInProgressIsRejected() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));
        final JobCardResponse started = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, job.version()), auth());
        jobCardService.changeServiceStatus(job.jobCardId(), started.services().get(0).jobServiceId(),
                new JobServiceStatusRequest("IN_PROGRESS", null), auth());
        final JobCardResponse withCompletedService = jobCardService.changeServiceStatus(job.jobCardId(),
                started.services().get(0).jobServiceId(), new JobServiceStatusRequest("COMPLETED", new BigDecimal("500.00")),
                auth());

        assertThatThrownBy(() -> jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("COMPLETED", null, false, withCompletedService.version()), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.JOB_CARD_INVALID_TRANSITION);
    }

    @Test
    void jobCancel001ReasonIsRequired() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));

        assertThatThrownBy(() -> jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("CANCELLED", "", false, job.version()), auth()))
                .isInstanceOf(com.bizco.server.identity.service.ApiValidationException.class);
    }

    @Test
    void jobPart001ConsumePartPersistsSnapshots() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));
        final ProductDetailResponse product = createProduct();

        final var result = jobCardService.addPart(UUID.randomUUID(), job.jobCardId(),
                new AddJobPartRequest(product.productId(), new BigDecimal("1.000"), null, false), auth());

        assertThat(result.response().productId()).isEqualTo(product.productId());
        assertThat(result.response().costPriceSnapshot()).isEqualByComparingTo(product.costPrice());
        assertThat(result.response().unitPriceSnapshot()).isEqualByComparingTo(product.sellingPrice());
    }

    @Test
    void jobPart003RetryWithSameIdempotencyKeyCreatesNoSecondPart() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));
        final ProductDetailResponse product = createProduct();
        final UUID idempotencyKey = UUID.randomUUID();
        final AddJobPartRequest request = new AddJobPartRequest(product.productId(), new BigDecimal("1.000"), null, false);

        final var first = jobCardService.addPart(idempotencyKey, job.jobCardId(), request, auth());
        final var second = jobCardService.addPart(idempotencyKey, job.jobCardId(), request, auth());

        assertThat(second.response().jobPartId()).isEqualTo(first.response().jobPartId());
        assertThat(jdbc.queryForObject("select count(*) from job_parts where job_card_id = ?", Long.class,
                job.jobCardId())).isEqualTo(1L);
    }

    @Test
    void jobPart004GeneratedInvoiceLineCarriesSourceJobPartMarker() {
        final JobCardResponse job = createWalkInWithService(createCustomer(), createService(60, false, 0));
        final ProductDetailResponse product = createProduct();
        final var part = jobCardService.addPart(UUID.randomUUID(), job.jobCardId(),
                new AddJobPartRequest(product.productId(), new BigDecimal("1.000"), null, false), auth()).response();

        final InvoiceDetailResponse invoice = jobCardService.generateServiceInvoice(job.jobCardId(),
                new GenerateServiceInvoiceRequest(false, true, List.of()), auth());

        assertThat(invoice.status()).isEqualTo("DRAFT");
        assertThat(invoice.lines()).hasSize(1);
        assertThat(invoice.lines().get(0).sourceJobPartId()).isEqualTo(part.jobPartId());
        // No stock_movements table exists yet to assert "no double deduction" against (Phase 5
        // gap, see class Javadoc) - the marker itself is what this test proves is in place.
    }

    @Test
    void sc03001FullRepairScenarioAppointmentThroughInvoiceAndPickup() {
        final ServiceResponse service = createService(90, true, 30);
        final AppointmentResponse appointment = createAppointment(service);

        final JobCardResponse job = convert(appointment.appointmentId());
        assertThat(job.status()).isEqualTo("ESTIMATE_PENDING");

        final JobCardResponse withEstimate = jobCardService.createEstimate(job.jobCardId(),
                new CreateEstimateRequest(new BigDecimal("18500.00"), "Display replacement plus labour", null), auth());
        final JobCardResponse approved = jobCardService.acceptEstimate(job.jobCardId(),
                withEstimate.estimates().get(0).jobEstimateId(), new EstimateResponseRequest("Approved"), auth());
        assertThat(approved.status()).isEqualTo("ESTIMATE_APPROVED");

        final JobCardResponse started = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, approved.version()), auth());

        final ProductDetailResponse part = createProduct();
        jobCardService.addPart(UUID.randomUUID(), job.jobCardId(),
                new AddJobPartRequest(part.productId(), new BigDecimal("1.000"), null, false), auth());

        jobCardService.changeServiceStatus(job.jobCardId(), started.services().get(0).jobServiceId(),
                new JobServiceStatusRequest("IN_PROGRESS", null), auth());
        final JobCardResponse withCompletedService = jobCardService.changeServiceStatus(job.jobCardId(),
                started.services().get(0).jobServiceId(),
                new JobServiceStatusRequest("COMPLETED", new BigDecimal("15000.00")), auth());

        final JobCardResponse ready = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("READY_FOR_PICKUP", null, false, withCompletedService.version()), auth());

        final InvoiceDetailResponse draft = jobCardService.generateServiceInvoice(job.jobCardId(),
                new GenerateServiceInvoiceRequest(true, true, List.of()), auth());
        assertThat(draft.lines()).hasSize(2);

        postSaleService.post(draft.invoiceId(), UUID.randomUUID(), new PostInvoiceRequest(draft.version(),
                List.of(new PaymentLineRequest("CASH", draft.totalAmount(), null)), false, List.of()), auth());

        final JobCardResponse readyReloaded = jobCardService.get(job.jobCardId());
        final JobCardResponse completed = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("COMPLETED", null, false, readyReloaded.version()), auth());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.pickupDate()).isEqualTo(LocalDate.now());
        assertThat(completed.warrantyEndDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(ready.status()).isEqualTo("READY_FOR_PICKUP");
    }

    private JobCardResponse driveToReadyForPickup(final JobCardResponse job) {
        final JobCardResponse started = jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("IN_PROGRESS", null, false, job.version()), auth());
        jobCardService.changeServiceStatus(job.jobCardId(), started.services().get(0).jobServiceId(),
                new JobServiceStatusRequest("IN_PROGRESS", null), auth());
        final JobCardResponse withCompletedService = jobCardService.changeServiceStatus(job.jobCardId(),
                started.services().get(0).jobServiceId(), new JobServiceStatusRequest("COMPLETED", new BigDecimal("500.00")),
                auth());
        return jobCardService.changeStatus(job.jobCardId(),
                new JobCardStatusRequest("READY_FOR_PICKUP", null, false, withCompletedService.version()), auth());
    }

    private JobCardResponse createWalkInWithService(final UUID customerId, final ServiceResponse service) {
        final JobCardResponse job = jobCardService.createWalkIn(new CreateJobCardRequest(customerId, null, "Phone",
                "Samsung", "A54", "SN" + token(), "Broken display", null, null, null), auth());
        return jobCardService.addService(job.jobCardId(),
                new AddJobServiceRequest(service.serviceId(), service.basePrice(), service.estimatedDurationMinutes(), null),
                auth());
    }

    private JobCardResponse convert(final UUID appointmentId) {
        return jobCardService.convertFromAppointment(UUID.randomUUID(), appointmentId,
                new ConvertAppointmentRequest("Phone", "Samsung", "A54", "SN" + token(), "Broken display", "Phone only", null),
                auth()).response();
    }

    private AppointmentResponse createAppointment(final ServiceResponse service) {
        return appointmentService.create(new CreateAppointmentRequest(createCustomer(), service.serviceId(),
                createTechnician(), futureSlot(), null, false), auth());
    }

    private UUID createCustomer() {
        final String suffix = token();
        return customerService.create(new CustomerCreateRequest("Job Customer " + suffix, "0771234567", null, null,
                null, null, null, null, "RETAIL", BigDecimal.ZERO, false, false), auth()).customerId();
    }

    private ServiceResponse createService(final int durationMinutes, final boolean requiresEstimate,
                                          final int warrantyDays) {
        final String suffix = token();
        return catalogService.createService(new ServiceCreateRequest("SVC-" + suffix, "Repair Service " + suffix, null,
                null, new BigDecimal("500.00"), durationMinutes, requiresEstimate, warrantyDays), auth());
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(
                new com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest("Job Part " + suffix, null, null), auth());
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("JP-" + suffix, null, "Replacement Screen", null,
                category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("3000.00"), new BigDecimal("6000.00"),
                null, new BigDecimal("5.000"), null), auth());
    }

    private UUID createTechnician() {
        final User user = createUser();
        staffProfileRepository.saveAndFlush(new StaffProfile(user.getId(), true, user.getDisplayName()));
        return user.getId();
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("job_" + token(), "Job Staff " + token(), passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private Instant futureSlot() {
        return Instant.now().plus(21, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(createUser().getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("jobcard.create"), new SimpleGrantedAuthority("jobcard.update"),
                        new SimpleGrantedAuthority("jobcard.status_change"), new SimpleGrantedAuthority("jobcard.complete"),
                        new SimpleGrantedAuthority("jobcard.parts.add"), new SimpleGrantedAuthority("jobcard.estimate.create"),
                        new SimpleGrantedAuthority("jobcard.estimate.approve"),
                        new SimpleGrantedAuthority("appointment.convert_to_job"), new SimpleGrantedAuthority("invoice.create")));
    }
}

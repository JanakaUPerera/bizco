package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.sales.application.SalesApprovalService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class PostSaleServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private SalesApprovalService salesApprovalService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void salePost001FullyPaidCashSaleReachesPaidStatusAndAllocatesNumber() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "1000.00");

        final PostInvoiceResponse posted = post(draft.invoiceId(), draft.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("1000.00"), null)), false, List.of()).response();

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.invoiceNumber()).matches("INV-\\d{8}-\\d{4}");
        assertThat(posted.paymentStatus()).isEqualTo("PAID");
        assertThat(posted.amountPaid()).isEqualByComparingTo("1000.00");
        assertThat(posted.balanceDue()).isEqualByComparingTo("0.00");

        assertThat(jdbc.queryForObject("select count(*) from customer_payments where customer_payment_id in "
                + "(select customer_payment_id from customer_payment_allocations where invoice_id = ?)",
                Long.class, posted.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from cashbook_entries where source_type = 'CUSTOMER_PAYMENT' "
                + "and direction = 'IN'", Long.class)).isGreaterThanOrEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action_code = 'INVOICE_POSTED'",
                Long.class, posted.invoiceId().toString())).isEqualTo(1L);
    }

    @Test
    void salePost002SplitPaymentAcrossTwoMethodsSumsToTotal() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "8000.00");

        final PostInvoiceResponse posted = post(draft.invoiceId(), draft.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("5000.00"), null),
                        new PaymentLineRequest("CARD", new BigDecimal("3000.00"), "AUTH-123")),
                false, List.of()).response();

        assertThat(posted.paymentStatus()).isEqualTo("PAID");
        assertThat(posted.amountPaid()).isEqualByComparingTo("8000.00");
        final Long paymentRows = jdbc.queryForObject("""
                select count(*) from customer_payment_allocations where invoice_id = ?
                """, Long.class, posted.invoiceId());
        assertThat(paymentRows).isEqualTo(2L);
    }

    @Test
    void salePost003ImmediateSaleIncompletePaymentIsRejected() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "10000.00");

        assertThatThrownBy(() -> post(draft.invoiceId(), draft.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("3000.00"), null)), false, List.of()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_PAYMENT_REQUIRED);

        assertThat(invoiceService.get(draft.invoiceId()).status()).isEqualTo("DRAFT");
    }

    @Test
    void salePost004ValidCreditSaleLeavesPartialBalanceAndIncreasesReceivable() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final CustomerDetailResponse customer = createCustomer(cashier, new BigDecimal("50000"));
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, customer.customerId(), "10000.00");

        final PostInvoiceResponse posted = post(draft.invoiceId(), draft.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("3000.00"), null)), true, List.of()).response();

        assertThat(posted.amountPaid()).isEqualByComparingTo("3000.00");
        assertThat(posted.balanceDue()).isEqualByComparingTo("7000.00");
        assertThat(posted.paymentStatus()).isEqualTo("PARTIAL");
        assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo("7000.00");
    }

    @Test
    void salePost005ZeroPaymentCreditSalePostsAsUnpaid() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final CustomerDetailResponse customer = createCustomer(cashier, new BigDecimal("50000"));
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, customer.customerId(), "10000.00");

        final PostInvoiceResponse posted = post(draft.invoiceId(), draft.version(), cashier,
                List.of(), true, List.of()).response();

        assertThat(posted.paymentStatus()).isEqualTo("UNPAID");
        assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo("10000.00");
    }

    @Test
    void salePost006CreditSaleRequiresCustomer() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "5000.00");

        assertThatThrownBy(() -> post(draft.invoiceId(), draft.version(), cashier, List.of(), true, List.of()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_CREDIT_CUSTOMER_REQUIRED);
    }

    @Test
    void crd005CreditLimitExceededIsRejected() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final CustomerDetailResponse customer = createCustomer(cashier, new BigDecimal("100000"));

        final InvoiceSummaryResponse first = createDraftWithCustomLine(cashier, customer.customerId(), "95000.00");
        post(first.invoiceId(), first.version(), cashier, List.of(), true, List.of());

        final InvoiceSummaryResponse second = createDraftWithCustomLine(cashier, customer.customerId(), "10000.00");
        assertThatThrownBy(() -> post(second.invoiceId(), second.version(), cashier, List.of(), true, List.of()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CUSTOMER_CREDIT_LIMIT_EXCEEDED);
    }

    @Test
    void saleDisc002DiscountOver10PercentRequiresApprovalThenSucceedsWithOne() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final User manager = createUser("manager_" + token(), "MANAGER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "1000.00");
        final InvoiceSummaryResponse discounted = applyInvoiceDiscount(draft.invoiceId(), draft.version(), "20");

        assertThatThrownBy(() -> post(discounted.invoiceId(), discounted.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("800.00"), null)), false, List.of()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_DISCOUNT_APPROVAL_REQUIRED);

        final SalesApprovalResponse approval = salesApprovalService.requestApproval(discounted.invoiceId(),
                new SalesApprovalRequest("DISCOUNT_10_25", null, new BigDecimal("20"), "Loyal customer",
                        manager.getUsername(), "Correct1!"), auth(cashier));

        final PostInvoiceResponse posted = post(discounted.invoiceId(), discounted.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("800.00"), null)), false,
                List.of(approval.approvalId())).response();

        assertThat(posted.status()).isEqualTo("POSTED");
    }

    @Test
    void saleDisc003ApprovalBoundToADifferentInvoiceDoesNotSatisfyTheCheck() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final User manager = createUser("manager_" + token(), "MANAGER");
        final InvoiceSummaryResponse otherDraft = createDraftWithCustomLine(cashier, null, "500.00");
        final SalesApprovalResponse approvalForOtherInvoice = salesApprovalService.requestApproval(otherDraft.invoiceId(),
                new SalesApprovalRequest("DISCOUNT_10_25", null, new BigDecimal("20"), "reason",
                        manager.getUsername(), "Correct1!"), auth(cashier));

        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "1000.00");
        final InvoiceSummaryResponse discounted = applyInvoiceDiscount(draft.invoiceId(), draft.version(), "20");

        assertThatThrownBy(() -> post(discounted.invoiceId(), discounted.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("800.00"), null)), false,
                List.of(approvalForOtherInvoice.approvalId())))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_DISCOUNT_APPROVAL_REQUIRED);
    }

    @Test
    void sysIdem001ExactRetryReplaysWithoutDoublePosting() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "1000.00");
        final UUID idempotencyKey = UUID.randomUUID();
        final PostInvoiceRequest request = new PostInvoiceRequest(draft.version(),
                List.of(new PaymentLineRequest("CASH", new BigDecimal("1000.00"), null)), false, List.of());

        final IdempotentResult<PostInvoiceResponse> first = postSaleService.post(draft.invoiceId(), idempotencyKey,
                request, auth(cashier));
        final IdempotentResult<PostInvoiceResponse> second = postSaleService.post(draft.invoiceId(), idempotencyKey,
                request, auth(cashier));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().invoiceNumber()).isEqualTo(first.response().invoiceNumber());
        assertThat(jdbc.queryForObject("select count(*) from invoices where invoice_number = ?", Long.class,
                first.response().invoiceNumber())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations where invoice_id = ?",
                Long.class, draft.invoiceId())).isEqualTo(1L);
    }

    @Test
    void postingAnAlreadyPostedInvoiceIsRejected() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = createDraftWithCustomLine(cashier, null, "1000.00");
        final PostInvoiceResponse posted = post(draft.invoiceId(), draft.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("1000.00"), null)), false, List.of()).response();

        assertThatThrownBy(() -> post(draft.invoiceId(), posted.version(), cashier,
                List.of(new PaymentLineRequest("CASH", new BigDecimal("1000.00"), null)), false, List.of()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_NOT_DRAFT);
    }

    /**
     * CRD-CON-001: two clients concurrently post credit invoices that together would exceed
     * remaining capacity. The pessimistic customer lock in PostSaleService must serialize them so
     * exactly one succeeds.
     */
    @Test
    void crdCon001ConcurrentCreditPostingsAgainstTheSameCustomerOnlyAllowOneToSucceed() throws Exception {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final CustomerDetailResponse customer = createCustomer(cashier, new BigDecimal("10000"));
        final InvoiceSummaryResponse draftA = createDraftWithCustomLine(cashier, customer.customerId(), "8000.00");
        final InvoiceSummaryResponse draftB = createDraftWithCustomLine(cashier, customer.customerId(), "8000.00");

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Callable<Object> taskA = () -> postSaleService.post(draftA.invoiceId(), UUID.randomUUID(),
                    new PostInvoiceRequest(draftA.version(), List.of(), true, List.of()), auth(cashier));
            final Callable<Object> taskB = () -> postSaleService.post(draftB.invoiceId(), UUID.randomUUID(),
                    new PostInvoiceRequest(draftB.version(), List.of(), true, List.of()), auth(cashier));

            final List<Future<Object>> futures = pool.invokeAll(List.of(taskA, taskB), 30, TimeUnit.SECONDS);
            int succeeded = 0;
            int rejectedForLimit = 0;
            for (final Future<Object> future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (final java.util.concurrent.ExecutionException executionException) {
                    if (executionException.getCause() instanceof IdentityException identityException
                            && identityException.getCode() == ApiErrorCode.CUSTOMER_CREDIT_LIMIT_EXCEEDED) {
                        rejectedForLimit++;
                    } else {
                        throw executionException;
                    }
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(rejectedForLimit).isEqualTo(1);
            assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo("8000.00");
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private BigDecimal outstandingReceivable(final UUID customerId) {
        final List<BigDecimal> rows = jdbc.query(
                "select outstanding_receivable from v_customer_receivables where customer_id = ?",
                (rs, rowNum) -> rs.getBigDecimal("outstanding_receivable"), customerId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private IdempotentResult<PostInvoiceResponse> post(final UUID invoiceId, final long version, final User actor,
                                                        final List<PaymentLineRequest> payments,
                                                        final boolean creditSale, final List<UUID> approvalIds) {
        return postSaleService.post(invoiceId, UUID.randomUUID(),
                new PostInvoiceRequest(version, payments, creditSale, approvalIds), auth(actor));
    }

    private InvoiceSummaryResponse createDraftWithCustomLine(final User cashier, final UUID customerId,
                                                              final String unitPrice) {
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", customerId, null), auth(cashier));
        final var afterLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("CUSTOM", null, null,
                "Service fee", BigDecimal.ONE, new BigDecimal(unitPrice), "EXEMPT", DiscountRequest.NONE));
        return toSummary(afterLine);
    }

    private InvoiceSummaryResponse applyInvoiceDiscount(final UUID invoiceId, final long version, final String percent) {
        final var updated = invoiceService.updateHeader(invoiceId,
                new com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest(LocalDate.now(), null, "SALES",
                        null, new DiscountRequest("PERCENTAGE", new BigDecimal(percent)), null, version));
        return toSummary(updated);
    }

    private InvoiceSummaryResponse toSummary(final com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse detail) {
        return new InvoiceSummaryResponse(detail.invoiceId(), detail.invoiceNumber(), detail.invoiceDate(),
                detail.status(), detail.customerId(), detail.totalAmount(), detail.version());
    }

    private CustomerDetailResponse createCustomer(final User actor, final BigDecimal creditLimit) {
        return customerService.create(new CustomerCreateRequest("Credit Customer " + token(), "0771234567", null,
                null, null, null, null, null, "CORPORATE", creditLimit, false, false), auth(actor));
    }

    private User createUser(final String username, final String roleCode) {
        final Role role = roleRepository.findByCode(roleCode).orElseThrow();
        final User user = new User(username, username, passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("invoice.create")));
    }
}

package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.PaymentAllocationRequest;
import com.bizco.common.dto.finance.PaymentDtos.RecordCustomerPaymentRequest;
import com.bizco.common.dto.finance.PaymentDtos.RecordInvoicePaymentRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.PaymentAllocationService;
import com.bizco.server.sales.application.PostSaleService;
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

class PaymentAllocationServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private PaymentAllocationService paymentAllocationService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
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
    void finArPay001LaterPartialPaymentReducesBalanceAndPostsCashbookIn() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "10000.00");

        final CustomerPaymentResponse payment = paymentAllocationService.recordForInvoice(posted.invoiceId(),
                UUID.randomUUID(), new RecordInvoicePaymentRequest(null, "CASH", new BigDecimal("4000.00"), null, null),
                auth(cashier)).response();

        assertThat(payment.allocations()).hasSize(1);
        assertThat(payment.allocations().get(0).invoiceBalanceAfter()).isEqualByComparingTo("6000.00");
        assertThat(paymentStatus(posted.invoiceId())).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("select count(*) from cashbook_entries where source_type = 'CUSTOMER_PAYMENT' "
                + "and amount = 4000.00 and direction = 'IN'", Long.class)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void finArPay002FinalPaymentReachesPaid() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "6000.00");

        paymentAllocationService.recordForInvoice(posted.invoiceId(), UUID.randomUUID(),
                new RecordInvoicePaymentRequest(null, "CASH", new BigDecimal("6000.00"), null, null), auth(cashier));

        assertThat(paymentStatus(posted.invoiceId())).isEqualTo("PAID");
    }

    @Test
    void finArPay003MultiInvoicePaymentSettlesBothInvoicesWithOnePaymentRow() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse invoiceA = creditSale(cashier, customer.customerId(), "20000.00");
        final PostInvoiceResponse invoiceB = creditSale(cashier, customer.customerId(), "30000.00");

        final CustomerPaymentResponse payment = paymentAllocationService.recordForCustomer(UUID.randomUUID(),
                new RecordCustomerPaymentRequest(customer.customerId(), null, "BANK_TRANSFER", new BigDecimal("50000.00"),
                        "TRX-1", null, List.of(new PaymentAllocationRequest(invoiceA.invoiceId(), new BigDecimal("20000.00")),
                                new PaymentAllocationRequest(invoiceB.invoiceId(), new BigDecimal("30000.00")))),
                auth(cashier)).response();

        assertThat(payment.allocations()).hasSize(2);
        assertThat(paymentStatus(invoiceA.invoiceId())).isEqualTo("PAID");
        assertThat(paymentStatus(invoiceB.invoiceId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from customer_payments where customer_payment_id = ?", Long.class,
                payment.customerPaymentId())).isEqualTo(1L);
        final BigDecimal allocatedTotal = payment.allocations().stream().map(a -> a.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocatedTotal).isEqualByComparingTo("50000.00");
    }

    @Test
    void finArPay004OverAllocationIsRejectedWithNoPartialPosting() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "5000.00");

        assertThatThrownBy(() -> paymentAllocationService.recordForInvoice(posted.invoiceId(), UUID.randomUUID(),
                new RecordInvoicePaymentRequest(null, "CASH", new BigDecimal("6000.00"), null, null), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE);

        assertThat(paymentStatus(posted.invoiceId())).isEqualTo("UNPAID");
        assertThat(jdbc.queryForObject("select count(*) from customer_payment_allocations where invoice_id = ?", Long.class,
                posted.invoiceId())).isEqualTo(0L);
    }

    /** FIN-AR-CON-001: two concurrent 4000 allocations against a 5000 balance - only one may commit. */
    @Test
    void finArCon001ConcurrentAllocationNeverExceedsBalance() throws Exception {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "5000.00");

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Callable<Object> task = () -> paymentAllocationService.recordForInvoice(posted.invoiceId(),
                    UUID.randomUUID(), new RecordInvoicePaymentRequest(null, "CASH", new BigDecimal("4000.00"), null, null),
                    auth(cashier));
            final List<Future<Object>> futures = pool.invokeAll(List.of(task, task), 30, TimeUnit.SECONDS);
            int succeeded = 0;
            int rejected = 0;
            for (final Future<Object> future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (final java.util.concurrent.ExecutionException executionException) {
                    if (executionException.getCause() instanceof IdentityException identityException
                            && identityException.getCode() == ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE) {
                        rejected++;
                    } else {
                        throw executionException;
                    }
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            final BigDecimal allocatedTotal = jdbc.queryForObject(
                    "select coalesce(sum(allocated_amount), 0) from customer_payment_allocations where invoice_id = ?",
                    BigDecimal.class, posted.invoiceId());
            assertThat(allocatedTotal).isLessThanOrEqualTo(new BigDecimal("5000.00"));
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private PostInvoiceResponse creditSale(final User cashier, final UUID customerId, final String total) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                customerId, null), auth(cashier));
        final var afterLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("CUSTOM", null, null, null,
                "Service fee", BigDecimal.ONE, new BigDecimal(total), "EXEMPT", DiscountRequest.NONE));
        return postSaleService.post(afterLine.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(afterLine.version(), List.of(), true, List.of()), auth(cashier)).response();
    }

    private String paymentStatus(final UUID invoiceId) {
        return jdbc.queryForObject("select payment_status from v_invoice_balances where invoice_id = ?", String.class,
                invoiceId);
    }

    private CustomerDetailResponse createCustomer(final User actor) {
        return customerService.create(new CustomerCreateRequest("Payment Customer " + token(), "0771234567", null, null,
                null, null, null, null, "CORPORATE", new BigDecimal("1000000"), false, false), auth(actor));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("cashier_" + token(), "Cashier", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("invoice.payment.create")));
    }
}

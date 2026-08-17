package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.SettlementRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.CreditNoteService;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.InvoiceVoidService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class InvoiceVoidServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceVoidService invoiceVoidService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private CreditNoteService creditNoteService;
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
    void saleVoid001AuthorizedVoidKeepsHistoricalValuesAndRecordsAudit() {
        final User manager = createUser("MANAGER");
        final CustomerDetailResponse customer = createCustomer(manager);
        final PostInvoiceResponse posted = creditSale(manager, customer.customerId(), "5000.00");

        final InvoiceDetailResponse voided = invoiceVoidService.voidInvoice(posted.invoiceId(),
                new VoidInvoiceRequest("Customer cancelled order", posted.version()), auth(manager));

        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.totalAmount()).isEqualByComparingTo("5000.00");
        assertThat(voided.invoiceNumber()).isEqualTo(posted.invoiceNumber());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action_code = 'INVOICE_VOIDED'",
                Long.class, posted.invoiceId().toString())).isEqualTo(1L);
        assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void saleVoid003VoidWithoutReasonFails() {
        final User manager = createUser("MANAGER");
        final CustomerDetailResponse customer = createCustomer(manager);
        final PostInvoiceResponse posted = creditSale(manager, customer.customerId(), "1000.00");

        assertThatThrownBy(() -> invoiceVoidService.voidInvoice(posted.invoiceId(),
                new VoidInvoiceRequest("", posted.version()), auth(manager)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void voidingADraftInvoiceIsRejected() {
        final User manager = createUser("MANAGER");
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, null), auth(manager));

        assertThatThrownBy(() -> invoiceVoidService.voidInvoice(draft.invoiceId(),
                new VoidInvoiceRequest("reason", draft.version()), auth(manager)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_NOT_POSTED);
    }

    @Test
    void voidingAnInvoiceWithAnIssuedCreditNoteIsRejected() {
        final User manager = createUser("MANAGER");
        final CustomerDetailResponse customer = createCustomer(manager);
        final PostInvoiceResponse posted = creditSale(manager, customer.customerId(), "1000.00");
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();
        creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(), "Partial return",
                        List.of(new CreditNoteLineRequest(lineId, BigDecimal.ONE, false)),
                        new SettlementRequest("CUSTOMER_CREDIT", null, null)),
                new UsernamePasswordAuthenticationToken(manager.getUsername(), "n/a",
                        List.of(new SimpleGrantedAuthority("invoice.credit_note.create"))));

        assertThatThrownBy(() -> invoiceVoidService.voidInvoice(posted.invoiceId(),
                new VoidInvoiceRequest("reason", posted.version()), auth(manager)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.DOMAIN_RULE_REJECTED);
    }

    @Test
    void voidingWithStaleVersionIsRejected() {
        final User manager = createUser("MANAGER");
        final CustomerDetailResponse customer = createCustomer(manager);
        final PostInvoiceResponse posted = creditSale(manager, customer.customerId(), "1000.00");

        assertThatThrownBy(() -> invoiceVoidService.voidInvoice(posted.invoiceId(),
                new VoidInvoiceRequest("reason", posted.version() + 99), auth(manager)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    private PostInvoiceResponse creditSale(final User cashier, final UUID customerId, final String total) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                customerId, null), auth(cashier));
        final var afterLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("CUSTOM", null, null,
                "Service fee", BigDecimal.ONE, new BigDecimal(total), "EXEMPT", DiscountRequest.NONE));
        return postSaleService.post(afterLine.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(afterLine.version(), List.of(), true, List.of()), auth(cashier)).response();
    }

    private BigDecimal outstandingReceivable(final UUID customerId) {
        final List<BigDecimal> rows = jdbc.query(
                "select outstanding_receivable from v_customer_receivables where customer_id = ?",
                (rs, rowNum) -> rs.getBigDecimal("outstanding_receivable"), customerId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private CustomerDetailResponse createCustomer(final User actor) {
        return customerService.create(new CustomerCreateRequest("Void Customer " + token(), "0771234567", null, null,
                null, null, null, null, "CORPORATE", new BigDecimal("100000"), false, false), auth(actor));
    }

    private User createUser(final String roleCode) {
        final Role role = roleRepository.findByCode(roleCode).orElseThrow();
        final User user = new User("user_" + token(), "User", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("invoice.void")));
    }
}

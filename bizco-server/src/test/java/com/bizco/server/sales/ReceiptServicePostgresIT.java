package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.sales.application.ReceiptService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class ReceiptServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private ReceiptService receiptService;
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
    void receiptIsAWellFormedPdfWithBarcodeAndQr() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "1500.00");

        final byte[] pdf = receiptService.receipt(posted.invoiceId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void receiptIsUnavailableForADraftInvoice() {
        final User cashier = createUser();
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, null), auth(cashier));

        assertThatThrownBy(() -> receiptService.receipt(draft.invoiceId()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_NOT_POSTED);
    }

    @Test
    void reprintGeneratesAPdfAndRecordsAuditSeparatelyFromInitialPost() {
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final PostInvoiceResponse posted = creditSale(cashier, customer.customerId(), "800.00");

        final byte[] pdf = receiptService.reprint(posted.invoiceId(), auth(cashier));

        assertThat(pdf).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action_code = 'INVOICE_REPRINTED'",
                Long.class, posted.invoiceId().toString())).isEqualTo(1L);
    }

    private PostInvoiceResponse creditSale(final User cashier, final UUID customerId, final String total) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                customerId, null), auth(cashier));
        final var afterLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("CUSTOM", null, null, null,
                "Service fee", BigDecimal.ONE, new BigDecimal(total), "EXEMPT", DiscountRequest.NONE));
        return postSaleService.post(afterLine.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(afterLine.version(), List.of(), true, List.of()), auth(cashier)).response();
    }

    private CustomerDetailResponse createCustomer(final User actor) {
        return customerService.create(new CustomerCreateRequest("Receipt Customer " + token(), "0771234567", null, null,
                null, null, null, null, "CORPORATE", new BigDecimal("100000"), false, false), auth(actor));
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
                List.of(new SimpleGrantedAuthority("invoice.reprint")));
    }
}

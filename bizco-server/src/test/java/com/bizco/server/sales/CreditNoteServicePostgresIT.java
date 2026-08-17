package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.SettlementRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.CreditNoteService;
import com.bizco.server.sales.application.InvoiceService;
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

class CreditNoteServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private CreditNoteService creditNoteService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private CatalogService catalogService;
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
    void saleCn001ValidReturnReversesVatProportionallyAndAppliesToBalance() {
        enableVat(new BigDecimal("10.0000"));
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        // Credit sale, unpaid: subtotal 2000.00 + VAT 200.00 = 2200.00 balance to return against.
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "2.000",
                "1000.00", true, BigDecimal.ZERO);
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();

        final CreditNoteResponse creditNote = creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(
                posted.invoiceId(), "Defective unit", List.of(new CreditNoteLineRequest(lineId, new BigDecimal("1.000"), true)),
                new SettlementRequest("APPLY_TO_BALANCE", null, null)), auth(cashier)).response();

        assertThat(creditNote.creditNoteNumber()).matches("CN-\\d{8}-\\d{4}");
        assertThat(creditNote.status()).isEqualTo("APPLIED");
        // Half the line (1 of 2 units) returned: subtotal 1000.00, VAT 100.00 at 10%.
        assertThat(creditNote.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(creditNote.vatAmount()).isEqualByComparingTo("100.00");
        assertThat(creditNote.totalAmount()).isEqualByComparingTo("1100.00");
        assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo("1100.00");
        assertThat(jdbc.queryForObject("select count(*) from credit_note_applications where credit_note_id = ?",
                Long.class, creditNote.creditNoteId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action_code = 'CREDIT_NOTE_POSTED'",
                Long.class, creditNote.creditNoteId().toString())).isEqualTo(1L);
    }

    @Test
    void saleCn002ExcessReturnIsRejected() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "2.000",
                "500.00", false, new BigDecimal("1000.00"));
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();

        creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(), "Return 1",
                List.of(new CreditNoteLineRequest(lineId, new BigDecimal("1.000"), true)),
                new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier));

        assertThatThrownBy(() -> creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(),
                        "Return again", List.of(new CreditNoteLineRequest(lineId, new BigDecimal("2.000"), true)),
                        new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.RETURN_QUANTITY_EXCEEDED);
    }

    @Test
    void saleCn003NonRestockReturnIsRecordedWithoutRestockFlag() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "1.000",
                "300.00", false, new BigDecimal("300.00"));
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();

        final CreditNoteResponse creditNote = creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(
                posted.invoiceId(), "Damaged beyond resale", List.of(new CreditNoteLineRequest(lineId, BigDecimal.ONE, false)),
                new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier)).response();

        assertThat(creditNote.lines().get(0).restock()).isFalse();
        assertThat(creditNote.status()).isEqualTo("ISSUED");
    }

    @Test
    void saleCn004RefundCreatesCashbookOutAndDoesNotTouchInvoiceBalance() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "1.000",
                "400.00", false, new BigDecimal("400.00"));
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();
        final BigDecimal balanceBefore = outstandingReceivable(customer.customerId());

        final CreditNoteResponse creditNote = creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(
                posted.invoiceId(), "Refund requested", List.of(new CreditNoteLineRequest(lineId, BigDecimal.ONE, true)),
                new SettlementRequest("REFUND", "CASH", null)), auth(cashier)).response();

        assertThat(creditNote.status()).isEqualTo("APPLIED");
        assertThat(outstandingReceivable(customer.customerId())).isEqualByComparingTo(balanceBefore);
        assertThat(jdbc.queryForObject("select count(*) from customer_refunds where credit_note_id = ?", Long.class,
                creditNote.creditNoteId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from cashbook_entries where source_type = 'CUSTOMER_REFUND' and direction = 'OUT' "
                        + "and amount = ?", Long.class, creditNote.totalAmount())).isEqualTo(1L);
    }

    @Test
    void returnWindowExpiredIsRejected() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final InvoiceSummaryResponse draft = createDraftWithProductLine(cashier, customer.customerId(), product,
                "1.000", "100.00");
        // Backdate the invoice past the default 7-day return window.
        jdbc.update("update invoices set invoice_date = ? where invoice_id = ?",
                LocalDate.now().minusDays(30), draft.invoiceId());
        final PostInvoiceResponse posted = postSaleService.post(draft.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(draft.version(), List.of(new PaymentLineRequest("CASH", new BigDecimal("100.00"), null)),
                        false, List.of()), auth(cashier)).response();
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();

        assertThatThrownBy(() -> creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(),
                        "Too late", List.of(new CreditNoteLineRequest(lineId, BigDecimal.ONE, true)),
                        new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.RETURN_WINDOW_EXPIRED);
    }

    @Test
    void returnEligibilityReflectsAlreadyReturnedQuantity() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "5.000",
                "100.00", false, new BigDecimal("500.00"));
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();
        creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(), "Partial return",
                List.of(new CreditNoteLineRequest(lineId, new BigDecimal("2.000"), true)),
                new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier));

        final ReturnEligibilityResponse eligibility = creditNoteService.returnEligibility(posted.invoiceId());

        assertThat(eligibility.withinWindow()).isTrue();
        assertThat(eligibility.lines()).hasSize(1);
        assertThat(eligibility.lines().get(0).quantitySold()).isEqualByComparingTo("5.000");
        assertThat(eligibility.lines().get(0).quantityAlreadyReturned()).isEqualByComparingTo("2.000");
        assertThat(eligibility.lines().get(0).quantityRemaining()).isEqualByComparingTo("3.000");
    }

    @Test
    void searchWithNoFiltersDoesNotFail() {
        disableVat();
        final User cashier = createUser();
        final CustomerDetailResponse customer = createCustomer(cashier);
        final ProductDetailResponse product = createProduct();
        final PostInvoiceResponse posted = postProductSale(cashier, customer.customerId(), product, "1.000",
                "100.00", false, new BigDecimal("100.00"));
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();
        final CreditNoteResponse issued = creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(
                posted.invoiceId(), "Search test", List.of(new CreditNoteLineRequest(lineId, BigDecimal.ONE, true)),
                new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth(cashier)).response();

        final var all = creditNoteService.search(null, null, null, null, null);

        assertThat(all.data()).extracting(cn -> cn.creditNoteId()).contains(issued.creditNoteId());
    }

    private PostInvoiceResponse postProductSale(final User cashier, final UUID customerId, final ProductDetailResponse product,
                                                final String quantity, final String unitPrice, final boolean creditSale,
                                                final BigDecimal payment) {
        final InvoiceSummaryResponse draft = createDraftWithProductLine(cashier, customerId, product, quantity, unitPrice);
        final List<PaymentLineRequest> payments = payment.compareTo(BigDecimal.ZERO) == 0
                ? List.of() : List.of(new PaymentLineRequest("CASH", payment, null));
        return postSaleService.post(draft.invoiceId(), UUID.randomUUID(), new PostInvoiceRequest(draft.version(),
                payments, creditSale, List.of()), auth(cashier)).response();
    }

    private InvoiceSummaryResponse createDraftWithProductLine(final User cashier, final UUID customerId,
                                                               final ProductDetailResponse product, final String quantity,
                                                               final String unitPrice) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                customerId, null), auth(cashier));
        final var afterLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("PRODUCT",
                product.productId(), null, null, new BigDecimal(quantity), new BigDecimal(unitPrice), null,
                DiscountRequest.NONE));
        return new InvoiceSummaryResponse(afterLine.invoiceId(), afterLine.invoiceNumber(), afterLine.invoiceDate(),
                afterLine.status(), afterLine.customerId(), afterLine.totalAmount(), afterLine.version());
    }

    private void enableVat(final BigDecimal rate) {
        jdbc.update("update tax_configuration set vat_enabled = true, vat_rate = ? where tax_configuration_id = 1", rate);
    }

    /**
     * tax_configuration is a single global row (not per-test data), and other test methods in
     * this class mutate it via enableVat() - tests that need it off must say so explicitly rather
     * than assume the schema default, since JUnit doesn't guarantee method execution order.
     */
    private void disableVat() {
        jdbc.update("update tax_configuration set vat_enabled = false where tax_configuration_id = 1");
    }

    private BigDecimal outstandingReceivable(final UUID customerId) {
        final List<BigDecimal> rows = jdbc.query(
                "select outstanding_receivable from v_customer_receivables where customer_id = ?",
                (rs, rowNum) -> rs.getBigDecimal("outstanding_receivable"), customerId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("CN Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("CN-" + suffix, null, "Credit Note Widget", null,
                category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("50.00"), new BigDecimal("100.00"),
                null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private CustomerDetailResponse createCustomer(final User actor) {
        return customerService.create(new CustomerCreateRequest("CN Customer " + token(), "0771234567", null, null,
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
                List.of(new SimpleGrantedAuthority("invoice.credit_note.create")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

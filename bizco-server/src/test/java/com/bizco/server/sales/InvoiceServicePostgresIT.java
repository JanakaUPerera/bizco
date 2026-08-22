package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.InvoiceService;
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

class InvoiceServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
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
    void saleDraft001CreateDraftHasNoFinancialEffect() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");

        final InvoiceSummaryResponse created = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, "Walk-in"), auth(cashier));

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.invoiceNumber()).isNull();
        assertThat(created.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // No stock ledger exists yet (Week 12 per DevelopmentPlan.md, migration number TBD); once
        // it does, this test should also assert zero stock_movements rows reference this invoice,
        // per SALE-DRAFT-001.
    }

    @Test
    void saleDraft002EditingDraftLinesAndHeaderIncrementsVersion() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final ProductDetailResponse product = createProduct(token(), new BigDecimal("100.00"), null);

        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));
        final InvoiceDetailResponse afterLine = invoiceService.addLine(draft.invoiceId(),
                new AddInvoiceLineRequest("PRODUCT", product.productId(), null, null, null,
                        new BigDecimal("2"), null, null, DiscountRequest.NONE));

        assertThat(afterLine.lines()).hasSize(1);
        assertThat(afterLine.lines().get(0).unitPrice()).isEqualByComparingTo("100.00");
        assertThat(afterLine.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(afterLine.version()).isGreaterThan(draft.version());

        final InvoiceDetailResponse afterHeaderUpdate = invoiceService.updateHeader(draft.invoiceId(),
                new UpdateInvoiceHeaderRequest(LocalDate.now(), null, "SALES", null,
                        new DiscountRequest("PERCENTAGE", new BigDecimal("10")), "VIP customer", afterLine.version()));

        assertThat(afterHeaderUpdate.notes()).isEqualTo("VIP customer");
        assertThat(afterHeaderUpdate.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(afterHeaderUpdate.totalAmount()).isEqualByComparingTo("180.00");
        assertThat(afterHeaderUpdate.version()).isGreaterThan(afterLine.version());
    }

    @Test
    void saleDraft003StaleVersionReturnsConcurrentModification() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));

        invoiceService.updateHeader(draft.invoiceId(), new UpdateInvoiceHeaderRequest(LocalDate.now(), null,
                "SALES", null, DiscountRequest.NONE, "First update", draft.version()));

        assertThatThrownBy(() -> invoiceService.updateHeader(draft.invoiceId(),
                new UpdateInvoiceHeaderRequest(LocalDate.now(), null, "SALES", null, DiscountRequest.NONE,
                        "Stale update", draft.version())))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void saleDraft004PostedInvoiceCannotBeEdited() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));
        markPosted(draft.invoiceId());

        assertThatThrownBy(() -> invoiceService.updateHeader(draft.invoiceId(),
                new UpdateInvoiceHeaderRequest(LocalDate.now(), null, "SALES", null, DiscountRequest.NONE,
                        "Should be rejected", draft.version())))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_NOT_DRAFT);

        assertThatThrownBy(() -> invoiceService.addLine(draft.invoiceId(),
                new AddInvoiceLineRequest("CUSTOM", null, null, null, "Late fee", BigDecimal.ONE,
                        new BigDecimal("100.00"), "STANDARD", DiscountRequest.NONE)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_NOT_DRAFT);
    }

    @Test
    void salePrice001RetailPriceResolutionForWalkInCustomer() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final ProductDetailResponse product = createProduct(token(), new BigDecimal("500.00"), new BigDecimal("400.00"));
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));

        final InvoiceDetailResponse result = invoiceService.addLine(draft.invoiceId(),
                new AddInvoiceLineRequest("PRODUCT", product.productId(), null, null, null, BigDecimal.ONE, null, null,
                        DiscountRequest.NONE));

        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("500.00");
    }

    @Test
    void salePrice002WholesalePriceResolutionForWholesaleCustomer() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final ProductDetailResponse product = createProduct(token(), new BigDecimal("500.00"), new BigDecimal("400.00"));
        final CustomerDetailResponse wholesaleCustomer = customerService.create(
                new CustomerCreateRequest("Wholesale Buyer " + token(), "0771234567", null, null, null, null,
                        null, null, "WHOLESALE", BigDecimal.ZERO, false, false), auth(cashier));
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", wholesaleCustomer.customerId(), null),
                auth(cashier));

        final InvoiceDetailResponse result = invoiceService.addLine(draft.invoiceId(),
                new AddInvoiceLineRequest("PRODUCT", product.productId(), null, null, null, BigDecimal.ONE, null, null,
                        DiscountRequest.NONE));

        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("400.00");
    }

    private void markPosted(final UUID invoiceId) {
        jdbc.update("""
                update invoices
                set status = 'POSTED', invoice_number = ?, posted_at = current_timestamp
                where invoice_id = ?
                """, "INV-TEST-" + token(), invoiceId);
    }

    private ProductDetailResponse createProduct(final String suffix, final BigDecimal sellingPrice,
                                                final BigDecimal wholesalePrice) {
        final var category = catalogService.createCategory(new CategoryCreateRequest("Category " + suffix, null, null),
                authPermissions("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final ProductDetailResponse product = catalogService.createProduct(new ProductCreateRequest("SKU-" + suffix,
                null, "Product " + suffix, null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD",
                new BigDecimal("50.00"), sellingPrice, wholesalePrice, new BigDecimal("2.000"), null),
                authPermissions("product.create"));
        seedStock(product.productId());
        return product;
    }

    /** See {@code HeldSaleServicePostgresIT.seedStock} - a fresh product starts at zero physical
     *  stock now that posting a PRODUCT line goes through the Week 12 ledger. */
    private void seedStock(final UUID productId) {
        final UUID actorId = jdbc.queryForObject("select user_id from users limit 1", UUID.class);
        final UUID variantId = jdbc.queryForObject(
                "select product_variant_id from product_variants where product_id = ? and is_default = true",
                UUID.class, productId);
        jdbc.update("""
                insert into stock_movements (product_id, product_variant_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, ?, 'ADJUSTMENT', 100000.000, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, variantId, UUID.randomUUID(), actorId);
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

    private UsernamePasswordAuthenticationToken authPermissions(final String... permissions) {
        return new UsernamePasswordAuthenticationToken("system-test", "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

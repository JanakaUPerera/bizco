package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSearchResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.UpdateHeldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.HeldSaleService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class HeldSaleServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private HeldSaleService heldSaleService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void saleHold001HoldPersistsCartAndAllocatesNumber() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();

        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "2.000", "500.00"));

        assertThat(held.heldNumber()).matches("HLD-\\d{8}-\\d{4}");
        assertThat(held.status()).isEqualTo("HELD");
        assertThat(held.items()).hasSize(1);
        assertThat(held.items().get(0).sku()).isEqualTo(product.sku());
        assertThat(held.items().get(0).estimatedLineTotal()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action_code = 'HELD_SALE_CREATED'",
                Long.class, held.heldSaleId().toString())).isEqualTo(1L);
    }

    @Test
    void saleHold004ResumeRestoresCartAndKeepsItActive() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "1.000", "250.00"));

        final HeldSaleDetailResponse resumed = heldSaleService.resume(held.heldSaleId(), auth(cashier));

        assertThat(resumed.status()).isEqualTo("RESUMED");
        assertThat(resumed.items()).hasSize(1);
    }

    @Test
    void updateReplacesCartAndRejectsStaleVersion() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "1.000", "250.00"));

        final HeldSaleDetailResponse updated = heldSaleService.update(held.heldSaleId(),
                new UpdateHeldSaleRequest(null, List.of(item(product, "3.000", "250.00")), "updated", held.version()),
                auth(cashier));

        assertThat(updated.status()).isEqualTo("HELD");
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).quantity()).isEqualByComparingTo("3.000");

        assertThatThrownBy(() -> heldSaleService.update(held.heldSaleId(),
                new UpdateHeldSaleRequest(null, List.of(item(product, "1.000", "250.00")), null, held.version()),
                auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void saleHold002CancelReleasesItAndBlocksFurtherUse() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "1.000", "100.00"));

        final HeldSaleDetailResponse cancelled = heldSaleService.cancel(held.heldSaleId(), auth(cashier));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> heldSaleService.cancel(held.heldSaleId(), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.HELD_SALE_NOT_ACTIVE);
        assertThatThrownBy(() -> heldSaleService.convert(held.heldSaleId(), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.HELD_SALE_NOT_ACTIVE);
    }

    @Test
    void saleHold005ConvertThenPostMarksHeldSaleConverted() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "2.000", "400.00"));

        final InvoiceDetailResponse draft = heldSaleService.convert(held.heldSaleId(), auth(cashier));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).lineType()).isEqualTo("PRODUCT");

        final HeldSaleDetailResponse stillHeld = heldSaleService.get(held.heldSaleId());
        assertThat(stillHeld.status()).isEqualTo("HELD");
        assertThat(stillHeld.convertedInvoiceId()).isEqualTo(draft.invoiceId());

        postSaleService.post(draft.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(draft.version(), List.of(new PaymentLineRequest("CASH",
                        draft.totalAmount(), null)), false, List.of()),
                auth(cashier));

        final HeldSaleDetailResponse converted = heldSaleService.get(held.heldSaleId());
        assertThat(converted.status()).isEqualTo("CONVERTED");
        assertThat(converted.convertedInvoiceId()).isEqualTo(draft.invoiceId());
    }

    @Test
    void convertIsIdempotentAndReusesTheSameDraft() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "1.000", "100.00"));

        final InvoiceDetailResponse first = heldSaleService.convert(held.heldSaleId(), auth(cashier));
        final InvoiceDetailResponse second = heldSaleService.convert(held.heldSaleId(), auth(cashier));

        assertThat(second.invoiceId()).isEqualTo(first.invoiceId());
        assertThat(jdbc.queryForObject("select count(*) from invoices where invoice_id = ?", Long.class,
                first.invoiceId())).isEqualTo(1L);
    }

    @Test
    void searchFiltersByStatus() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        final HeldSaleDetailResponse held = hold(cashier, null, item(product, "1.000", "100.00"));
        heldSaleService.cancel(held.heldSaleId(), auth(cashier));
        hold(cashier, null, item(product, "1.000", "100.00"));

        final HeldSaleSearchResponse activeOnly = heldSaleService.search("HELD", null);

        assertThat(activeOnly.data()).extracting(summary -> summary.heldSaleId()).doesNotContain(held.heldSaleId());
        assertThat(activeOnly.data()).isNotEmpty();
    }

    private HeldSaleDetailResponse hold(final User cashier, final UUID customerId, final HeldSaleItemRequest... items) {
        return heldSaleService.hold(new HoldSaleRequest(customerId, List.of(items), "test hold"), auth(cashier));
    }

    private HeldSaleItemRequest item(final ProductDetailResponse product, final String quantity, final String price) {
        return new HeldSaleItemRequest(product.productId(), null, new BigDecimal(quantity), new BigDecimal(price),
                DiscountRequest.NONE);
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Held Sale " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final ProductDetailResponse product = catalogService.createProduct(new ProductCreateRequest("HLD-" + suffix,
                null, "Held Sale Widget", null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD",
                new BigDecimal("50.00"), new BigDecimal("100.00"), null, new BigDecimal("2.000"), null),
                auth("product.create"));
        seedStock(product.productId());
        return product;
    }

    /** Every test here holds/sells a freshly-created product, which starts with zero physical stock
     *  now that Week 12's ledger backs {@code HeldSaleService}'s availability check - so give it
     *  abundant stock directly (bypassing the adjustment workflow, which is what this class isn't
     *  testing) rather than making every test case do it. */
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
                List.of(new SimpleGrantedAuthority("invoice.hold_bill")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

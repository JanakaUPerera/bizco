package com.bizco.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductBarcodeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.SettlementRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.catalog.application.VariantService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockAdjustmentService;
import com.bizco.server.inventory.application.StockQueryService;
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

/** Phase 6 Week 19 (DevelopmentPlan.md Week 19 task 19.6, required acceptance VAR-POS-001..003). */
class VariantAwarePosIT extends PostgresIntegrationTest {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private VariantService variantService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private CreditNoteService creditNoteService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private StockQueryService stockQueryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private User actor;

    @Test
    void varPos001BarcodeResolvesToTheSpecificVariantItBelongsTo() {
        actor = createUser();
        final String suffix = token();
        final ProductDetailResponse product = createProduct(suffix, "PB-" + suffix);
        final VariantResponse defaultVariant = defaultVariant(product.productId());

        // Diverge the default variant's own barcode from the product's, so a scan of the
        // product's original barcode can only resolve via the product-level fallback branch.
        variantService.updateVariant(defaultVariant.productVariantId(), new VariantUpdateRequest(
                defaultVariant.sku(), null, defaultVariant.variantLabel(), defaultVariant.costPrice(),
                defaultVariant.sellingPrice(), defaultVariant.wholesalePrice(), defaultVariant.reorderPoint(),
                defaultVariant.active(), defaultVariant.imagePath(), defaultVariant.version()), auth("product.variant.update"));

        final ProductBarcodeResponse fallbackMatch = catalogService.barcode("PB-" + suffix, false);
        assertThat(fallbackMatch.variantSpecific()).isFalse();
        assertThat(fallbackMatch.resolvedVariantId()).isEqualTo(defaultVariant.productVariantId());
        assertThat(fallbackMatch.product().productId()).isEqualTo(product.productId());

        final VariantResponse secondVariant = createSecondVariant(product.productId(), "V2-" + suffix,
                "V2B-" + suffix, new BigDecimal("30.00"));

        final ProductBarcodeResponse variantMatch = catalogService.barcode("V2B-" + suffix, false);
        assertThat(variantMatch.variantSpecific()).isTrue();
        assertThat(variantMatch.resolvedVariantId()).isEqualTo(secondVariant.productVariantId());
        assertThat(variantMatch.product().productId()).isEqualTo(product.productId());
    }

    @Test
    void varPos002SaleOfAnExplicitlyChosenVariantPostsStockAgainstThatVariantNotTheDefault() {
        actor = createUser();
        final String suffix = token();
        final ProductDetailResponse product = createProduct(suffix, null);
        final VariantResponse secondVariant = createSecondVariant(product.productId(), "V2-" + suffix, null,
                new BigDecimal("30.00"));
        seedStock(secondVariant.productVariantId());

        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, null), auth("invoice.create"));
        final InvoiceDetailResponse withLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest(
                "PRODUCT", product.productId(), secondVariant.productVariantId(), null, null, BigDecimal.ONE,
                secondVariant.sellingPrice(), null, DiscountRequest.NONE));
        assertThat(withLine.lines().get(0).productVariantId()).isEqualTo(secondVariant.productVariantId());
        final PostInvoiceResponse posted = postSaleService.post(draft.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(withLine.version(),
                        List.of(new PaymentLineRequest("CASH", secondVariant.sellingPrice(), null)), false, List.of()),
                auth("invoice.create")).response();

        assertThat(posted.status()).isEqualTo("POSTED");
        final Long movementsAgainstVariant = jdbc.queryForObject(
                "select count(*) from stock_movements where movement_type = 'SALE' and reference_type = 'INVOICE' "
                        + "and reference_id = ? and product_variant_id = ?",
                Long.class, posted.invoiceId(), secondVariant.productVariantId());
        assertThat(movementsAgainstVariant).isEqualTo(1L);

        // A variant that belongs to a different product must be rejected, not silently accepted.
        final ProductDetailResponse otherProduct = createProduct(token(), null);
        final VariantResponse otherDefault = defaultVariant(otherProduct.productId());
        final var mismatchDraft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null,
                "SALES", null, null), auth("invoice.create"));
        assertThatThrownBy(() -> invoiceService.addLine(mismatchDraft.invoiceId(), new AddInvoiceLineRequest(
                        "PRODUCT", product.productId(), otherDefault.productVariantId(), null, null, BigDecimal.ONE,
                        null, null, DiscountRequest.NONE)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.VARIANT_PRODUCT_MISMATCH);
    }

    @Test
    void varPos003SaleReturnAndAdjustmentOfOneVariantDoNotAffectItsSiblingVariant() {
        actor = createUser();
        final String suffix = token();
        final ProductDetailResponse product = createProduct(suffix, null);
        final VariantResponse defaultVariant = defaultVariant(product.productId());
        final VariantResponse secondVariant = createSecondVariant(product.productId(), "V2-" + suffix, null,
                new BigDecimal("30.00"));

        adjustStock(product.productId(), defaultVariant.productVariantId(), "20.000");
        adjustStock(product.productId(), secondVariant.productVariantId(), "50.000");

        final CustomerDetailResponse customer = customerService.create(new CustomerCreateRequest(
                "VAR-POS Customer " + suffix, "0771234567", null, null, null, null, null, null, "RETAIL",
                BigDecimal.ZERO, false, false), auth("customer.create"));
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                customer.customerId(), null), auth("invoice.create"));
        final InvoiceDetailResponse withLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest(
                "PRODUCT", product.productId(), secondVariant.productVariantId(), null, null,
                new BigDecimal("5"), secondVariant.sellingPrice(), null, DiscountRequest.NONE));
        final PostInvoiceResponse posted = postSaleService.post(draft.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(withLine.version(), List.of(new PaymentLineRequest("CASH",
                        secondVariant.sellingPrice().multiply(new BigDecimal("5")), null)), false, List.of()),
                auth("invoice.create")).response();
        final UUID lineId = invoiceService.get(posted.invoiceId()).lines().get(0).invoiceLineId();

        creditNoteService.issue(UUID.randomUUID(), new CreateCreditNoteRequest(posted.invoiceId(), "Wrong variant sold",
                List.of(new CreditNoteLineRequest(lineId, new BigDecimal("2"), true)),
                new SettlementRequest("CUSTOMER_CREDIT", null, null)), auth("invoice.credit_note.create"));

        adjustStock(product.productId(), secondVariant.productVariantId(), "3.000");

        // 50 - 5 (sale) + 2 (restocked return) + 3 (adjustment) = 50; the default variant, seeded
        // separately and never touched by any of the above, must still read exactly its own 20.
        final StockLevelResponse secondLevel = levelFor(secondVariant.sku());
        assertThat(secondLevel.productVariantId()).isEqualTo(secondVariant.productVariantId());
        assertThat(secondLevel.availableStock()).isEqualByComparingTo("50.000");
        final StockLevelResponse defaultLevel = levelFor(defaultVariant.sku());
        assertThat(defaultLevel.productVariantId()).isEqualTo(defaultVariant.productVariantId());
        assertThat(defaultLevel.availableStock()).isEqualByComparingTo("20.000");
    }

    private void adjustStock(final UUID productId, final UUID productVariantId, final String quantity) {
        final var adjustment = stockAdjustmentService.create(new CreateStockAdjustmentRequest(productId,
                productVariantId, "POSITIVE", new BigDecimal(quantity), "VAR-POS seed"),
                auth("inventory.adjustment.create"));
        assertThat(adjustment.productVariantId()).isEqualTo(productVariantId);
        stockAdjustmentService.approve(UUID.randomUUID(), adjustment.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("Confirmed", adjustment.version()), auth("inventory.adjustment.approve"));
    }

    private StockLevelResponse levelFor(final String sku) {
        final var result = stockQueryService.search(sku, 0, 10);
        assertThat(result.data()).as("exactly one variant-level row for sku %s", sku).hasSize(1);
        return result.data().get(0);
    }

    private void seedStock(final UUID productVariantId) {
        final UUID actorId = jdbc.queryForObject("select user_id from users limit 1", UUID.class);
        final UUID productId = jdbc.queryForObject(
                "select product_id from product_variants where product_variant_id = ?", UUID.class, productVariantId);
        jdbc.update("""
                insert into stock_movements (product_id, product_variant_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, ?, 'ADJUSTMENT', 100000.000, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, productVariantId, UUID.randomUUID(), actorId);
    }

    private VariantResponse defaultVariant(final UUID productId) {
        return variantService.listVariants(productId).stream().filter(VariantResponse::defaultVariant).findFirst()
                .orElseThrow();
    }

    private VariantResponse createSecondVariant(final UUID productId, final String sku, final String barcode,
                                                final BigDecimal sellingPrice) {
        return variantService.createVariant(productId, new VariantCreateRequest(sku, barcode, "Alternate", null,
                sellingPrice, null, new BigDecimal("2.000"), null), auth("product.variant.create"));
    }

    private ProductDetailResponse createProduct(final String suffix, final String barcode) {
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("VarPos " + suffix,
                null, null), auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("VPOS-" + suffix, barcode,
                "Variant POS Widget " + suffix, null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD",
                new BigDecimal("10.00"), new BigDecimal("20.00"), null, new BigDecimal("2.000"), null),
                auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        final User user = new User("varpos_" + token(), "VarPos Test Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken(actor.getUsername(), "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

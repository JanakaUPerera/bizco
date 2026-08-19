package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.GoodsReceiptService;
import com.bizco.server.purchasing.application.SupplierReturnService;
import com.bizco.server.purchasing.application.SupplierService;
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

/** PUR-RET-001..003, SYS-IDEM-005 (DevelopmentPlan.md Week 15). */
class SupplierReturnServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private SupplierReturnService supplierReturnService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private SupplierService supplierService;
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
    void purRet001CreateReturnDeductsStockAndComputesTotal() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("10.000"),
                new BigDecimal("8.00"));

        final var result = supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                supplier.supplierId(), receipt.goodsReceiptId(), "Wrong item shipped",
                List.of(new CreateSupplierReturnItemRequest(receipt.items().get(0).goodsReceiptItemId(),
                        new BigDecimal("3.000")))), auth(user));

        assertThat(result.replayed()).isFalse();
        final SupplierReturnResponse response = result.response();
        assertThat(response.totalAmount()).isEqualByComparingTo("24.00"); // 3 * 8.00
        assertThat(response.returnNumber()).startsWith("SRT-");

        final BigDecimal physicalStock = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movements where product_id = ?", BigDecimal.class,
                product.productId());
        assertThat(physicalStock).isEqualByComparingTo("7.000"); // 10 received - 3 returned
    }

    @Test
    void purRet002ReturnValuationUsesOriginalGoodsReceiptCostNotCurrentProductCost() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse firstReceipt = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00"));
        // A later receipt changes the product's current cost - the return of the FIRST receipt's
        // line must still value at 10.00, not this new 25.00.
        postedReceipt(user, supplier, product, new BigDecimal("5.000"), new BigDecimal("25.00"));

        final var result = supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                supplier.supplierId(), firstReceipt.goodsReceiptId(), "Original cost check",
                List.of(new CreateSupplierReturnItemRequest(firstReceipt.items().get(0).goodsReceiptItemId(),
                        new BigDecimal("2.000")))), auth(user));

        assertThat(result.response().totalAmount()).isEqualByComparingTo("20.00"); // 2 * 10.00, not 2 * 25.00
        assertThat(result.response().items().get(0).unitCost()).isEqualByComparingTo("10.00");
    }

    @Test
    void purRet003CumulativeReturnCannotExceedReceivedQuantity() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00"));
        final UUID goodsReceiptItemId = receipt.items().get(0).goodsReceiptItemId();

        supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(supplier.supplierId(),
                receipt.goodsReceiptId(), "First return",
                List.of(new CreateSupplierReturnItemRequest(goodsReceiptItemId, new BigDecimal("4.000")))), auth(user));

        assertThatThrownBy(() -> supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                supplier.supplierId(), receipt.goodsReceiptId(), "Second return",
                List.of(new CreateSupplierReturnItemRequest(goodsReceiptItemId, new BigDecimal("2.000")))), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.RETURN_QUANTITY_EXCEEDED);
    }

    @Test
    void purRet004ReturnAgainstNonPostedOrWrongSupplierReceiptIsRejected() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final SupplierDetailResponse otherSupplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse draftWithItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), BigDecimal.TEN, null, null, BigDecimal.ONE));

        assertThatThrownBy(() -> supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                supplier.supplierId(), draftWithItem.goodsReceiptId(), "Not posted yet",
                List.of(new CreateSupplierReturnItemRequest(draftWithItem.items().get(0).goodsReceiptItemId(),
                        BigDecimal.ONE))), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.GOODS_RECEIPT_NOT_POSTED);

        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00"));
        assertThatThrownBy(() -> supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                otherSupplier.supplierId(), receipt.goodsReceiptId(), "Wrong supplier",
                List.of(new CreateSupplierReturnItemRequest(receipt.items().get(0).goodsReceiptItemId(),
                        BigDecimal.ONE))), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.DOMAIN_RULE_REJECTED);
    }

    @Test
    void sysIdem005CreateIsIdempotentUnderTheSameKey() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("6.000"),
                new BigDecimal("5.00"));
        final UUID idempotencyKey = UUID.randomUUID();
        final var request = new CreateSupplierReturnRequest(supplier.supplierId(), receipt.goodsReceiptId(),
                "Idempotency check",
                List.of(new CreateSupplierReturnItemRequest(receipt.items().get(0).goodsReceiptItemId(),
                        new BigDecimal("2.000"))));

        final var first = supplierReturnService.create(idempotencyKey, request, auth(user));
        final var second = supplierReturnService.create(idempotencyKey, request, auth(user));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().supplierReturnId()).isEqualTo(first.response().supplierReturnId());
        final BigDecimal physicalStock = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movements where product_id = ?", BigDecimal.class,
                product.productId());
        assertThat(physicalStock).isEqualByComparingTo("4.000"); // 6 received - 2 returned, only once
    }

    private GoodsReceiptDetailResponse postedReceipt(final User user, final SupplierDetailResponse supplier,
                                                      final ProductDetailResponse product, final BigDecimal quantity,
                                                      final BigDecimal unitCost) {
        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse withItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), quantity, null, null, unitCost));
        return goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user)).response();
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-RET-" + suffix, "Return Test Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Return Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("RETI-" + suffix, null, "Return Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("1.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("sret_" + token(), "Return Test Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("purchasing.grn.create"),
                        new SimpleGrantedAuthority("purchasing.return.create")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

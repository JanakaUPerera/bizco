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
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.GoodsReceiptService;
import com.bizco.server.purchasing.application.PurchaseOrderService;
import com.bizco.server.purchasing.application.SupplierProductService;
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

/** PUR-GRN-001..005, PUR-GRN-PARTIAL-001..002 (DevelopmentPlan.md Week 14). */
class GoodsReceiptServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private SupplierProductService supplierProductService;
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
    void purGrn001DraftCreationAddItemsRecalculatesTotal() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();

        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), null, LocalDate.now(), "ad-hoc receipt"),
                auth(user));
        assertThat(draft.status()).isEqualTo("DRAFT");

        final GoodsReceiptDetailResponse withItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), new BigDecimal("10.000"), null, null,
                        new BigDecimal("8.00")));
        assertThat(withItem.totalAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void purGrn002PostOnlyMovesUsableQuantityIntoStock() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse withItem = adHocReceiptWithItem(user, supplier, product,
                new BigDecimal("10.000"), new BigDecimal("2.000"), new BigDecimal("1.000"), new BigDecimal("5.00"));

        final var posted = goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user));

        assertThat(posted.response().status()).isEqualTo("POSTED");
        final BigDecimal physicalStock = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movements where product_id = ?", BigDecimal.class,
                product.productId());
        assertThat(physicalStock).isEqualByComparingTo("7.000"); // 10 received - 2 damaged - 1 rejected
    }

    @Test
    void purGrn003PostUpdatesCostHistoryAndProductCost() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        supplierProductService.create(new SupplierProductCreateRequest(supplier.supplierId(), product.productId(),
                null, new BigDecimal("50.00"), BigDecimal.ONE, null, true), auth("purchasing.supplier_product.create"));
        final GoodsReceiptDetailResponse withItem = adHocReceiptWithItem(user, supplier, product,
                new BigDecimal("4.000"), null, null, new BigDecimal("55.00"));

        goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user));

        // Phase 6 Week 18: goods-receipt posting now writes the variant's cost price, not the
        // product's (the variant is the real cost-tracking granularity going forward).
        final BigDecimal variantCostPrice = jdbc.queryForObject(
                "select cost_price from product_variants where product_id = ? and is_default = true", BigDecimal.class,
                product.productId());
        assertThat(variantCostPrice).isEqualByComparingTo("55.00");
        final Long historyCount = jdbc.queryForObject(
                "select count(*) from product_cost_history where product_id = ? and unit_cost = 55.00", Long.class,
                product.productId());
        assertThat(historyCount).isEqualTo(1L);
        final BigDecimal lastPurchasePrice = jdbc.queryForObject(
                "select last_purchase_price from supplier_products where supplier_id = ? and product_id = ?",
                BigDecimal.class, supplier.supplierId(), product.productId());
        assertThat(lastPurchasePrice).isEqualByComparingTo("55.00");
    }

    @Test
    void purGrn004DuplicateSupplierReferenceOnPostedReceiptsIsRejected() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final String reference = "INV-" + token();

        final GoodsReceiptDetailResponse firstDraft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), reference, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse first = goodsReceiptService.addItem(firstDraft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), BigDecimal.ONE, null, null, BigDecimal.TEN));
        goodsReceiptService.post(UUID.randomUUID(), first.goodsReceiptId(), new PostGoodsReceiptRequest(first.version()),
                auth(user));

        final GoodsReceiptDetailResponse secondDraft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), reference, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse second = goodsReceiptService.addItem(secondDraft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), BigDecimal.ONE, null, null, BigDecimal.TEN));

        assertThatThrownBy(() -> goodsReceiptService.post(UUID.randomUUID(), second.goodsReceiptId(),
                new PostGoodsReceiptRequest(second.version()), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.GOODS_RECEIPT_SUPPLIER_REFERENCE_DUPLICATE);
    }

    @Test
    void purGrn005PostIsIdempotentUnderTheSameKey() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse withItem = adHocReceiptWithItem(user, supplier, product,
                new BigDecimal("3.000"), null, null, new BigDecimal("12.00"));
        final UUID idempotencyKey = UUID.randomUUID();
        final var request = new PostGoodsReceiptRequest(withItem.version());

        final var first = goodsReceiptService.post(idempotencyKey, withItem.goodsReceiptId(), request, auth(user));
        final var second = goodsReceiptService.post(idempotencyKey, withItem.goodsReceiptId(), request, auth(user));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().receiptNumber()).isEqualTo(first.response().receiptNumber());
        final BigDecimal physicalStock = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movements where product_id = ?", BigDecimal.class,
                product.productId());
        assertThat(physicalStock).isEqualByComparingTo("3.000");
    }

    @Test
    void purGrnPartial001PartialReceiptSetsPurchaseOrderPartiallyReceived() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final PurchaseOrderDetailResponse sentPo = sentPurchaseOrder(user, supplier, product, new BigDecimal("100.000"));

        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(new CreateGoodsReceiptRequest(
                sentPo.purchaseOrderId(), supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse withItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(sentPo.items().get(0).purchaseOrderItemId(), product.productId(),
                        new BigDecimal("40.000"), null, null, new BigDecimal("10.00")));

        goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user));

        final PurchaseOrderDetailResponse po = purchaseOrderService.get(sentPo.purchaseOrderId());
        assertThat(po.status()).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    void purGrnPartial002FullyReceivedAcrossMultipleReceiptsAutoClosesPurchaseOrder() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final PurchaseOrderDetailResponse sentPo = sentPurchaseOrder(user, supplier, product, new BigDecimal("50.000"));
        final UUID poItemId = sentPo.items().get(0).purchaseOrderItemId();

        receiveAgainstPo(user, supplier, product, sentPo.purchaseOrderId(), poItemId, new BigDecimal("30.000"));
        assertThat(purchaseOrderService.get(sentPo.purchaseOrderId()).status()).isEqualTo("PARTIALLY_RECEIVED");

        receiveAgainstPo(user, supplier, product, sentPo.purchaseOrderId(), poItemId, new BigDecimal("20.000"));
        assertThat(purchaseOrderService.get(sentPo.purchaseOrderId()).status()).isEqualTo("CLOSED");
    }

    private void receiveAgainstPo(final User user, final SupplierDetailResponse supplier,
                                  final ProductDetailResponse product, final UUID purchaseOrderId,
                                  final UUID poItemId, final BigDecimal quantity) {
        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(new CreateGoodsReceiptRequest(
                purchaseOrderId, supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse withItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(poItemId, product.productId(), quantity, null, null,
                        new BigDecimal("10.00")));
        goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user));
    }

    private PurchaseOrderDetailResponse sentPurchaseOrder(final User user, final SupplierDetailResponse supplier,
                                                           final ProductDetailResponse product,
                                                           final BigDecimal quantityOrdered) {
        final PurchaseOrderDetailResponse draft = purchaseOrderService.createDraft(new CreatePurchaseOrderRequest(
                supplier.supplierId(), LocalDate.now(), null, null, "test PO"), auth(user));
        final PurchaseOrderDetailResponse withItem = purchaseOrderService.addItem(draft.purchaseOrderId(),
                new AddPurchaseOrderItemRequest(product.productId(), quantityOrdered, new BigDecimal("1.00")));
        final var sent = purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(withItem.version()), auth(user));
        return sent.response();
    }

    private GoodsReceiptDetailResponse adHocReceiptWithItem(final User user, final SupplierDetailResponse supplier,
                                                             final ProductDetailResponse product,
                                                             final BigDecimal quantityReceived,
                                                             final BigDecimal quantityDamaged,
                                                             final BigDecimal quantityRejected,
                                                             final BigDecimal unitCost) {
        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        return goodsReceiptService.addItem(draft.goodsReceiptId(), new AddGoodsReceiptItemRequest(null,
                product.productId(), quantityReceived, quantityDamaged, quantityRejected, unitCost));
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-GRN-" + suffix, "GRN Test Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("GRN Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("GRNI-" + suffix, null, "GRN Widget " + suffix,
                null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal("1.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("grn_" + token(), "GRN Test Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("purchasing.grn.create"),
                        new SimpleGrantedAuthority("purchasing.po.create"),
                        new SimpleGrantedAuthority("purchasing.po.approve")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

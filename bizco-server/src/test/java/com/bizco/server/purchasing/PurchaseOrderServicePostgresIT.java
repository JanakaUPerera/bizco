package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CancelPurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.PurchaseOrderService;
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

/** PUR-PO-001..006 (DevelopmentPlan.md Week 13, DatabaseDesign.md &sect;17.3). */
class PurchaseOrderServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private PurchaseOrderService purchaseOrderService;
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
    void purPo001DraftCreationAddItemsAndRecalculatesTotals() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();

        final PurchaseOrderDetailResponse draft = purchaseOrderService.createDraft(
                new CreatePurchaseOrderRequest(supplier.supplierId(), LocalDate.now(), null, null, "test order"),
                auth(user));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.poNumber()).isNull();

        final PurchaseOrderDetailResponse withItem = purchaseOrderService.addItem(draft.purchaseOrderId(),
                new AddPurchaseOrderItemRequest(product.productId(), new BigDecimal("10.000"), new BigDecimal("5.00")));

        assertThat(withItem.items()).hasSize(1);
        assertThat(withItem.totalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void purPo002BelowThresholdSendsDirectlyFromDraft() {
        final User user = createUser();
        final PurchaseOrderDetailResponse withItem = draftWithOneItem(user, new BigDecimal("100.000"),
                new BigDecimal("10.00")); // total 1000, below the 50,000 default threshold

        final var result = purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(withItem.version()), auth(user));

        assertThat(result.response().status()).isEqualTo("SENT");
        assertThat(result.response().poNumber()).matches("PO-\\d{8}-\\d{4}");
    }

    @Test
    void purPo003AtOrAboveThresholdRequiresApprovalBeforeSend() {
        final User user = createUser();
        final User approver = createUser();
        final PurchaseOrderDetailResponse withItem = draftWithOneItem(user, new BigDecimal("100.000"),
                new BigDecimal("600.00")); // total 60,000, at/above the 50,000 default threshold

        assertThatThrownBy(() -> purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(withItem.version()), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.PURCHASE_ORDER_APPROVAL_REQUIRED);

        final var approved = purchaseOrderService.approve(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(withItem.version()), auth(approver));
        assertThat(approved.response().status()).isEqualTo("APPROVED");
        assertThat(approved.response().poNumber()).isNotNull();

        final var sent = purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(approved.response().version()), auth(user));
        assertThat(sent.response().status()).isEqualTo("SENT");
        assertThat(sent.response().poNumber()).isEqualTo(approved.response().poNumber());
    }

    @Test
    void purPo004ApproveIsIdempotentUnderTheSameKey() {
        final User user = createUser();
        final PurchaseOrderDetailResponse withItem = draftWithOneItem(user, new BigDecimal("1.000"),
                new BigDecimal("60000.00"));
        final UUID idempotencyKey = UUID.randomUUID();
        final var request = new DecidePurchaseOrderRequest(withItem.version());

        final var first = purchaseOrderService.approve(idempotencyKey, withItem.purchaseOrderId(), request, auth(user));
        final var second = purchaseOrderService.approve(idempotencyKey, withItem.purchaseOrderId(), request, auth(user));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().poNumber()).isEqualTo(first.response().poNumber());
    }

    @Test
    void purPo005CancelReleasesTheDraftAndBlocksFurtherUse() {
        final User user = createUser();
        final PurchaseOrderDetailResponse withItem = draftWithOneItem(user, BigDecimal.ONE, new BigDecimal("100.00"));

        final PurchaseOrderDetailResponse cancelled = purchaseOrderService.cancel(withItem.purchaseOrderId(),
                new CancelPurchaseOrderRequest("no longer needed", withItem.version()), auth(user));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(cancelled.version()), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.PURCHASE_ORDER_INVALID_TRANSITION);
    }

    @Test
    void purPo006StaleVersionIsRejected() {
        final User user = createUser();
        final PurchaseOrderDetailResponse withItem = draftWithOneItem(user, BigDecimal.ONE, new BigDecimal("100.00"));

        assertThatThrownBy(() -> purchaseOrderService.send(UUID.randomUUID(), withItem.purchaseOrderId(),
                new DecidePurchaseOrderRequest(withItem.version() + 99), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    private PurchaseOrderDetailResponse draftWithOneItem(final User user, final BigDecimal quantity,
                                                          final BigDecimal unitPrice) {
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final PurchaseOrderDetailResponse draft = purchaseOrderService.createDraft(
                new CreatePurchaseOrderRequest(supplier.supplierId(), LocalDate.now(), null, null, "test order"),
                auth(user));
        return purchaseOrderService.addItem(draft.purchaseOrderId(),
                new AddPurchaseOrderItemRequest(product.productId(), quantity, unitPrice));
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-POI-" + suffix, "PO Test Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("PO Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("POI-" + suffix, null, "PO Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("po_" + token(), "PO Test Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("purchasing.po.create"),
                        new SimpleGrantedAuthority("purchasing.po.approve")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

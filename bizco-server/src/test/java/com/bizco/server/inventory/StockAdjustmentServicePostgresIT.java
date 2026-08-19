package com.bizco.server.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockAdjustmentService;
import com.bizco.server.inventory.application.StockQueryService;
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

/** STK-ADJ-001..005 (AcceptanceTests.md &sect;23): PENDING -&gt; APPROVED/REJECTED, exactly one
 *  movement on approval, double-decide rejected, insufficient stock rejected. */
class StockAdjustmentServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private StockQueryService stockQueryService;
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
    void stkAdj001CreateLandsPendingWithNoStockEffect() {
        final User requester = createUser();
        final ProductDetailResponse product = createProduct();

        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "POSITIVE", new BigDecimal("10.000"),
                        "Initial stock count"), auth(requester, "inventory.adjustment.create"));

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("0.000");
    }

    @Test
    void stkAdj002ApproveCreatesExactlyOneMovement() {
        final User requester = createUser();
        final User approver = createUser();
        final ProductDetailResponse product = createProduct();
        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "POSITIVE", new BigDecimal("8.000"), "Count"),
                auth(requester, "inventory.adjustment.create"));

        final var result = stockAdjustmentService.approve(UUID.randomUUID(), created.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("Confirmed", created.version()),
                auth(approver, "inventory.adjustment.approve"));

        assertThat(result.response().status()).isEqualTo("APPROVED");
        assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("8.000");
        final Long movementCount = jdbc.queryForObject(
                "select count(*) from stock_movements where movement_type = 'ADJUSTMENT' and source_line_id = ?",
                Long.class, created.stockAdjustmentId());
        assertThat(movementCount).isEqualTo(1L);
    }

    @Test
    void stkAdj003RejectLeavesNoStockMovement() {
        final User requester = createUser();
        final User approver = createUser();
        final ProductDetailResponse product = createProduct();
        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "NEGATIVE", new BigDecimal("2.000"), "Damage claim"),
                auth(requester, "inventory.adjustment.create"));

        final var result = stockAdjustmentService.reject(UUID.randomUUID(), created.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("Not warranted", created.version()),
                auth(approver, "inventory.adjustment.approve"));

        assertThat(result.response().status()).isEqualTo("REJECTED");
        assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("0.000");
    }

    @Test
    void stkAdj004DoubleApprovalIsRejected() {
        final User requester = createUser();
        final User approver = createUser();
        final ProductDetailResponse product = createProduct();
        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "POSITIVE", new BigDecimal("5.000"), "Count"),
                auth(requester, "inventory.adjustment.create"));
        stockAdjustmentService.approve(UUID.randomUUID(), created.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("first", created.version()),
                auth(approver, "inventory.adjustment.approve"));

        assertThatThrownBy(() -> stockAdjustmentService.approve(UUID.randomUUID(), created.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("second", created.version()),
                auth(approver, "inventory.adjustment.approve")))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);

        final Long movementCount = jdbc.queryForObject(
                "select count(*) from stock_movements where movement_type = 'ADJUSTMENT' and source_line_id = ?",
                Long.class, created.stockAdjustmentId());
        assertThat(movementCount).isEqualTo(1L);
    }

    @Test
    void stkAdj005NegativeAdjustmentBeyondAvailableStockIsRejected() {
        final User requester = createUser();
        final User approver = createUser();
        final ProductDetailResponse product = createProduct();
        seedStock(product.productId(), "3.000");
        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "NEGATIVE", new BigDecimal("5.000"),
                        "Damage - more than on hand"), auth(requester, "inventory.adjustment.create"));

        assertThatThrownBy(() -> stockAdjustmentService.approve(UUID.randomUUID(), created.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("Approved anyway", created.version()),
                auth(approver, "inventory.adjustment.approve")))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.STOCK_INSUFFICIENT);

        final StockLevelResponse level = stockQueryService.levelFor(product.productId());
        assertThat(level.physicalStock()).isEqualByComparingTo("3.000");
    }

    @Test
    void approveIsIdempotentUnderTheSameKey() {
        final User requester = createUser();
        final User approver = createUser();
        final ProductDetailResponse product = createProduct();
        final StockAdjustmentResponse created = stockAdjustmentService.create(
                new CreateStockAdjustmentRequest(product.productId(), "POSITIVE", new BigDecimal("6.000"), "Count"),
                auth(requester, "inventory.adjustment.create"));
        final UUID idempotencyKey = UUID.randomUUID();
        final DecideStockAdjustmentRequest request = new DecideStockAdjustmentRequest("Confirmed", created.version());

        final var first = stockAdjustmentService.approve(idempotencyKey, created.stockAdjustmentId(), request,
                auth(approver, "inventory.adjustment.approve"));
        final var second = stockAdjustmentService.approve(idempotencyKey, created.stockAdjustmentId(), request,
                auth(approver, "inventory.adjustment.approve"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("6.000");
    }

    private void seedStock(final UUID productId, final String quantity) {
        final UUID actorId = jdbc.queryForObject("select user_id from users limit 1", UUID.class);
        jdbc.update("""
                insert into stock_movements (product_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, 'ADJUSTMENT', ?, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, new BigDecimal(quantity), UUID.randomUUID(), actorId);
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Adjustment " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("ADJ-" + suffix, null, "Adjustment Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("adj_" + token(), "Adjustment Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user, final String permission) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority(permission)));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }
}

package com.bizco.server.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderItemResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSearchResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSummaryResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.manufacturing.application.BillOfMaterialsService;
import com.bizco.server.manufacturing.application.ProductionService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BOM-PROD-001..003 (DevelopmentPlan.md Week 21 task 21.5). */
class ProductionServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private ProductionService productionService;
    @Autowired
    private BillOfMaterialsService bomService;
    @Autowired
    private CatalogService catalogService;
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

    @Test
    void bomProd001StockedProductionConsumesComponentsAndIncreasesFinishedStock() {
        final User user = createUser();
        final ProductDetailResponse frameProduct = createProduct("8.00");
        final UUID frame = variantOf(frameProduct);
        final UUID finished = variantOf(createProduct("0.00"));
        seedStock(frame, "10.000", user);

        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("2.000"), new BigDecimal("0.500")),
                auth(user));

        final IdempotentResult<ProductionOrderResponse> result = productionService.produce(UUID.randomUUID(),
                new ProduceRequest(bom.bomId(), new BigDecimal("3.000"), "STOCKED", "batch 1"), auth(user));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().productionNumber()).isNotBlank();
        // (2.000 + 0.500 wastage) * 3 produced = 7.500 consumed.
        assertThat(stockQueryService.levelForVariant(frame).physicalStock()).isEqualByComparingTo("2.500");
        assertThat(stockQueryService.levelForVariant(finished).physicalStock()).isEqualByComparingTo("3.000");

        // SRS.md §6.4.11.4: actual production cost includes wastage even though the BOM's own
        // estimate does not - the captured unitCostAtProduction/totalCost/totalComponentCost must
        // reflect the same wastage-inclusive 7.500, not the wastage-excluded 6.000 the estimate uses.
        final ProductionOrderResponse response = result.response();
        assertThat(response.items()).hasSize(1);
        final ProductionOrderItemResponse frameLine = response.items().get(0);
        assertThat(frameLine.componentVariantId()).isEqualTo(frame);
        assertThat(frameLine.quantityConsumed()).isEqualByComparingTo("7.500");
        assertThat(frameLine.unitCostAtProduction()).isEqualByComparingTo("8.00");
        assertThat(frameLine.totalCost()).isEqualByComparingTo("60.00");
        assertThat(response.totalComponentCost()).isEqualByComparingTo("60.00");
        assertThat(response.totalComponentCost()).isEqualByComparingTo(response.items().stream()
                .map(ProductionOrderItemResponse::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void bomProd002MadeToOrderProductionConsumesComponentsButNeverStocksTheFinishedVariant() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        seedStock(frame, "10.000", user);

        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));

        productionService.produce(UUID.randomUUID(),
                new ProduceRequest(bom.bomId(), new BigDecimal("2.000"), "MADE_TO_ORDER", null), auth(user));

        assertThat(stockQueryService.levelForVariant(frame).physicalStock()).isEqualByComparingTo("8.000");
        assertThat(stockQueryService.levelForVariant(finished).physicalStock()).isEqualByComparingTo("0.000");
    }

    @Test
    void bomProd003InsufficientComponentStockRejectsTheWholeProductionAndReplayIsIdempotent() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID glass = variantOf(createProduct("3.50"));
        final UUID finished = variantOf(createProduct("0.00"));
        seedStock(frame, "10.000", user);
        seedStock(glass, "1.000", user);

        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(glass, new BigDecimal("1.000"), null), auth(user));

        final UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> productionService.produce(key,
                new ProduceRequest(bom.bomId(), new BigDecimal("5.000"), "STOCKED", null), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.STOCK_INSUFFICIENT);

        // Never partial: frame had enough for 5 units, glass did not - frame must be untouched too.
        assertThat(stockQueryService.levelForVariant(frame).physicalStock()).isEqualByComparingTo("10.000");
        assertThat(stockQueryService.levelForVariant(glass).physicalStock()).isEqualByComparingTo("1.000");
    }

    @Test
    void produceIsIdempotentUnderTheSameKey() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        seedStock(frame, "10.000", user);
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));

        final UUID key = UUID.randomUUID();
        final ProduceRequest request = new ProduceRequest(bom.bomId(), new BigDecimal("2.000"), "STOCKED", null);
        final IdempotentResult<ProductionOrderResponse> first = productionService.produce(key, request, auth(user));
        final IdempotentResult<ProductionOrderResponse> second = productionService.produce(key, request, auth(user));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().productionOrderId()).isEqualTo(first.response().productionOrderId());
        assertThat(stockQueryService.levelForVariant(frame).physicalStock()).isEqualByComparingTo("8.000");
    }

    @Test
    void bomProd004SearchAndGetReturnTheProductionOrder() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        seedStock(frame, "10.000", user);

        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));

        final IdempotentResult<ProductionOrderResponse> produced = productionService.produce(UUID.randomUUID(),
                new ProduceRequest(bom.bomId(), new BigDecimal("2.000"), "STOCKED", "search test"), auth(user));
        final UUID productionOrderId = produced.response().productionOrderId();

        final ProductionOrderSearchResponse search = productionService.search(bom.bomId(), null, 0, 100);
        assertThat(search.data()).extracting(ProductionOrderSummaryResponse::productionOrderId)
                .contains(productionOrderId);

        final ProductionOrderResponse detail = productionService.get(productionOrderId);
        assertThat(detail.productionOrderId()).isEqualTo(productionOrderId);
        assertThat(detail.productionNumber()).isEqualTo(produced.response().productionNumber());
        assertThat(detail.bomId()).isEqualTo(bom.bomId());
    }

    private UUID variantOf(final ProductDetailResponse product) {
        return jdbc.queryForObject(
                "select product_variant_id from product_variants where product_id = ? and is_default = true",
                UUID.class, product.productId());
    }

    private ProductDetailResponse createProduct(final String costPrice) {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Prod " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("PRD-" + suffix, null, "Prod Part " + suffix, null,
                category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal(costPrice),
                new BigDecimal("100.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private void seedStock(final UUID variantId, final String quantity, final User actor) {
        final UUID productId = jdbc.queryForObject(
                "select product_id from product_variants where product_variant_id = ?", UUID.class, variantId);
        jdbc.update("""
                insert into stock_movements (product_id, product_variant_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, ?, 'ADJUSTMENT', ?, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, variantId, new BigDecimal(quantity), UUID.randomUUID(), actor.getId());
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        final User user = new User("prd_" + token(), "Production Tester", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a", java.util.List.of(
                new SimpleGrantedAuthority("manufacturing.bom.manage"), new SimpleGrantedAuthority("manufacturing.produce")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", java.util.List.of(new SimpleGrantedAuthority(permission)));
    }
}

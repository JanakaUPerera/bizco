package com.bizco.server.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.manufacturing.application.BillOfMaterialsService;
import com.bizco.server.manufacturing.application.ProductionService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** BOM-CON-001 (DevelopmentPlan.md Week 21 task 21.5): the component variant row lock
 *  ({@code StockPostingService.lockVariants}), not any application-level pre-check, is what a
 *  concurrent over-produce attempt must be unable to get past. Mirrors {@code StockConcurrencyIT}:
 *  each attempt runs in its own thread and its own transaction. */
class ProductionConcurrencyIT extends PostgresIntegrationTest {

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
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void bomCon001ExactlyOneConcurrentProductionOfTheLastComponentUnitCommits() throws Exception {
        final int attempts = 8;
        final User user = inTransaction(this::createUser);
        final ProductDetailResponse frameProduct = inTransaction(() -> createProduct("8.00"));
        final UUID frame = inTransaction(() -> variantOf(frameProduct));
        final ProductDetailResponse finishedProduct = inTransaction(() -> createProduct("0.00"));
        final UUID finished = inTransaction(() -> variantOf(finishedProduct));
        inTransaction(() -> {
            seedStock(frame, "1.000", user);
            return null;
        });
        final UUID bomId = inTransaction(() -> {
            final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
            bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));
            return bom.bomId();
        });

        final ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            final List<Callable<Object>> tasks = IntStream.range(0, attempts)
                    .<Callable<Object>>mapToObj(i -> () -> attemptProduce(user, bomId)).toList();
            final List<Object> results = pool.invokeAll(tasks).stream().map(this::get).toList();

            final long succeeded = results.stream().filter(r -> r instanceof UUID).count();
            final long insufficientStock = results.stream().filter(IdentityException.class::isInstance)
                    .map(IdentityException.class::cast)
                    .filter(ex -> ex.getCode() == ApiErrorCode.STOCK_INSUFFICIENT).count();

            assertThat(succeeded).isEqualTo(1);
            assertThat(insufficientStock).isEqualTo(attempts - 1);
            assertThat(stockQueryService.levelForVariant(frame).physicalStock()).isEqualByComparingTo("0.000");
            assertThat(stockQueryService.levelForVariant(finished).physicalStock()).isEqualByComparingTo("1.000");
        } finally {
            pool.shutdown();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    private Object attemptProduce(final User user, final UUID bomId) {
        try {
            return inTransaction(() -> productionService.produce(UUID.randomUUID(),
                    new ProduceRequest(bomId, BigDecimal.ONE, "STOCKED", null), auth(user)).response().productionOrderId());
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private UUID variantOf(final ProductDetailResponse product) {
        return jdbc.queryForObject(
                "select product_variant_id from product_variants where product_id = ? and is_default = true",
                UUID.class, product.productId());
    }

    private ProductDetailResponse createProduct(final String costPrice) {
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final var category = catalogService.createCategory(new CategoryCreateRequest("Con " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("CONM-" + suffix, null, "Con Part " + suffix, null,
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
        final User user = new User("con_" + UUID.randomUUID().toString().substring(0, 8), "Concurrency Producer",
                passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a", List.of(
                new SimpleGrantedAuthority("manufacturing.bom.manage"), new SimpleGrantedAuthority("manufacturing.produce")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }

    private <T> T inTransaction(final Supplier<T> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private <T> T get(final java.util.concurrent.Future<T> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

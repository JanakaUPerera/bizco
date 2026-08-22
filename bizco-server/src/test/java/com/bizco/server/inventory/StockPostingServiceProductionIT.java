package com.bizco.server.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Phase 7 Week 20 task 20.1's stock-ledger half: PRODUCTION_IN/PRODUCTION_OUT are new movement
 *  types posted through the same {@code StockPostingService} contract every other module uses. */
class StockPostingServiceProductionIT extends PostgresIntegrationTest {

    @Autowired
    private StockPostingService stockPostingService;
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
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void productionInIncreasesFinishedVariantStock() {
        final UUID finishedVariantId = inTransaction(this::createDefaultVariant);
        final UUID actorId = inTransaction(this::createUser);
        final UUID productionOrderId = UUID.randomUUID();

        inTransaction(() -> {
            stockPostingService.lockVariant(finishedVariantId);
            stockPostingService.postProductionIn(finishedVariantId, productionOrderId, new BigDecimal("5.000"), actorId);
            return null;
        });

        assertThat(stockQueryService.levelForVariant(finishedVariantId).physicalStock())
                .isEqualByComparingTo("5.000");
    }

    @Test
    void productionOutDecreasesComponentVariantStockAndRequiresAvailability() {
        final UUID componentVariantId = inTransaction(this::createDefaultVariant);
        final UUID actorId = inTransaction(this::createUser);
        inTransaction(() -> {
            seedStock(componentVariantId, "3.000", actorId);
            return null;
        });
        final UUID productionOrderId = UUID.randomUUID();
        final UUID productionOrderItemId = UUID.randomUUID();

        inTransaction(() -> {
            stockPostingService.lockVariant(componentVariantId);
            stockPostingService.requireAvailable(componentVariantId, new BigDecimal("3.000"));
            stockPostingService.postProductionOut(componentVariantId, productionOrderId, productionOrderItemId,
                    new BigDecimal("3.000"), actorId);
            return null;
        });

        assertThat(stockQueryService.levelForVariant(componentVariantId).physicalStock())
                .isEqualByComparingTo("0.000");
    }

    private UUID createDefaultVariant() {
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final var category = catalogService.createCategory(new CategoryCreateRequest("Mfg " + suffix, null, null),
                auth());
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final ProductDetailResponse product = catalogService.createProduct(new ProductCreateRequest("MFG-" + suffix,
                null, "Mfg Widget " + suffix, null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD",
                new BigDecimal("10.00"), new BigDecimal("100.00"), null, new BigDecimal("2.000"), null), auth());
        return jdbc.queryForObject(
                "select product_variant_id from product_variants where product_id = ? and is_default = true",
                UUID.class, product.productId());
    }

    private UUID createUser() {
        final Role role = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        final User user = new User("mfg_" + UUID.randomUUID().toString().substring(0, 8), "Mfg Tester",
                passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user).getId();
    }

    private void seedStock(final UUID variantId, final String quantity, final UUID actorId) {
        final UUID productId = jdbc.queryForObject(
                "select product_id from product_variants where product_variant_id = ?", UUID.class, variantId);
        jdbc.update("""
                insert into stock_movements (product_id, product_variant_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, ?, 'ADJUSTMENT', ?, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, variantId, new BigDecimal(quantity), UUID.randomUUID(), actorId);
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("superadmin", "n/a",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("product.create"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("product.category.create")));
    }

    private <T> T inTransaction(final java.util.function.Supplier<T> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }
}

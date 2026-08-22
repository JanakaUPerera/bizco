package com.bizco.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceUpdateRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CatalogWeek5PostgresIT extends PostgresIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void uomSeedAndProductBarcodeUniquenessWork() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Cables " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final var p1 = catalogService.createProduct(product("SKU-" + suffix + "-A", "BC-" + suffix, category.categoryId(),
                pcs, "USB Cable"), auth("product.create"));

        assertThat(stockMovementRows()).isZero();
        assertThat(catalogService.barcode("BC-" + suffix, false).product().productId()).isEqualTo(p1.productId());
        assertThat(catalogService.getProduct(p1.productId(), false).costPrice()).isNull();
        assertThat(catalogService.getProduct(p1.productId(), true).costPrice()).isEqualByComparingTo("10.00");

        assertThatThrownBy(() -> catalogService.createProduct(product("SKU-" + suffix + "-B", "BC-" + suffix,
                category.categoryId(), pcs, "Duplicate Barcode"), auth("product.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.PRODUCT_BARCODE_DUPLICATE);

        catalogService.createProduct(product("SKU-" + suffix + "-C", null, category.categoryId(), pcs,
                "Null Barcode A"), auth("product.create"));
        catalogService.createProduct(product("SKU-" + suffix + "-D", null, category.categoryId(), pcs,
                "Null Barcode B"), auth("product.create"));
    }

    @Test
    void duplicateSkuPriceValidationAndOptimisticLockingWork() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Adapters " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final var product = catalogService.createProduct(product("DUP-" + suffix, null, category.categoryId(), pcs,
                "Adapter"), auth("product.create"));

        assertThatThrownBy(() -> catalogService.createProduct(product("DUP-" + suffix, null, category.categoryId(), pcs,
                "Duplicate SKU"), auth("product.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.PRODUCT_SKU_DUPLICATE);

        assertThatThrownBy(() -> catalogService.createProduct(new ProductCreateRequest("BAD-" + suffix, null, "Bad",
                null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal("-1.00"),
                BigDecimal.ONE, null, BigDecimal.ZERO, null), auth("product.create")))
                .isInstanceOf(ApiValidationException.class);

        final ProductUpdateRequest stale = new ProductUpdateRequest(product.sku(), null, product.name(), null,
                category.categoryId(), null, pcs, "INVENTORY", "STANDARD", BigDecimal.TEN, BigDecimal.ONE, null,
                BigDecimal.ZERO, true, null, product.version() + 99);
        assertThatThrownBy(() -> catalogService.updateProduct(product.productId(), stale, auth("product.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void categoryCycleAndServiceValidationWork() {
        final String suffix = token();
        final var parent = catalogService.createCategory(new CategoryCreateRequest("Parent " + suffix, null, null),
                auth("product.category.create"));
        final var child = catalogService.createCategory(new CategoryCreateRequest("Child " + suffix,
                parent.categoryId(), null), auth("product.category.create"));

        assertThatThrownBy(() -> catalogService.updateCategory(parent.categoryId(), new CategoryUpdateRequest(
                parent.name(), child.categoryId(), null, true, parent.version()), auth("product.category.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CATEGORY_CYCLE);

        assertThatThrownBy(() -> catalogService.createService(new ServiceCreateRequest("SVC-BAD-" + suffix, "Bad",
                null, "Repair", BigDecimal.ONE, 0, false, 0), auth("service.create")))
                .isInstanceOf(ApiValidationException.class);

        final var service = catalogService.createService(new ServiceCreateRequest("SVC-" + suffix, "Install",
                null, "Repair", new BigDecimal("100.00"), 60, true, 30), auth("service.create"));
        assertThatThrownBy(() -> catalogService.updateService(service.serviceId(), new ServiceUpdateRequest(
                service.serviceCode(), service.name(), null, service.category(), service.basePrice(),
                service.estimatedDurationMinutes(), service.requiresEstimate(), service.warrantyDays(),
                true, service.version() + 1), auth("service.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void indexesRequiredForSearchAndBarcodeExist() {
        assertThat(jdbc.queryForObject("select count(*) from uom where code in ('PCS','KG','LTR','BOX','DOZ','BTL','CASE','MTR')",
                Long.class)).isEqualTo(8);
        assertThat(indexExists("uq_products_sku")).isTrue();
        assertThat(indexExists("uq_products_barcode")).isTrue();
        assertThat(indexExists("idx_products_name_trgm")).isTrue();
        assertThat(indexExists("idx_products_category_active")).isTrue();
    }

    private boolean indexExists(final String name) {
        final Integer count = jdbc.queryForObject("select count(*) from pg_indexes where indexname = ?", Integer.class, name);
        return count != null && count == 1;
    }

    private long stockMovementRows() {
        final String table = jdbc.queryForObject("select to_regclass('public.stock_movements')", String.class);
        if (table == null) {
            return 0;
        }
        return jdbc.queryForObject("select count(*) from stock_movements", Long.class);
    }

    private ProductCreateRequest product(final String sku, final String barcode, final Long categoryId,
                                         final Long uomId, final String name) {
        return new ProductCreateRequest(sku, barcode, name, null, categoryId, null, uomId, "INVENTORY",
                "STANDARD", new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("18.00"),
                new BigDecimal("2.000"), null);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

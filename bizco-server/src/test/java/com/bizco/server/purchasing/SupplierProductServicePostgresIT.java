package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductUpdateRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.SupplierProductService;
import com.bizco.server.purchasing.application.SupplierService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** PUR-SUPPROD-001..003 (DevelopmentPlan.md Week 13). */
class SupplierProductServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private SupplierProductService supplierProductService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void purSupprod001CreateAndSearchCatalogEntry() {
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();

        final SupplierProductResponse created = supplierProductService.create(
                new SupplierProductCreateRequest(supplier.supplierId(), product.productId(), "SUP-SKU-1",
                        new BigDecimal("45.00"), new BigDecimal("10.000"), 7, true), auth());

        assertThat(created.supplierId()).isEqualTo(supplier.supplierId());
        assertThat(created.productId()).isEqualTo(product.productId());
        assertThat(created.purchasePrice()).isEqualByComparingTo("45.00");
        assertThat(created.preferred()).isTrue();

        final var results = supplierProductService.search(supplier.supplierId(), null, 0, 20);
        assertThat(results.data()).extracting(SupplierProductResponse::supplierProductId)
                .contains(created.supplierProductId());
    }

    @Test
    void purSupprod002DuplicateSupplierProductPairIsRejected() {
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        supplierProductService.create(new SupplierProductCreateRequest(supplier.supplierId(), product.productId(),
                null, new BigDecimal("10.00"), BigDecimal.ONE, null, false), auth());

        assertThatThrownBy(() -> supplierProductService.create(new SupplierProductCreateRequest(supplier.supplierId(),
                product.productId(), null, new BigDecimal("12.00"), BigDecimal.ONE, null, false), auth()))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.SUPPLIER_PRODUCT_DUPLICATE);
    }

    @Test
    void purSupprod003OnlyOnePreferredSupplierPerProduct() {
        final SupplierDetailResponse supplierA = createSupplier();
        final SupplierDetailResponse supplierB = createSupplier();
        final ProductDetailResponse product = createProduct();
        final SupplierProductResponse first = supplierProductService.create(new SupplierProductCreateRequest(
                supplierA.supplierId(), product.productId(), null, new BigDecimal("10.00"), BigDecimal.ONE, null, true),
                auth());

        // Marking a second supplier preferred for the same product demotes the first one instead of
        // being rejected - see SupplierProductService.demoteExistingPreferred's Javadoc.
        final SupplierProductResponse second = supplierProductService.create(new SupplierProductCreateRequest(
                supplierB.supplierId(), product.productId(), null, new BigDecimal("9.00"), BigDecimal.ONE, null, true),
                auth());
        assertThat(second.preferred()).isTrue();

        final var results = supplierProductService.search(null, product.productId(), 0, 20);
        assertThat(results.data()).filteredOn(SupplierProductResponse::preferred)
                .extracting(SupplierProductResponse::supplierProductId).containsExactly(second.supplierProductId());

        final SupplierProductResponse updated = supplierProductService.update(second.supplierProductId(),
                new SupplierProductUpdateRequest(second.supplierSku(), second.purchasePrice(), second.minOrderQty(),
                        second.leadTimeDays(), false, second.version()), auth());
        assertThat(updated.preferred()).isFalse();
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-PO-" + suffix, "Purchasing Test Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth());
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Purchasing " + suffix, null, null),
                auth());
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("PUR-" + suffix, null, "Purchasing Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth());
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a",
                List.of(new SimpleGrantedAuthority("supplier.create"), new SimpleGrantedAuthority("product.create"),
                        new SimpleGrantedAuthority("product.category.create"),
                        new SimpleGrantedAuthority("purchasing.supplier_product.create"),
                        new SimpleGrantedAuthority("purchasing.supplier_product.update")));
    }
}

package com.bizco.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.BrandCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.BrandUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryAttributeAssignRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
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

/** Phase 6 Week 16 (DevelopmentPlan.md Week 16, DatabaseDesign.md §55): brands, dynamic
 *  attributes, and category-attribute assignment. Covers acceptance IDs CAT-BRAND-001..002,
 *  CAT-ATTR-001..003 (docs/AcceptanceTests.md §10). */
class BrandAttributePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void brandCreateUpdateDuplicateAndOptimisticLockingWork() {
        final String suffix = token();
        final var brand = catalogService.createBrand(new BrandCreateRequest("Brand " + suffix, "Desc", null),
                auth("product.brand.create"));
        assertThat(brand.active()).isTrue();

        assertThatThrownBy(() -> catalogService.createBrand(new BrandCreateRequest("Brand " + suffix, null, null),
                auth("product.brand.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.BRAND_NAME_DUPLICATE);

        final var updated = catalogService.updateBrand(brand.brandId(), new BrandUpdateRequest("Brand " + suffix
                + " Updated", "New desc", "/logo.png", false, brand.version()), auth("product.brand.update"));
        assertThat(updated.active()).isFalse();
        assertThat(updated.logoPath()).isEqualTo("/logo.png");

        final BrandUpdateRequest stale = new BrandUpdateRequest("Stale", null, null, true, brand.version());
        assertThatThrownBy(() -> catalogService.updateBrand(brand.brandId(), stale, auth("product.brand.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void attributeCreatePerDataTypeAndDuplicateNameWork() {
        final String suffix = token();
        final var text = catalogService.createAttribute(new AttributeCreateRequest("Material " + suffix, "TEXT"),
                auth("product.attribute.create"));
        assertThat(text.dataType()).isEqualTo("TEXT");
        catalogService.createAttribute(new AttributeCreateRequest("Weight " + suffix, "NUMBER"),
                auth("product.attribute.create"));
        catalogService.createAttribute(new AttributeCreateRequest("Fragile " + suffix, "BOOLEAN"),
                auth("product.attribute.create"));

        assertThatThrownBy(() -> catalogService.createAttribute(new AttributeCreateRequest("Material " + suffix,
                "TEXT"), auth("product.attribute.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.ATTRIBUTE_NAME_DUPLICATE);

        assertThatThrownBy(() -> catalogService.createAttribute(new AttributeCreateRequest("Bad " + suffix, "MADE_UP"),
                auth("product.attribute.create")))
                .isInstanceOf(ApiValidationException.class);
    }

    @Test
    void attributeValuesOnlyAllowedForEnumAttributesAndRejectDuplicates() {
        final String suffix = token();
        final var color = catalogService.createAttribute(new AttributeCreateRequest("Color " + suffix, "ENUM"),
                auth("product.attribute.create"));
        final var red = catalogService.addAttributeValue(color.attributeId(), new AttributeValueCreateRequest("Red"),
                auth("product.attribute.create"));
        assertThat(red.value()).isEqualTo("Red");
        assertThat(catalogService.attributeValues(color.attributeId())).hasSize(1);

        assertThatThrownBy(() -> catalogService.addAttributeValue(color.attributeId(),
                new AttributeValueCreateRequest("Red"), auth("product.attribute.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.ATTRIBUTE_VALUE_DUPLICATE);

        final var material = catalogService.createAttribute(new AttributeCreateRequest("Material " + suffix, "TEXT"),
                auth("product.attribute.create"));
        assertThatThrownBy(() -> catalogService.addAttributeValue(material.attributeId(),
                new AttributeValueCreateRequest("Steel"), auth("product.attribute.create")))
                .isInstanceOf(ApiValidationException.class);
    }

    @Test
    void categoryAttributeAssignmentUnassignAndDuplicateWork() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Phones " + suffix, null, null),
                auth("product.category.create"));
        final var ram = catalogService.createAttribute(new AttributeCreateRequest("RAM " + suffix, "TEXT"),
                auth("product.attribute.create"));

        final var assignment = catalogService.assignCategoryAttribute(category.categoryId(),
                new CategoryAttributeAssignRequest(ram.attributeId(), true), auth("product.attribute.update"));
        assertThat(assignment.required()).isTrue();
        assertThat(catalogService.categoryAttributes(category.categoryId())).hasSize(1);

        assertThatThrownBy(() -> catalogService.assignCategoryAttribute(category.categoryId(),
                new CategoryAttributeAssignRequest(ram.attributeId(), false), auth("product.attribute.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CATEGORY_ATTRIBUTE_DUPLICATE);

        catalogService.unassignCategoryAttribute(category.categoryId(), ram.attributeId(), auth("product.attribute.update"));
        assertThat(catalogService.categoryAttributes(category.categoryId())).isEmpty();

        assertThatThrownBy(() -> catalogService.unassignCategoryAttribute(category.categoryId(), ram.attributeId(),
                auth("product.attribute.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CATEGORY_ATTRIBUTE_NOT_FOUND);
    }

    @Test
    void productWithAndWithoutBrandRemainsValid() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Gadgets " + suffix, null, null),
                auth("product.category.create"));
        final var brand = catalogService.createBrand(new BrandCreateRequest("Gadget Brand " + suffix, null, null),
                auth("product.brand.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);

        final var withBrand = catalogService.createProduct(new ProductCreateRequest("BR-" + suffix, null, "Branded",
                null, category.categoryId(), brand.brandId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, BigDecimal.ZERO, null), auth("product.create"));
        assertThat(withBrand.brandId()).isEqualTo(brand.brandId());
        assertThat(withBrand.brandName()).isEqualTo(brand.name());

        // MVP.md §4.8: a product's brand is optional; products without a brand remain valid.
        final var withoutBrand = catalogService.createProduct(new ProductCreateRequest("NB-" + suffix, null,
                "Unbranded", null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, BigDecimal.ZERO, null), auth("product.create"));
        assertThat(withoutBrand.brandId()).isNull();
        assertThat(withoutBrand.brandName()).isNull();

        final var rebranded = catalogService.updateProduct(withoutBrand.productId(), new ProductUpdateRequest(
                withoutBrand.sku(), null, withoutBrand.name(), null, category.categoryId(), brand.brandId(), pcs,
                "INVENTORY", "STANDARD", BigDecimal.TEN, BigDecimal.TEN, null, BigDecimal.ZERO, true, null,
                withoutBrand.version()), auth("product.update"));
        assertThat(rebranded.brandId()).isEqualTo(brand.brandId());
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

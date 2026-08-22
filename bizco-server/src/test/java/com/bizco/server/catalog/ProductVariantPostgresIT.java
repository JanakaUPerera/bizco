package com.bizco.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryAttributeAssignRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantAttributeSelection;
import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantGenerateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.catalog.application.VariantService;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Phase 6 Week 17 (DevelopmentPlan.md Week 17 task 17.6, DatabaseDesign.md §56). Covers
 *  acceptance IDs VAR-SCHEMA-001..002, VAR-BACKFILL-001..002 (docs/AcceptanceTests.md §10). */
class ProductVariantPostgresIT extends PostgresIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private VariantService variantService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createProductAutoCreatesExactlyOneDefaultVariant() {
        final String suffix = token();
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Cat " + suffix,
                null, null), auth("product.category.create"));
        final ProductDetailResponse product = catalogService.createProduct(product("SKU-" + suffix, category.categoryId(),
                pcs), auth("product.create"));

        final List<VariantResponse> variants = variantService.listVariants(product.productId());
        assertThat(variants).hasSize(1);
        final VariantResponse defaultVariant = variants.get(0);
        assertThat(defaultVariant.defaultVariant()).isTrue();
        assertThat(defaultVariant.sku()).isEqualTo(product.sku());
        assertThat(defaultVariant.sellingPrice()).isEqualByComparingTo(product.sellingPrice());
        assertThat(defaultVariant.variantLabel()).isEqualTo(product.name());
    }

    @Test
    void updateProductSyncsDefaultVariantWhileSingleThenStops() {
        final String suffix = token();
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Cat " + suffix,
                null, null), auth("product.category.create"));
        final ProductDetailResponse product = catalogService.createProduct(product("SYNC-" + suffix, category.categoryId(),
                pcs), auth("product.create"));

        final ProductDetailResponse updated = catalogService.updateProduct(product.productId(), updateOf(product,
                new BigDecimal("30.00")), auth("product.update"));
        VariantResponse defaultVariant = variantService.listVariants(product.productId()).get(0);
        assertThat(defaultVariant.sellingPrice()).isEqualByComparingTo("30.00");

        // Generate a second variant -- from here the product becomes a style/parent and the sync stops.
        final AttributeResponse color = catalogService.createAttribute(new AttributeCreateRequest("Color " + suffix,
                "ENUM"), auth("product.attribute.create"));
        final AttributeValueResponse red = catalogService.addAttributeValue(color.attributeId(),
                new AttributeValueCreateRequest("Red"), auth("product.attribute.create"));
        catalogService.assignCategoryAttribute(category.categoryId(), new CategoryAttributeAssignRequest(
                color.attributeId(), false), auth("product.attribute.update"));
        variantService.generateVariants(product.productId(), new VariantGenerateRequest(List.of(
                new VariantAttributeSelection(color.attributeId(), List.of(red.attributeValueId())))),
                auth("product.variant.create"));

        catalogService.updateProduct(product.productId(), updateOf(updated, new BigDecimal("45.00")),
                auth("product.update"));
        defaultVariant = variantService.listVariants(product.productId()).stream()
                .filter(VariantResponse::defaultVariant).findFirst().orElseThrow();
        assertThat(defaultVariant.sellingPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void generateVariantsProducesCartesianCombinationsAndRejectsDuplicateLabel() {
        final String suffix = token();
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Apparel " + suffix,
                null, null), auth("product.category.create"));
        final ProductDetailResponse product = catalogService.createProduct(product("SHIRT-" + suffix, category.categoryId(),
                pcs), auth("product.create"));

        final AttributeResponse color = catalogService.createAttribute(new AttributeCreateRequest("Color " + suffix,
                "ENUM"), auth("product.attribute.create"));
        final AttributeValueResponse red = catalogService.addAttributeValue(color.attributeId(),
                new AttributeValueCreateRequest("Red"), auth("product.attribute.create"));
        final AttributeValueResponse blue = catalogService.addAttributeValue(color.attributeId(),
                new AttributeValueCreateRequest("Blue"), auth("product.attribute.create"));
        final AttributeResponse size = catalogService.createAttribute(new AttributeCreateRequest("Size " + suffix,
                "ENUM"), auth("product.attribute.create"));
        final AttributeValueResponse small = catalogService.addAttributeValue(size.attributeId(),
                new AttributeValueCreateRequest("S"), auth("product.attribute.create"));
        final AttributeValueResponse medium = catalogService.addAttributeValue(size.attributeId(),
                new AttributeValueCreateRequest("M"), auth("product.attribute.create"));
        catalogService.assignCategoryAttribute(category.categoryId(), new CategoryAttributeAssignRequest(
                color.attributeId(), true), auth("product.attribute.update"));
        catalogService.assignCategoryAttribute(category.categoryId(), new CategoryAttributeAssignRequest(
                size.attributeId(), true), auth("product.attribute.update"));

        final List<VariantResponse> generated = variantService.generateVariants(product.productId(),
                new VariantGenerateRequest(List.of(
                        new VariantAttributeSelection(color.attributeId(), List.of(red.attributeValueId(), blue.attributeValueId())),
                        new VariantAttributeSelection(size.attributeId(), List.of(small.attributeValueId(), medium.attributeValueId())))),
                auth("product.variant.create"));

        assertThat(generated).hasSize(4);
        assertThat(generated.stream().map(VariantResponse::variantLabel)).containsExactlyInAnyOrder(
                "Red / S", "Red / M", "Blue / S", "Blue / M");
        assertThat(variantService.listVariants(product.productId())).hasSize(5); // 1 default + 4 generated

        assertThatThrownBy(() -> variantService.generateVariants(product.productId(), new VariantGenerateRequest(List.of(
                        new VariantAttributeSelection(color.attributeId(), List.of(red.attributeValueId())),
                        new VariantAttributeSelection(size.attributeId(), List.of(small.attributeValueId())))),
                auth("product.variant.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.VARIANT_LABEL_DUPLICATE);
    }

    @Test
    void generateVariantsRejectsNonEnumAndUnassignedAttributes() {
        final String suffix = token();
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Tools " + suffix,
                null, null), auth("product.category.create"));
        final ProductDetailResponse product = catalogService.createProduct(product("TOOL-" + suffix, category.categoryId(),
                pcs), auth("product.create"));

        final AttributeResponse material = catalogService.createAttribute(new AttributeCreateRequest("Material " + suffix,
                "TEXT"), auth("product.attribute.create"));
        catalogService.assignCategoryAttribute(category.categoryId(), new CategoryAttributeAssignRequest(
                material.attributeId(), false), auth("product.attribute.update"));
        assertThatThrownBy(() -> variantService.generateVariants(product.productId(), new VariantGenerateRequest(
                        List.of(new VariantAttributeSelection(material.attributeId(), List.of(1L)))),
                auth("product.variant.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.VARIANT_ATTRIBUTE_NOT_ENUM);

        final AttributeResponse unassigned = catalogService.createAttribute(new AttributeCreateRequest("Finish " + suffix,
                "ENUM"), auth("product.attribute.create"));
        final AttributeValueResponse matte = catalogService.addAttributeValue(unassigned.attributeId(),
                new AttributeValueCreateRequest("Matte"), auth("product.attribute.create"));
        assertThatThrownBy(() -> variantService.generateVariants(product.productId(), new VariantGenerateRequest(
                        List.of(new VariantAttributeSelection(unassigned.attributeId(), List.of(matte.attributeValueId())))),
                auth("product.variant.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.VARIANT_ATTRIBUTE_NOT_ASSIGNED);
    }

    @Test
    void manualVariantCreateDuplicateSkuAndOptimisticLockingWork() {
        final String suffix = token();
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Bags " + suffix,
                null, null), auth("product.category.create"));
        final ProductDetailResponse product = catalogService.createProduct(product("BAG-" + suffix, category.categoryId(),
                pcs), auth("product.create"));

        final VariantResponse variant = variantService.createVariant(product.productId(), new VariantCreateRequest(
                        "BAG-" + suffix + "-XL", null, "XL", new BigDecimal("10.00"), new BigDecimal("25.00"), null,
                        BigDecimal.ZERO, null),
                auth("product.variant.create"));
        assertThat(variant.defaultVariant()).isFalse();

        assertThatThrownBy(() -> variantService.createVariant(product.productId(), new VariantCreateRequest(
                        "BAG-" + suffix + "-XL", null, "XL Duplicate", new BigDecimal("10.00"), new BigDecimal("25.00"),
                        null, BigDecimal.ZERO, null),
                auth("product.variant.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.VARIANT_SKU_DUPLICATE);

        final VariantUpdateRequest stale = new VariantUpdateRequest(variant.sku(), null, "XL", new BigDecimal("10.00"),
                new BigDecimal("28.00"), null, BigDecimal.ZERO, true, null, variant.version() + 99);
        assertThatThrownBy(() -> variantService.updateVariant(variant.productVariantId(), stale, auth("product.variant.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);

        assertThatThrownBy(() -> variantService.createVariant(product.productId(), new VariantCreateRequest(
                        "", null, "Bad", new BigDecimal("-1.00"), new BigDecimal("25.00"), null, BigDecimal.ZERO, null),
                auth("product.variant.create")))
                .isInstanceOf(ApiValidationException.class);
    }

    @Test
    void productVariantSchemaIndexesAndVariantIdColumnsExist() {
        assertThat(indexExists("uq_product_variants_default")).isTrue();
        assertThat(indexExists("uq_product_variants_sku")).isTrue();
        assertThat(indexExists("uq_product_variants_barcode")).isTrue();

        final List<String> tablesWithVariantColumn = List.of("invoice_lines", "held_sale_items", "job_parts",
                "stock_movements", "stock_adjustments", "supplier_products", "purchase_order_items",
                "goods_receipt_items", "supplier_return_items", "product_cost_history");
        for (final String table : tablesWithVariantColumn) {
            final Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.columns where table_name = ? and column_name = 'product_variant_id'",
                    Integer.class, table);
            assertThat(count).as("table %s should have a product_variant_id column", table).isEqualTo(1);
        }
        // credit_note_lines deliberately has neither product_id nor product_variant_id -- it
        // resolves the product via original_invoice_line_id -> invoice_lines (see V027 header comment).
        final Integer creditNoteLinesVariantColumn = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'credit_note_lines' and column_name = 'product_variant_id'",
                Integer.class);
        assertThat(creditNoteLinesVariantColumn).isZero();
    }

    private boolean indexExists(final String name) {
        final Integer count = jdbc.queryForObject("select count(*) from pg_indexes where indexname = ?", Integer.class, name);
        return count != null && count == 1;
    }

    private ProductCreateRequest product(final String sku, final Long categoryId, final Long uomId) {
        return new ProductCreateRequest(sku, null, sku + " Name", null, categoryId, null, uomId, "INVENTORY",
                "STANDARD", new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("18.00"),
                BigDecimal.ZERO, null);
    }

    private ProductUpdateRequest updateOf(final ProductDetailResponse p, final BigDecimal sellingPrice) {
        return new ProductUpdateRequest(p.sku(), p.barcode(), p.name(), p.description(), p.categoryId(), p.brandId(),
                p.uomId(), p.productType(), p.taxCategory(), p.costPrice() == null ? BigDecimal.ZERO : p.costPrice(),
                sellingPrice, p.wholesalePrice(), p.reorderPoint(), p.active(), p.imagePath(), p.version());
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

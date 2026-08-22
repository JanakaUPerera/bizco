package com.bizco.server.catalog.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantAttributeSelection;
import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantGenerateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Attribute;
import com.bizco.server.catalog.domain.AttributeDataType;
import com.bizco.server.catalog.domain.AttributeValue;
import com.bizco.server.catalog.domain.CategoryAttribute;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.domain.VariantAttributeValue;
import com.bizco.server.catalog.infrastructure.AttributeValueRepository;
import com.bizco.server.catalog.infrastructure.CategoryAttributeRepository;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
import com.bizco.server.catalog.infrastructure.VariantAttributeValueRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phase 6 Week 17 (DatabaseDesign.md §56, DevelopmentPlan.md Week 17). Kept separate from
 *  {@link CatalogService} (already 650+ lines after Week 16's brand/attribute additions) — the
 *  attribute-driven generation logic here is meaningfully different from simple lookup CRUD and
 *  will keep growing through Weeks 18-19.
 *
 *  <p>Design: every product has exactly one default variant ({@link #createDefaultVariant}),
 *  kept in sync with the product's own sku/barcode/pricing fields for as long as the product has
 *  no other variant ({@link #syncDefaultVariantIfSingle}). Once a second variant exists (manual
 *  {@link #createVariant} or {@link #generateVariants}), the product becomes a style/parent and
 *  variants are priced/edited independently — {@code updateProduct} stops touching variant rows. */
@Service
public class VariantService {

    private final ProductVariantRepository variantRepository;
    private final VariantAttributeValueRepository variantAttributeValueRepository;
    private final ProductRepository productRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public VariantService(final ProductVariantRepository variantRepository,
                          final VariantAttributeValueRepository variantAttributeValueRepository,
                          final ProductRepository productRepository,
                          final CategoryAttributeRepository categoryAttributeRepository,
                          final AttributeValueRepository attributeValueRepository,
                          final AuditService auditService, final UserRepository userRepository) {
        this.variantRepository = variantRepository;
        this.variantAttributeValueRepository = variantAttributeValueRepository;
        this.productRepository = productRepository;
        this.categoryAttributeRepository = categoryAttributeRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    /** Called by {@code CatalogService.createProduct} right after the product is saved. */
    @Transactional
    public void createDefaultVariant(final Product product, final ProductCreateRequest request) {
        try {
            variantRepository.saveAndFlush(new ProductVariant(product, request.sku(), request.barcode(),
                    product.getName(), money(request.costPrice()), money(request.sellingPrice()),
                    request.wholesalePrice(), money(request.reorderPoint()), request.imagePath(), true));
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateVariant(ex);
        }
    }

    /** Called by {@code CatalogService.updateProduct}; a no-op once the product has more than one
     *  variant (task 17.5's generated/manual variants are edited independently from then on). */
    @Transactional
    public void syncDefaultVariantIfSingle(final Product product, final ProductUpdateRequest request) {
        if (variantRepository.countByProductId(product.getId()) > 1) {
            return;
        }
        final ProductVariant defaultVariant = variantRepository.findByProductIdAndDefaultVariantTrue(product.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Product " + product.getId() + " has no default variant; Week 17 invariant violated."));
        try {
            defaultVariant.update(request.sku(), request.barcode(), product.getName(), money(request.costPrice()),
                    money(request.sellingPrice()), request.wholesalePrice(), money(request.reorderPoint()),
                    request.active(), request.imagePath());
            variantRepository.flush();
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateVariant(ex);
        }
    }

    /** Phase 6 Week 19 (task 19.1): the more specific of the two barcode lookups
     *  {@code CatalogService.barcode} tries - a variant's own barcode, set independently of its
     *  parent product's via {@link #updateVariant}. */
    @Transactional(readOnly = true)
    public Optional<ProductVariant> findByBarcode(final String barcode) {
        return variantRepository.findByBarcode(barcode);
    }

    /** Phase 6 Week 18: resolves the id CatalogService's product listing/barcode-lookup pass to
     *  {@link com.bizco.server.catalog.application.ProductStockQueryPort} — stock now lives at
     *  variant granularity, and this is the common "product has only its default variant" case.
     *  Batched (one query per page), not one lookup per row. */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> defaultVariantIds(final Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return variantRepository.findAllByProductIdInAndDefaultVariantTrue(productIds).stream()
                .collect(Collectors.toMap(v -> v.getProduct().getId(), ProductVariant::getId));
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> listVariants(final UUID productId) {
        product(productId);
        return variantRepository.findAllByProductIdOrderByVariantLabelAsc(productId).stream()
                .map(this::variantResponse).toList();
    }

    @Transactional
    public VariantResponse createVariant(final UUID productId, final VariantCreateRequest request,
                                         final Authentication authentication) {
        final Product product = product(productId);
        validateVariant(request.sku(), request.sellingPrice(), request.costPrice(), request.wholesalePrice(),
                request.reorderPoint());
        try {
            final ProductVariant saved = variantRepository.saveAndFlush(new ProductVariant(product, request.sku(),
                    request.barcode(), request.variantLabel(), money(request.costPrice()),
                    money(request.sellingPrice()), request.wholesalePrice(), money(request.reorderPoint()),
                    request.imagePath(), false));
            auditService.record("PRODUCT_VARIANT", saved.getId().toString(), "VARIANT_CREATED", actor(authentication),
                    Map.of("sku", saved.getSku()));
            return variantResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateVariant(ex);
        }
    }

    @Transactional
    public VariantResponse updateVariant(final UUID variantId, final VariantUpdateRequest request,
                                         final Authentication authentication) {
        final ProductVariant variant = variant(variantId);
        assertVersion(variant.getVersion(), request.version(), "The variant was modified by another user.");
        validateVariant(request.sku(), request.sellingPrice(), request.costPrice(), request.wholesalePrice(),
                request.reorderPoint());
        try {
            variant.update(request.sku(), request.barcode(), request.variantLabel(), money(request.costPrice()),
                    money(request.sellingPrice()), request.wholesalePrice(), money(request.reorderPoint()),
                    request.active(), request.imagePath());
            variantRepository.flush();
            auditService.record("PRODUCT_VARIANT", variantId.toString(), "VARIANT_UPDATED", actor(authentication),
                    Map.of("sku", variant.getSku()));
            return variantResponse(variant);
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateVariant(ex);
        }
    }

    /** Attribute-driven cartesian generation (task 17.5, e.g. Color × Size). Restricted to
     *  {@code ENUM} attributes assigned to the product's category (Week 16's
     *  {@code category_attributes}/{@code attribute_values}) since only those have a discrete,
     *  enumerable value list — {@code TEXT}/{@code NUMBER}/{@code BOOLEAN} attributes are entered
     *  freely per variant, not generated combinatorially (DatabaseDesign.md §55.2). Generated
     *  variants inherit the product's current pricing as a starting point (placeholder SKU/price
     *  the user refines afterward in the variant list) and are rejected if the computed label
     *  would collide with a variant that already exists. */
    @Transactional
    public List<VariantResponse> generateVariants(final UUID productId, final VariantGenerateRequest request,
                                                   final Authentication authentication) {
        final Product product = product(productId);
        if (request.selections() == null || request.selections().isEmpty()) {
            throw new IdentityException(ApiErrorCode.VARIANT_GENERATE_EMPTY_SELECTION, HttpStatus.BAD_REQUEST,
                    "At least one attribute selection is required.");
        }
        final Map<Long, CategoryAttribute> assigned = categoryAttributeRepository
                .findAllByCategoryIdOrderByAttributeNameAsc(product.getCategory().getId()).stream()
                .collect(Collectors.toMap(ca -> ca.getAttribute().getId(), ca -> ca));

        final List<Attribute> axisAttributes = new ArrayList<>();
        final List<List<AttributeValue>> axisValues = new ArrayList<>();
        for (final VariantAttributeSelection selection : request.selections()) {
            final CategoryAttribute categoryAttribute = assigned.get(selection.attributeId());
            if (categoryAttribute == null) {
                throw new IdentityException(ApiErrorCode.VARIANT_ATTRIBUTE_NOT_ASSIGNED, HttpStatus.CONFLICT,
                        "Attribute is not assigned to this product's category.");
            }
            final Attribute attribute = categoryAttribute.getAttribute();
            if (attribute.getDataType() != AttributeDataType.ENUM) {
                throw new IdentityException(ApiErrorCode.VARIANT_ATTRIBUTE_NOT_ENUM, HttpStatus.CONFLICT,
                        "Only ENUM attributes can be used for variant generation.");
            }
            if (selection.attributeValueIds() == null || selection.attributeValueIds().isEmpty()) {
                throw new IdentityException(ApiErrorCode.VARIANT_GENERATE_EMPTY_SELECTION, HttpStatus.BAD_REQUEST,
                        "At least one value is required for attribute " + attribute.getName() + ".");
            }
            final List<AttributeValue> values = selection.attributeValueIds().stream()
                    .map(id -> attributeValue(id, attribute.getId())).toList();
            axisAttributes.add(attribute);
            axisValues.add(values);
        }

        final Set<String> existingLabels = variantRepository.findAllByProductIdOrderByVariantLabelAsc(productId)
                .stream().map(ProductVariant::getVariantLabel).collect(Collectors.toSet());
        final List<List<AttributeValue>> combinations = cartesianProduct(axisValues);
        final List<ProductVariant> created = new ArrayList<>();
        // Every product already has its default variant (createDefaultVariant/backfill V026), so
        // this count is always >= 1; the suffix simply continues numbering from there.
        int suffix = (int) variantRepository.countByProductId(productId) + 1;
        for (final List<AttributeValue> combination : combinations) {
            final String label = combination.stream().map(AttributeValue::getValue)
                    .collect(Collectors.joining(" / "));
            if (existingLabels.contains(label)) {
                throw new IdentityException(ApiErrorCode.VARIANT_LABEL_DUPLICATE, HttpStatus.CONFLICT,
                        "A variant labeled \"" + label + "\" already exists for this product.");
            }
            try {
                final ProductVariant variant = variantRepository.saveAndFlush(new ProductVariant(product,
                        product.getSku() + "-" + suffix++, null, label, product.getCostPrice(),
                        product.getSellingPrice(), product.getWholesalePrice(), product.getReorderPoint(), null,
                        false));
                for (int axis = 0; axis < axisAttributes.size(); axis++) {
                    variantAttributeValueRepository.save(VariantAttributeValue.forEnumValue(variant,
                            axisAttributes.get(axis), combination.get(axis)));
                }
                created.add(variant);
            } catch (final DataIntegrityViolationException ex) {
                throw duplicateVariant(ex);
            }
        }
        auditService.record("PRODUCT_VARIANT", productId.toString(), "VARIANTS_GENERATED", actor(authentication),
                Map.of("count", String.valueOf(created.size())));
        return created.stream().map(this::variantResponse).toList();
    }

    private List<List<AttributeValue>> cartesianProduct(final List<List<AttributeValue>> axes) {
        List<List<AttributeValue>> result = List.of(List.of());
        for (final List<AttributeValue> axis : axes) {
            final List<List<AttributeValue>> next = new ArrayList<>();
            for (final List<AttributeValue> partial : result) {
                for (final AttributeValue value : axis) {
                    final List<AttributeValue> combination = new ArrayList<>(partial);
                    combination.add(value);
                    next.add(combination);
                }
            }
            result = next;
        }
        return result;
    }

    private void validateVariant(final String sku, final BigDecimal sellingPrice, final BigDecimal costPrice,
                                 final BigDecimal wholesalePrice, final BigDecimal reorderPoint) {
        final List<FieldError> errors = new ArrayList<>();
        if (sku == null || sku.isBlank()) errors.add(new FieldError("sku", "REQUIRED", "SKU is required."));
        nonNegative(errors, "sellingPrice", sellingPrice);
        nonNegative(errors, "costPrice", costPrice);
        nonNegative(errors, "wholesalePrice", wholesalePrice);
        nonNegative(errors, "reorderPoint", reorderPoint);
        if (sellingPrice == null) errors.add(new FieldError("sellingPrice", "REQUIRED", "Selling price is required."));
        if (!errors.isEmpty()) throw new ApiValidationException(errors);
    }

    private void nonNegative(final List<FieldError> errors, final String field, final BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new FieldError(field, "NEGATIVE", field + " must be zero or greater."));
        }
    }

    private Product product(final UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Product was not found."));
    }

    private ProductVariant variant(final UUID id) {
        return variantRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Variant was not found."));
    }

    private AttributeValue attributeValue(final Long id, final Long expectedAttributeId) {
        final AttributeValue value = attributeValueRepository.findById(id)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.ATTRIBUTE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Attribute value was not found."));
        if (!value.getAttribute().getId().equals(expectedAttributeId)) {
            throw new ApiValidationException(List.of(new FieldError("attributeValueIds", "MISMATCHED",
                    "Attribute value does not belong to the selected attribute.")));
        }
        return value;
    }

    private IdentityException duplicateVariant(final DataIntegrityViolationException ex) {
        final String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_product_variants_barcode")) {
            return new IdentityException(ApiErrorCode.VARIANT_BARCODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Variant barcode already exists.");
        }
        return new IdentityException(ApiErrorCode.VARIANT_SKU_DUPLICATE, HttpStatus.CONFLICT,
                "Variant SKU already exists.");
    }

    private VariantResponse variantResponse(final ProductVariant v) {
        return new VariantResponse(v.getId(), v.getProduct().getId(), v.getSku(), v.getBarcode(),
                v.getVariantLabel(), v.getCostPrice(), v.getSellingPrice(), v.getWholesalePrice(),
                v.getReorderPoint(), v.isActive(), v.getImagePath(), v.isDefault(), v.getCreatedAt(),
                v.getUpdatedAt(), v.getVersion());
    }

    private void assertVersion(final long current, final long expected, final String message) {
        if (current != expected) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT, message);
        }
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private BigDecimal money(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}

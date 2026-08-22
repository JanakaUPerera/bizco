package com.bizco.server.catalog.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueResponse;
import com.bizco.common.dto.catalog.CatalogDtos.BrandCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.BrandResponse;
import com.bizco.common.dto.catalog.CatalogDtos.BrandUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryAttributeAssignRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryAttributeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductBarcodeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductPriceResolutionResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.UomResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Attribute;
import com.bizco.server.catalog.domain.AttributeDataType;
import com.bizco.server.catalog.domain.AttributeValue;
import com.bizco.server.catalog.domain.Brand;
import com.bizco.server.catalog.domain.CategoryAttribute;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductCategory;
import com.bizco.server.catalog.domain.ProductType;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.domain.ServiceDefinition;
import com.bizco.server.catalog.domain.TaxCategory;
import com.bizco.server.catalog.domain.Uom;
import com.bizco.server.catalog.infrastructure.AttributeRepository;
import com.bizco.server.catalog.infrastructure.AttributeValueRepository;
import com.bizco.server.catalog.infrastructure.BrandRepository;
import com.bizco.server.catalog.infrastructure.CategoryAttributeRepository;
import com.bizco.server.catalog.infrastructure.ProductCategoryRepository;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.catalog.infrastructure.ServiceDefinitionRepository;
import com.bizco.server.catalog.infrastructure.UomRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ProductCategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final ProductRepository productRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final BrandRepository brandRepository;
    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final VariantService variantService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ProductStockQueryPort stockQueryPort;

    public CatalogService(final ProductCategoryRepository categoryRepository, final UomRepository uomRepository,
                          final ProductRepository productRepository,
                          final ServiceDefinitionRepository serviceRepository, final BrandRepository brandRepository,
                          final AttributeRepository attributeRepository,
                          final AttributeValueRepository attributeValueRepository,
                          final CategoryAttributeRepository categoryAttributeRepository,
                          final VariantService variantService,
                          final AuditService auditService, final UserRepository userRepository,
                          final ProductStockQueryPort stockQueryPort) {
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.productRepository = productRepository;
        this.serviceRepository = serviceRepository;
        this.brandRepository = brandRepository;
        this.attributeRepository = attributeRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.categoryAttributeRepository = categoryAttributeRepository;
        this.variantService = variantService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.stockQueryPort = stockQueryPort;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(this::categoryResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(final CategoryCreateRequest request, final Authentication authentication) {
        validateCategory(request.name());
        final ProductCategory parent = parent(request.parentId());
        final ProductCategory saved = categoryRepository.save(new ProductCategory(request.name(), parent,
                request.description()));
        auditService.record("PRODUCT_CATEGORY", saved.getId().toString(), "CATEGORY_CREATED", actor(authentication),
                Map.of("name", saved.getName()));
        return categoryResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(final Long id, final CategoryUpdateRequest request,
                                           final Authentication authentication) {
        validateCategory(request.name());
        final ProductCategory category = category(id);
        assertVersion(category.getVersion(), request.version(), "The category was modified by another user.");
        final ProductCategory parent = parent(request.parentId());
        preventCycle(category, parent);
        category.update(request.name(), parent, request.description(), request.active());
        auditService.record("PRODUCT_CATEGORY", id.toString(), "CATEGORY_UPDATED", actor(authentication),
                Map.of("name", category.getName()));
        return categoryResponse(category);
    }

    @Transactional(readOnly = true)
    public List<UomResponse> uom() {
        return uomRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(u -> new UomResponse(u.getId(), u.getCode(), u.getName(), u.getCategory(), u.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> brands() {
        return brandRepository.findAllByOrderByNameAsc().stream().map(this::brandResponse).toList();
    }

    @Transactional
    public BrandResponse createBrand(final BrandCreateRequest request, final Authentication authentication) {
        validateBrand(request.name());
        try {
            final Brand saved = brandRepository.saveAndFlush(new Brand(request.name(), request.description(),
                    request.logoPath()));
            auditService.record("BRAND", saved.getId().toString(), "BRAND_CREATED", actor(authentication),
                    Map.of("name", saved.getName()));
            return brandResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.BRAND_NAME_DUPLICATE, HttpStatus.CONFLICT,
                    "Brand name already exists.");
        }
    }

    @Transactional
    public BrandResponse updateBrand(final Long id, final BrandUpdateRequest request,
                                     final Authentication authentication) {
        validateBrand(request.name());
        final Brand brand = brand(id);
        assertVersion(brand.getVersion(), request.version(), "The brand was modified by another user.");
        try {
            brand.update(request.name(), request.description(), request.logoPath(), request.active());
            brandRepository.flush();
            auditService.record("BRAND", id.toString(), "BRAND_UPDATED", actor(authentication),
                    Map.of("name", brand.getName()));
            return brandResponse(brand);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.BRAND_NAME_DUPLICATE, HttpStatus.CONFLICT,
                    "Brand name already exists.");
        }
    }

    @Transactional(readOnly = true)
    public List<AttributeResponse> attributes() {
        return attributeRepository.findAllByOrderByNameAsc().stream().map(this::attributeResponse).toList();
    }

    @Transactional
    public AttributeResponse createAttribute(final AttributeCreateRequest request,
                                             final Authentication authentication) {
        final AttributeDataType dataType = validateAttribute(request.name(), request.dataType());
        try {
            final Attribute saved = attributeRepository.saveAndFlush(new Attribute(request.name(), dataType));
            auditService.record("ATTRIBUTE", saved.getId().toString(), "ATTRIBUTE_CREATED", actor(authentication),
                    Map.of("name", saved.getName()));
            return attributeResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.ATTRIBUTE_NAME_DUPLICATE, HttpStatus.CONFLICT,
                    "Attribute name already exists.");
        }
    }

    @Transactional(readOnly = true)
    public List<AttributeValueResponse> attributeValues(final Long attributeId) {
        // Confirms the attribute exists before returning an (possibly empty) value list, matching
        // category()/service()'s not-found behaviour elsewhere in this service.
        attribute(attributeId);
        return attributeValueRepository.findAllByAttributeIdOrderByValueAsc(attributeId).stream()
                .map(this::attributeValueResponse).toList();
    }

    @Transactional
    public AttributeValueResponse addAttributeValue(final Long attributeId, final AttributeValueCreateRequest request,
                                                     final Authentication authentication) {
        final Attribute attribute = attribute(attributeId);
        if (attribute.getDataType() != AttributeDataType.ENUM) {
            throw new ApiValidationException(List.of(new FieldError("attributeId", "NOT_ENUM",
                    "Attribute values can only be added to an ENUM attribute.")));
        }
        if (request.value() == null || request.value().isBlank()) {
            throw new ApiValidationException(List.of(new FieldError("value", "REQUIRED", "Value is required.")));
        }
        try {
            final AttributeValue saved = attributeValueRepository.saveAndFlush(new AttributeValue(attribute,
                    request.value()));
            auditService.record("ATTRIBUTE", attributeId.toString(), "ATTRIBUTE_VALUE_ADDED", actor(authentication),
                    Map.of("value", saved.getValue()));
            return attributeValueResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.ATTRIBUTE_VALUE_DUPLICATE, HttpStatus.CONFLICT,
                    "This value already exists for the attribute.");
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryAttributeResponse> categoryAttributes(final Long categoryId) {
        category(categoryId);
        return categoryAttributeRepository.findAllByCategoryIdOrderByAttributeNameAsc(categoryId).stream()
                .map(this::categoryAttributeResponse).toList();
    }

    @Transactional
    public CategoryAttributeResponse assignCategoryAttribute(final Long categoryId,
                                                              final CategoryAttributeAssignRequest request,
                                                              final Authentication authentication) {
        final ProductCategory category = category(categoryId);
        final Attribute attribute = attribute(request.attributeId());
        if (categoryAttributeRepository.existsByCategoryIdAndAttributeId(categoryId, request.attributeId())) {
            throw new IdentityException(ApiErrorCode.CATEGORY_ATTRIBUTE_DUPLICATE, HttpStatus.CONFLICT,
                    "This attribute is already assigned to the category.");
        }
        final CategoryAttribute saved = categoryAttributeRepository.save(new CategoryAttribute(category, attribute,
                request.required()));
        auditService.record("PRODUCT_CATEGORY", categoryId.toString(), "CATEGORY_ATTRIBUTE_ASSIGNED",
                actor(authentication), Map.of("attribute", attribute.getName()));
        return categoryAttributeResponse(saved);
    }

    @Transactional
    public void unassignCategoryAttribute(final Long categoryId, final Long attributeId,
                                          final Authentication authentication) {
        final CategoryAttribute link = categoryAttributeRepository.findByCategoryIdAndAttributeId(categoryId, attributeId)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.CATEGORY_ATTRIBUTE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "This attribute is not assigned to the category."));
        categoryAttributeRepository.delete(link);
        auditService.record("PRODUCT_CATEGORY", categoryId.toString(), "CATEGORY_ATTRIBUTE_UNASSIGNED",
                actor(authentication), Map.of("attribute", link.getAttribute().getName()));
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> products(final String q, final Long categoryId, final String type,
                                                 final Boolean active, final int page, final int size,
                                                 final boolean includeCost) {
        return products(q, categoryId, type, active, null, null, page, size, includeCost);
    }

    /** Phase 6 Week 19 (task 19.5): {@code brandId}/{@code attributeValueId} are additive filters -
     *  both null keeps today's behavior. {@code attributeValueId} matches a product having at
     *  least one variant carrying that ENUM value (attributes live at variant granularity - §56.1),
     *  so it's a single value pick, not a full multi-attribute facet filter. */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> products(final String q, final Long categoryId, final String type,
                                                 final Boolean active, final Long brandId,
                                                 final Long attributeValueId, final int page, final int size,
                                                 final boolean includeCost) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<Product> result = productRepository.search(blankToNull(q), categoryId, nullableProductType(type),
                active, brandId, attributeValueId, pageable);
        // One batched stock lookup for the whole page (ProductStockQueryPort's Javadoc) instead of
        // one query per row. Phase 6 Week 18: stock lives at variant granularity now, so resolve
        // each product's default variant first (Week 19's variant-aware screens do real per-variant
        // display; this keeps today's product list showing accurate numbers for the common case).
        final Set<UUID> inventoryProductIds = result.getContent().stream()
                .filter(p -> p.getProductType() == ProductType.INVENTORY).map(Product::getId).collect(Collectors.toSet());
        final Map<UUID, UUID> defaultVariantIds = variantService.defaultVariantIds(inventoryProductIds);
        final Map<UUID, ProductStockLevel> levels = stockQueryPort.levelsFor(defaultVariantIds.values());
        return result.map(product -> productSummary(product, includeCost,
                levels.get(defaultVariantIds.get(product.getId()))));
    }

    @Transactional
    public ProductDetailResponse createProduct(final ProductCreateRequest request, final Authentication authentication) {
        validateProduct(request.sku(), request.name(), request.categoryId(), request.uomId(), request.productType(),
                request.taxCategory(), request.costPrice(), request.sellingPrice(), request.wholesalePrice(),
                request.reorderPoint());
        try {
            final Product product = new Product(request.sku(), request.barcode(), request.name(), request.description(),
                    category(request.categoryId()), brandOrNull(request.brandId()), uomEntity(request.uomId()),
                    productType(request.productType()), taxCategory(request.taxCategory()), money(request.costPrice()),
                    money(request.sellingPrice()), request.wholesalePrice(), quantity(request.reorderPoint()),
                    request.imagePath());
            final Product saved = productRepository.saveAndFlush(product);
            // Phase 6 Week 17 (DatabaseDesign.md §56.1): every product gets exactly one default
            // variant, created here so the invariant holds from the moment the product exists.
            variantService.createDefaultVariant(saved, request);
            auditService.record("PRODUCT", saved.getId().toString(), "PRODUCT_CREATED", actor(authentication),
                    Map.of("sku", saved.getSku()));
            return productDetail(saved, true);
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateProduct(ex);
        }
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(final UUID id, final boolean includeCost) {
        return productDetail(product(id), includeCost);
    }

    /** Phase 6 Week 19 (task 19.1): tries the more specific match first - a variant's own barcode -
     *  before falling back to the product-level barcode every product still carries. See {@link
     *  com.bizco.common.dto.catalog.CatalogDtos.ProductBarcodeResponse}'s Javadoc for how the
     *  caller (POS) is expected to use {@code variantSpecific} to decide whether to prompt. */
    @Transactional(readOnly = true)
    public ProductBarcodeResponse barcode(final String barcode, final boolean includeCost) {
        final Optional<ProductVariant> variantMatch = variantService.findByBarcode(barcode)
                .filter(v -> v.isActive() && v.getProduct().isActive());
        if (variantMatch.isPresent()) {
            final ProductVariant variant = variantMatch.get();
            final Product product = variant.getProduct();
            final ProductStockLevel level = stockQueryPort.levelsFor(Set.of(variant.getId())).get(variant.getId());
            return new ProductBarcodeResponse(productSummary(product, includeCost, level), variant.getId(), true);
        }
        final Product product = productRepository.findByBarcode(barcode)
                .filter(Product::isActive)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product was not found."));
        final UUID defaultVariantId = variantService.defaultVariantIds(Set.of(product.getId())).get(product.getId());
        final ProductStockLevel level = defaultVariantId == null ? null
                : stockQueryPort.levelsFor(Set.of(defaultVariantId)).get(defaultVariantId);
        return new ProductBarcodeResponse(productSummary(product, includeCost, level), defaultVariantId, false);
    }

    @Transactional
    public ProductDetailResponse updateProduct(final UUID id, final ProductUpdateRequest request,
                                               final Authentication authentication) {
        validateProduct(request.sku(), request.name(), request.categoryId(), request.uomId(), request.productType(),
                request.taxCategory(), request.costPrice(), request.sellingPrice(), request.wholesalePrice(),
                request.reorderPoint());
        final Product product = product(id);
        assertVersion(product.getVersion(), request.version(), "The product was modified by another user.");
        final BigDecimal oldCost = product.getCostPrice();
        final BigDecimal oldSelling = product.getSellingPrice();
        final BigDecimal oldWholesale = product.getWholesalePrice();
        try {
            product.update(request.sku(), request.barcode(), request.name(), request.description(),
                    category(request.categoryId()), brandOrNull(request.brandId()), uomEntity(request.uomId()),
                    productType(request.productType()), taxCategory(request.taxCategory()), money(request.costPrice()),
                    money(request.sellingPrice()), request.wholesalePrice(), quantity(request.reorderPoint()),
                    request.active(), request.imagePath());
            productRepository.flush();
            // Phase 6 Week 17: keeps the default variant in lockstep while the product is still
            // single-variant; a no-op once task 17.5's generation/manual variants exist (VariantService
            // javadoc).
            variantService.syncDefaultVariantIfSingle(product, request);
            auditService.record("PRODUCT", id.toString(), "PRODUCT_UPDATED", actor(authentication),
                    Map.of("sku", product.getSku()));
            if (!same(oldCost, product.getCostPrice()) || !same(oldSelling, product.getSellingPrice())
                    || !same(oldWholesale, product.getWholesalePrice())) {
                auditService.record("PRODUCT", id.toString(), "PRODUCT_PRICE_CHANGED", actor(authentication),
                        Map.of("sku", product.getSku()));
            }
            return productDetail(product, true);
        } catch (final DataIntegrityViolationException ex) {
            throw duplicateProduct(ex);
        }
    }

    @Transactional
    public ProductDetailResponse activateProduct(final UUID id, final Authentication authentication) {
        final Product product = product(id);
        product.activate();
        auditService.record("PRODUCT", id.toString(), "PRODUCT_ACTIVATED", actor(authentication),
                Map.of("sku", product.getSku()));
        return productDetail(product, true);
    }

    @Transactional
    public ProductDetailResponse deactivateProduct(final UUID id, final Authentication authentication) {
        final Product product = product(id);
        product.deactivate();
        auditService.record("PRODUCT", id.toString(), "PRODUCT_DEACTIVATED", actor(authentication),
                Map.of("sku", product.getSku()));
        return productDetail(product, true);
    }

    @Transactional(readOnly = true)
    public ProductPriceResolutionResponse resolvePrice(final UUID id, final String tier) {
        final Product product = product(id);
        final boolean wholesale = "WHOLESALE".equalsIgnoreCase(tier) && product.getWholesalePrice() != null;
        return new ProductPriceResolutionResponse(id, wholesale ? "WHOLESALE" : "RETAIL",
                wholesale ? product.getWholesalePrice() : product.getSellingPrice());
    }

    @Transactional(readOnly = true)
    public Page<ServiceResponse> services(final String q, final Boolean active, final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return serviceRepository.search(blankToNull(q), active, pageable).map(this::serviceResponse);
    }

    @Transactional
    public ServiceResponse createService(final ServiceCreateRequest request, final Authentication authentication) {
        validateService(request.serviceCode(), request.name(), request.basePrice(), request.estimatedDurationMinutes(),
                request.warrantyDays());
        try {
            final ServiceDefinition saved = serviceRepository.saveAndFlush(new ServiceDefinition(request.serviceCode(),
                    request.name(), request.description(), request.category(), money(request.basePrice()),
                    request.estimatedDurationMinutes(), request.requiresEstimate(), integer(request.warrantyDays())));
            auditService.record("SERVICE", saved.getId().toString(), "SERVICE_CREATED", actor(authentication),
                    Map.of("serviceCode", saved.getServiceCode()));
            return serviceResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.SERVICE_CODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Service code already exists.");
        }
    }

    @Transactional(readOnly = true)
    public ServiceResponse getService(final UUID id) {
        return serviceResponse(service(id));
    }

    @Transactional
    public ServiceResponse updateService(final UUID id, final ServiceUpdateRequest request,
                                         final Authentication authentication) {
        validateService(request.serviceCode(), request.name(), request.basePrice(), request.estimatedDurationMinutes(),
                request.warrantyDays());
        final ServiceDefinition service = service(id);
        assertVersion(service.getVersion(), request.version(), "The service was modified by another user.");
        try {
            service.update(request.serviceCode(), request.name(), request.description(), request.category(),
                    money(request.basePrice()), request.estimatedDurationMinutes(), request.requiresEstimate(),
                    integer(request.warrantyDays()), request.active());
            serviceRepository.flush();
            auditService.record("SERVICE", id.toString(), "SERVICE_UPDATED", actor(authentication),
                    Map.of("serviceCode", service.getServiceCode()));
            return serviceResponse(service);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.SERVICE_CODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Service code already exists.");
        }
    }

    @Transactional
    public ServiceResponse activateService(final UUID id, final Authentication authentication) {
        final ServiceDefinition service = service(id);
        service.activate();
        auditService.record("SERVICE", id.toString(), "SERVICE_ACTIVATED", actor(authentication),
                Map.of("serviceCode", service.getServiceCode()));
        return serviceResponse(service);
    }

    @Transactional
    public ServiceResponse deactivateService(final UUID id, final Authentication authentication) {
        final ServiceDefinition service = service(id);
        service.deactivate();
        auditService.record("SERVICE", id.toString(), "SERVICE_DEACTIVATED", actor(authentication),
                Map.of("serviceCode", service.getServiceCode()));
        return serviceResponse(service);
    }

    private void validateCategory(final String name) {
        if (name == null || name.isBlank()) {
            throw new ApiValidationException(List.of(new FieldError("name", "REQUIRED", "Name is required.")));
        }
    }

    private void validateBrand(final String name) {
        if (name == null || name.isBlank()) {
            throw new ApiValidationException(List.of(new FieldError("name", "REQUIRED", "Name is required.")));
        }
    }

    /** Returns the parsed {@link AttributeDataType}; throws {@link ApiValidationException} for a
     *  blank name or an unrecognised data type rather than letting the JPA {@code CHECK} constraint
     *  surface as an opaque 500/constraint-violation. */
    private AttributeDataType validateAttribute(final String name, final String dataType) {
        final List<FieldError> errors = new ArrayList<>();
        required(errors, "name", name);
        AttributeDataType parsed = null;
        try {
            parsed = dataType == null ? null : AttributeDataType.valueOf(dataType.trim().toUpperCase());
        } catch (final RuntimeException ex) {
            parsed = null;
        }
        if (parsed == null) errors.add(new FieldError("dataType", "INVALID", "Data type is invalid."));
        if (!errors.isEmpty()) throw new ApiValidationException(errors);
        return parsed;
    }

    private void validateProduct(final String sku, final String name, final Long categoryId, final Long uomId,
                                 final String productType, final String taxCategory, final BigDecimal costPrice,
                                 final BigDecimal sellingPrice, final BigDecimal wholesalePrice,
                                 final BigDecimal reorderPoint) {
        final List<FieldError> errors = new ArrayList<>();
        required(errors, "sku", sku);
        required(errors, "name", name);
        if (categoryId == null) errors.add(new FieldError("categoryId", "REQUIRED", "Category is required."));
        if (uomId == null) errors.add(new FieldError("uomId", "REQUIRED", "UOM is required."));
        parseProductType(errors, productType);
        parseTaxCategory(errors, taxCategory);
        nonNegative(errors, "costPrice", costPrice);
        nonNegative(errors, "sellingPrice", sellingPrice);
        nonNegative(errors, "wholesalePrice", wholesalePrice);
        nonNegative(errors, "reorderPoint", reorderPoint);
        if (!errors.isEmpty()) throw new ApiValidationException(errors);
    }

    private void validateService(final String code, final String name, final BigDecimal basePrice,
                                 final Integer duration, final Integer warrantyDays) {
        final List<FieldError> errors = new ArrayList<>();
        required(errors, "serviceCode", code);
        required(errors, "name", name);
        nonNegative(errors, "basePrice", basePrice);
        if (duration == null || duration <= 0) {
            errors.add(new FieldError("estimatedDurationMinutes", "POSITIVE", "Duration must be greater than zero."));
        }
        if (warrantyDays != null && warrantyDays < 0) {
            errors.add(new FieldError("warrantyDays", "NEGATIVE", "Warranty days must be zero or greater."));
        }
        if (!errors.isEmpty()) throw new ApiValidationException(errors);
    }

    private void preventCycle(final ProductCategory category, final ProductCategory parent) {
        ProductCategory cursor = parent;
        while (cursor != null) {
            if (category.getId().equals(cursor.getId())) {
                throw new IdentityException(ApiErrorCode.CATEGORY_CYCLE, HttpStatus.CONFLICT,
                        "Category hierarchy cannot contain a cycle.");
            }
            cursor = cursor.getParent();
        }
    }

    private ProductCategory parent(final Long id) {
        return id == null ? null : category(id);
    }

    private ProductCategory category(final Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.CATEGORY_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Category was not found."));
    }

    private Uom uomEntity(final Long id) {
        return uomRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.UOM_NOT_FOUND,
                HttpStatus.NOT_FOUND, "UOM was not found."));
    }

    private Brand brandOrNull(final Long id) {
        return id == null ? null : brand(id);
    }

    private Brand brand(final Long id) {
        return brandRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.BRAND_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Brand was not found."));
    }

    private Attribute attribute(final Long id) {
        return attributeRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.ATTRIBUTE_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Attribute was not found."));
    }

    private Product product(final UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Product was not found."));
    }

    private ServiceDefinition service(final UUID id) {
        return serviceRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Service was not found."));
    }

    private IdentityException duplicateProduct(final DataIntegrityViolationException ex) {
        final String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_products_barcode")) {
            return new IdentityException(ApiErrorCode.PRODUCT_BARCODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Product barcode already exists.");
        }
        return new IdentityException(ApiErrorCode.PRODUCT_SKU_DUPLICATE, HttpStatus.CONFLICT,
                "Product SKU already exists.");
    }

    private ProductType productType(final String value) { return ProductType.valueOf(value.trim().toUpperCase()); }
    private TaxCategory taxCategory(final String value) { return TaxCategory.valueOf(value.trim().toUpperCase()); }
    private ProductType nullableProductType(final String value) {
        return value == null || value.isBlank() ? null : productType(value);
    }

    private void parseProductType(final List<FieldError> errors, final String value) {
        try { productType(value == null || value.isBlank() ? "INVENTORY" : value); }
        catch (final RuntimeException ex) { errors.add(new FieldError("productType", "INVALID", "Product type is invalid.")); }
    }

    private void parseTaxCategory(final List<FieldError> errors, final String value) {
        try { taxCategory(value == null || value.isBlank() ? "STANDARD" : value); }
        catch (final RuntimeException ex) { errors.add(new FieldError("taxCategory", "INVALID", "Tax category is invalid.")); }
    }

    private CategoryResponse categoryResponse(final ProductCategory c) {
        final ProductCategory parent = c.getParent();
        return new CategoryResponse(c.getId(), c.getName(), parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(), c.getDescription(), c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private BrandResponse brandResponse(final Brand b) {
        return new BrandResponse(b.getId(), b.getName(), b.getDescription(), b.getLogoPath(), b.isActive(),
                b.getCreatedAt(), b.getUpdatedAt(), b.getVersion());
    }

    private AttributeResponse attributeResponse(final Attribute a) {
        return new AttributeResponse(a.getId(), a.getName(), a.getDataType().name(), a.getCreatedAt());
    }

    private AttributeValueResponse attributeValueResponse(final AttributeValue v) {
        return new AttributeValueResponse(v.getId(), v.getAttribute().getId(), v.getValue());
    }

    private CategoryAttributeResponse categoryAttributeResponse(final CategoryAttribute ca) {
        return new CategoryAttributeResponse(ca.getId(), ca.getCategory().getId(), ca.getAttribute().getId(),
                ca.getAttribute().getName(), ca.getAttribute().getDataType().name(), ca.isRequired());
    }

    /** {@code level} is null for a SERVICE-type product (not stock-tracked) or when the caller
     *  didn't ask {@link ProductStockQueryPort} about this product; the response fields stay null
     *  either way, matching {@code v_available_stock}'s own INVENTORY-only scope. */
    private ProductSummaryResponse productSummary(final Product p, final boolean includeCost, final ProductStockLevel level) {
        final Brand brand = p.getBrand();
        return new ProductSummaryResponse(p.getId(), p.getSku(), p.getBarcode(), p.getName(),
                p.getCategory().getName(), brand == null ? null : brand.getName(), p.getUom().getCode(),
                p.getProductType().name(), p.getTaxCategory().name(), p.getSellingPrice(), p.getWholesalePrice(),
                includeCost ? p.getCostPrice() : null, p.getReorderPoint(), level == null ? null : level.physicalStock(),
                level == null ? null : level.reservedStock(), level == null ? null : level.availableStock(),
                p.isActive(), p.getVersion());
    }

    private ProductDetailResponse productDetail(final Product p, final boolean includeCost) {
        final Brand brand = p.getBrand();
        return new ProductDetailResponse(p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getDescription(),
                p.getCategory().getId(), p.getCategory().getName(), brand == null ? null : brand.getId(),
                brand == null ? null : brand.getName(), p.getUom().getId(), p.getUom().getCode(),
                p.getProductType().name(), p.getTaxCategory().name(), includeCost ? p.getCostPrice() : null,
                p.getSellingPrice(), p.getWholesalePrice(), p.getReorderPoint(), p.isActive(), p.getImagePath(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    private ServiceResponse serviceResponse(final ServiceDefinition s) {
        return new ServiceResponse(s.getId(), s.getServiceCode(), s.getName(), s.getDescription(), s.getCategory(),
                s.getBasePrice(), s.getEstimatedDurationMinutes(), s.isRequiresEstimate(), s.getWarrantyDays(),
                s.isActive(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
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

    private void required(final List<FieldError> errors, final String field, final String value) {
        if (value == null || value.isBlank()) errors.add(new FieldError(field, "REQUIRED", field + " is required."));
    }

    private void nonNegative(final List<FieldError> errors, final String field, final BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new FieldError(field, "NEGATIVE", field + " must be zero or greater."));
        }
    }

    private BigDecimal money(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal quantity(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private int integer(final Integer value) { return value == null ? 0 : value; }
    private String blankToNull(final String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean same(final BigDecimal left, final BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }
}

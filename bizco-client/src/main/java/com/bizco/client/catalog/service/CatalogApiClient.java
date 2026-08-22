package com.bizco.client.catalog.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
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
import com.bizco.common.dto.catalog.CatalogDtos.ProductSearchResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceSearchResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.UomResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CatalogApiClient extends ApiClient {

    public CatalogApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<List<CategoryResponse>> categories() {
        return get("/api/v1/product-categories", new TypeReference<>() {
        });
    }

    public CompletableFuture<CategoryResponse> createCategory(final CategoryCreateRequest request) {
        return post("/api/v1/product-categories", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<CategoryResponse> updateCategory(final Long id, final CategoryUpdateRequest request) {
        return put("/api/v1/product-categories/" + id, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<UomResponse>> uom() {
        return get("/api/v1/uom", new TypeReference<>() {
        });
    }

    public CompletableFuture<List<BrandResponse>> brands() {
        return get("/api/v1/brands", new TypeReference<>() {
        });
    }

    public CompletableFuture<BrandResponse> createBrand(final BrandCreateRequest request) {
        return post("/api/v1/brands", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<BrandResponse> updateBrand(final Long id, final BrandUpdateRequest request) {
        return put("/api/v1/brands/" + id, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<AttributeResponse>> attributes() {
        return get("/api/v1/attributes", new TypeReference<>() {
        });
    }

    public CompletableFuture<AttributeResponse> createAttribute(final AttributeCreateRequest request) {
        return post("/api/v1/attributes", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<AttributeValueResponse>> attributeValues(final Long attributeId) {
        return get("/api/v1/attributes/" + attributeId + "/values", new TypeReference<>() {
        });
    }

    public CompletableFuture<AttributeValueResponse> addAttributeValue(final Long attributeId,
                                                                        final AttributeValueCreateRequest request) {
        return post("/api/v1/attributes/" + attributeId + "/values", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<CategoryAttributeResponse>> categoryAttributes(final Long categoryId) {
        return get("/api/v1/product-categories/" + categoryId + "/attributes", new TypeReference<>() {
        });
    }

    public CompletableFuture<CategoryAttributeResponse> assignCategoryAttribute(final Long categoryId,
                                                                                 final CategoryAttributeAssignRequest request) {
        return post("/api/v1/product-categories/" + categoryId + "/attributes", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<Void> unassignCategoryAttribute(final Long categoryId, final Long attributeId) {
        return delete("/api/v1/product-categories/" + categoryId + "/attributes/" + attributeId);
    }

    public CompletableFuture<ProductSearchResponse> products(final String q, final Long categoryId, final String type,
                                                             final Boolean active, final int page, final int size) {
        return products(q, categoryId, type, active, null, null, page, size);
    }

    /** Phase 6 Week 19 (task 19.5): {@code brandId}/{@code attributeValueId} are additive filters,
     *  both null (the 6-arg overload above) keeps today's behavior for every other caller. */
    public CompletableFuture<ProductSearchResponse> products(final String q, final Long categoryId, final String type,
                                                             final Boolean active, final Long brandId,
                                                             final Long attributeValueId, final int page,
                                                             final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/products?page=").append(page).append("&size=").append(size);
        append(path, "q", q);
        if (categoryId != null) path.append("&categoryId=").append(categoryId);
        append(path, "type", type);
        if (active != null) path.append("&active=").append(active);
        if (brandId != null) path.append("&brandId=").append(brandId);
        if (attributeValueId != null) path.append("&attributeValueId=").append(attributeValueId);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    /** Phase 6 Week 19 (task 19.1): resolves a scanned barcode - variant-specific first, falling
     *  back to the product-level barcode - see {@code CatalogService.barcode}'s Javadoc. Returns a
     *  failed future (404) when nothing matches, same as every other not-found lookup. */
    public CompletableFuture<ProductBarcodeResponse> barcode(final String code) {
        return get("/api/v1/products/barcode/" + URLEncoder.encode(code, StandardCharsets.UTF_8),
                new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductDetailResponse> getProduct(final UUID id) {
        return get("/api/v1/products/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductDetailResponse> createProduct(final ProductCreateRequest request) {
        return post("/api/v1/products", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductDetailResponse> updateProduct(final UUID id, final ProductUpdateRequest request) {
        return put("/api/v1/products/" + id, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductDetailResponse> activateProduct(final UUID id) {
        return post("/api/v1/products/" + id + "/activate", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductDetailResponse> deactivateProduct(final UUID id) {
        return post("/api/v1/products/" + id + "/deactivate", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<ServiceSearchResponse> services(final String q, final Boolean active, final int page,
                                                             final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/services?page=").append(page).append("&size=").append(size);
        append(path, "q", q);
        if (active != null) path.append("&active=").append(active);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<ServiceResponse> createService(final ServiceCreateRequest request) {
        return post("/api/v1/services", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<ServiceResponse> updateService(final UUID id, final ServiceUpdateRequest request) {
        return put("/api/v1/services/" + id, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<ServiceResponse> activateService(final UUID id) {
        return post("/api/v1/services/" + id + "/activate", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<ServiceResponse> deactivateService(final UUID id) {
        return post("/api/v1/services/" + id + "/deactivate", new Object(), new TypeReference<>() {
        });
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }
}

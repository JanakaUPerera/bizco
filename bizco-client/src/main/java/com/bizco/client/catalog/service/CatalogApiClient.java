package com.bizco.client.catalog.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryUpdateRequest;
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

    public CompletableFuture<ProductSearchResponse> products(final String q, final Long categoryId, final String type,
                                                             final Boolean active, final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/products?page=").append(page).append("&size=").append(size);
        append(path, "q", q);
        if (categoryId != null) path.append("&categoryId=").append(categoryId);
        append(path, "type", type);
        if (active != null) path.append("&active=").append(active);
        return get(path.toString(), new TypeReference<>() {
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

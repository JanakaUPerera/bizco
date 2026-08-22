package com.bizco.client.catalog.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantGenerateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Separate from {@link CatalogApiClient} — matches how {@code VariantController}/
 *  {@code VariantService} are their own service boundary on the server (Phase 6 Week 17). */
public class VariantApiClient extends ApiClient {

    public VariantApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<List<VariantResponse>> variants(final UUID productId) {
        return get("/api/v1/products/" + productId + "/variants", new TypeReference<>() {
        });
    }

    public CompletableFuture<VariantResponse> createVariant(final UUID productId, final VariantCreateRequest request) {
        return post("/api/v1/products/" + productId + "/variants", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<VariantResponse> updateVariant(final UUID productId, final UUID variantId,
                                                             final VariantUpdateRequest request) {
        return put("/api/v1/products/" + productId + "/variants/" + variantId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<VariantResponse>> generateVariants(final UUID productId,
                                                                      final VariantGenerateRequest request) {
        return post("/api/v1/products/" + productId + "/variants/generate", request, new TypeReference<>() {
        });
    }
}

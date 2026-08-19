package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductSearchResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Per-supplier product catalog (DevelopmentPlan.md Week 13). */
public class SupplierProductApiClient extends ApiClient {

    public SupplierProductApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<SupplierProductSearchResponse> search(final UUID supplierId, final UUID productId,
                                                                    final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/supplier-products?page=").append(page).append("&size=").append(size);
        if (supplierId != null) {
            path.append("&supplierId=").append(supplierId);
        }
        if (productId != null) {
            path.append("&productId=").append(productId);
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierProductResponse> create(final SupplierProductCreateRequest request) {
        return post("/api/v1/supplier-products", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierProductResponse> update(final UUID id, final SupplierProductUpdateRequest request) {
        return put("/api/v1/supplier-products/" + id, request, new TypeReference<>() {
        });
    }
}

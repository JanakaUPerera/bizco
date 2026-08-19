package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnSearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Supplier returns against a POSTED goods receipt (DevelopmentPlan.md Week 15). */
public class SupplierReturnApiClient extends ApiClient {

    public SupplierReturnApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<SupplierReturnSearchResponse> search(final UUID supplierId, final UUID goodsReceiptId,
                                                                   final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/supplier-returns?page=").append(page)
                .append("&size=").append(size);
        if (supplierId != null) {
            path.append("&supplierId=").append(supplierId);
        }
        if (goodsReceiptId != null) {
            path.append("&goodsReceiptId=").append(goodsReceiptId);
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierReturnResponse> get(final UUID id) {
        return get("/api/v1/supplier-returns/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierReturnResponse> create(final UUID idempotencyKey,
                                                             final CreateSupplierReturnRequest request) {
        return postIdempotent("/api/v1/supplier-returns", request, idempotencyKey, new TypeReference<>() {
        });
    }
}

package com.bizco.client.manufacturing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ProductionOrderApiClient extends ApiClient {

    public ProductionOrderApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<ProductionOrderSearchResponse> search(final UUID bomId, final UUID finishedVariantId,
                                                                   final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/production-orders?page=" + page + "&size=" + size);
        if (bomId != null) {
            path.append("&bomId=").append(bomId);
        }
        if (finishedVariantId != null) {
            path.append("&finishedVariantId=").append(finishedVariantId);
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductionOrderResponse> get(final UUID productionOrderId) {
        return get("/api/v1/production-orders/" + productionOrderId, new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductionOrderResponse> produce(final UUID idempotencyKey, final ProduceRequest request) {
        return postIdempotent("/api/v1/production-orders", request, idempotencyKey, new TypeReference<>() {
        });
    }
}

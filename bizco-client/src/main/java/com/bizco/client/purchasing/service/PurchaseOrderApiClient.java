package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CancelPurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderSearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Purchase Order DRAFT/APPROVED/SENT workflow (DevelopmentPlan.md Week 13). */
public class PurchaseOrderApiClient extends ApiClient {

    public PurchaseOrderApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<PurchaseOrderSearchResponse> search(final UUID supplierId, final String status,
                                                                  final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/purchase-orders?page=").append(page).append("&size=").append(size);
        if (supplierId != null) {
            path.append("&supplierId=").append(supplierId);
        }
        if (status != null && !status.isBlank()) {
            path.append("&status=").append(status.trim());
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> get(final UUID id) {
        return get("/api/v1/purchase-orders/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> create(final CreatePurchaseOrderRequest request) {
        return post("/api/v1/purchase-orders", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> addItem(final UUID id, final AddPurchaseOrderItemRequest request) {
        return post("/api/v1/purchase-orders/" + id + "/items", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> removeItem(final UUID id, final UUID itemId) {
        return delete("/api/v1/purchase-orders/" + id + "/items/" + itemId, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> approve(final UUID id, final UUID idempotencyKey,
                                                                   final DecidePurchaseOrderRequest request) {
        return postIdempotent("/api/v1/purchase-orders/" + id + "/approve", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> send(final UUID id, final UUID idempotencyKey,
                                                                final DecidePurchaseOrderRequest request) {
        return postIdempotent("/api/v1/purchase-orders/" + id + "/send", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<PurchaseOrderDetailResponse> cancel(final UUID id, final CancelPurchaseOrderRequest request) {
        return post("/api/v1/purchase-orders/" + id + "/cancel", request, new TypeReference<>() {
        });
    }
}

package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSearchResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.ProductCostHistorySearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Goods Receipt DRAFT/POSTED workflow (DevelopmentPlan.md Week 14). */
public class GoodsReceiptApiClient extends ApiClient {

    public GoodsReceiptApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<GoodsReceiptSearchResponse> search(final UUID supplierId, final String status,
                                                                 final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/goods-receipts?page=").append(page).append("&size=").append(size);
        if (supplierId != null) {
            path.append("&supplierId=").append(supplierId);
        }
        if (status != null && !status.isBlank()) {
            path.append("&status=").append(status.trim());
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptDetailResponse> get(final UUID id) {
        return get("/api/v1/goods-receipts/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptDetailResponse> create(final CreateGoodsReceiptRequest request) {
        return post("/api/v1/goods-receipts", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptDetailResponse> addItem(final UUID id, final AddGoodsReceiptItemRequest request) {
        return post("/api/v1/goods-receipts/" + id + "/items", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptDetailResponse> removeItem(final UUID id, final UUID itemId) {
        return delete("/api/v1/goods-receipts/" + id + "/items/" + itemId, new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptDetailResponse> post(final UUID id, final UUID idempotencyKey,
                                                               final PostGoodsReceiptRequest request) {
        return postIdempotent("/api/v1/goods-receipts/" + id + "/post", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<ProductCostHistorySearchResponse> costHistory(final UUID productId, final int page,
                                                                            final int size) {
        return get("/api/v1/goods-receipts/cost-history?productId=" + productId + "&page=" + page + "&size=" + size,
                new TypeReference<>() {
                });
    }
}

package com.bizco.client.inventory.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.LowStockSummaryResponse;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentResponse;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentSearchResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelSearchResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementSearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Client for Inventory (DevelopmentPlan.md Week 12): stock levels/history and the stock
 *  adjustment PENDING -&gt; APPROVED/REJECTED workflow (StockController/StockAdjustmentController). */
public class InventoryApiClient extends ApiClient {

    public InventoryApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<StockLevelSearchResponse> levels(final String q, final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/stock/levels?page=").append(page).append("&size=").append(size);
        append(path, "q", q);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<StockLevelResponse> level(final UUID productId) {
        return get("/api/v1/stock/levels/" + productId, new TypeReference<>() {
        });
    }

    public CompletableFuture<StockLevelSearchResponse> lowStock(final int page, final int size) {
        return get("/api/v1/stock/low-stock?page=" + page + "&size=" + size, new TypeReference<>() {
        });
    }

    public CompletableFuture<LowStockSummaryResponse> lowStockSummary() {
        return get("/api/v1/stock/low-stock/summary", new TypeReference<>() {
        });
    }

    public CompletableFuture<StockMovementSearchResponse> movements(final UUID productId, final int page, final int size) {
        return get("/api/v1/stock/movements?productId=" + productId + "&page=" + page + "&size=" + size,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<StockAdjustmentSearchResponse> adjustments(final UUID productId, final String status,
                                                                        final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/stock-adjustments?page=").append(page).append("&size=").append(size);
        if (productId != null) {
            path.append("&productId=").append(productId);
        }
        append(path, "status", status);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<StockAdjustmentResponse> createAdjustment(final CreateStockAdjustmentRequest request) {
        return post("/api/v1/stock-adjustments", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<StockAdjustmentResponse> approveAdjustment(final UUID adjustmentId, final UUID idempotencyKey,
                                                                        final DecideStockAdjustmentRequest request) {
        return postIdempotent("/api/v1/stock-adjustments/" + adjustmentId + "/approve", request, idempotencyKey,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<StockAdjustmentResponse> rejectAdjustment(final UUID adjustmentId, final UUID idempotencyKey,
                                                                       final DecideStockAdjustmentRequest request) {
        return postIdempotent("/api/v1/stock-adjustments/" + adjustmentId + "/reject", request, idempotencyKey,
                new TypeReference<>() {
                });
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }
}

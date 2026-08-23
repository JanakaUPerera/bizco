package com.bizco.client.manufacturing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSearchResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.UpdateBomRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BillOfMaterialsApiClient extends ApiClient {

    public BillOfMaterialsApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<BomSearchResponse> search(final boolean activeOnly, final int page, final int size) {
        return get("/api/v1/boms?activeOnly=" + activeOnly + "&page=" + page + "&size=" + size, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> get(final UUID bomId) {
        return get("/api/v1/boms/" + bomId, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> create(final CreateBomRequest request) {
        return post("/api/v1/boms", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> update(final UUID bomId, final UpdateBomRequest request) {
        return put("/api/v1/boms/" + bomId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> addItem(final UUID bomId, final BomItemRequest request) {
        return post("/api/v1/boms/" + bomId + "/items", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> updateItem(final UUID bomId, final UUID bomItemId,
                                                           final BomItemRequest request) {
        return put("/api/v1/boms/" + bomId + "/items/" + bomItemId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<BomDetailResponse> removeItem(final UUID bomId, final UUID bomItemId) {
        return delete("/api/v1/boms/" + bomId + "/items/" + bomItemId, new TypeReference<>() {
        });
    }
}

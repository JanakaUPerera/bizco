package com.bizco.client.sales.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSearchResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.UpdateHeldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HeldSaleApiClient extends ApiClient {

    public HeldSaleApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<HeldSaleDetailResponse> hold(final HoldSaleRequest request) {
        return post("/api/v1/held-sales", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<HeldSaleSearchResponse> search(final String status) {
        final String path = status == null || status.isBlank() ? "/api/v1/held-sales"
                : "/api/v1/held-sales?status=" + status;
        return get(path, new TypeReference<>() {
        });
    }

    public CompletableFuture<HeldSaleDetailResponse> get(final UUID heldSaleId) {
        return get("/api/v1/held-sales/" + heldSaleId, new TypeReference<>() {
        });
    }

    public CompletableFuture<HeldSaleDetailResponse> resume(final UUID heldSaleId) {
        return post("/api/v1/held-sales/" + heldSaleId + "/resume", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<HeldSaleDetailResponse> update(final UUID heldSaleId, final UpdateHeldSaleRequest request) {
        return put("/api/v1/held-sales/" + heldSaleId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<HeldSaleDetailResponse> cancel(final UUID heldSaleId) {
        return post("/api/v1/held-sales/" + heldSaleId + "/cancel", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> convert(final UUID heldSaleId) {
        return post("/api/v1/held-sales/" + heldSaleId + "/convert", new Object(), new TypeReference<>() {
        });
    }
}

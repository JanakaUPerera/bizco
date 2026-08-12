package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSearchResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SupplierApiClient extends ApiClient {

    public SupplierApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<SupplierSearchResponse> search(final String q, final String status,
                                                            final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/suppliers?page=").append(page).append("&size=").append(size);
        append(path, "q", q);
        append(path, "status", status);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierDetailResponse> create(final SupplierCreateRequest request) {
        return post("/api/v1/suppliers", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierDetailResponse> get(final UUID id) {
        return get("/api/v1/suppliers/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierDetailResponse> update(final UUID id, final SupplierUpdateRequest request) {
        return put("/api/v1/suppliers/" + id, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierDetailResponse> activate(final UUID id) {
        return post("/api/v1/suppliers/" + id + "/activate", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierDetailResponse> deactivate(final UUID id) {
        return post("/api/v1/suppliers/" + id + "/deactivate", new Object(), new TypeReference<>() {
        });
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }
}

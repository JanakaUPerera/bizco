package com.bizco.client.customer.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreditSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSearchResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CustomerApiClient extends ApiClient {

    public CustomerApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<CustomerSearchResponse> search(final String query, final String category,
                                                            final String status, final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/customers?page=")
                .append(page).append("&size=").append(size);
        append(path, "q", query);
        append(path, "category", category);
        append(path, "status", status);
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> create(final CustomerCreateRequest request) {
        return post("/api/v1/customers", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> get(final UUID customerId) {
        return get("/api/v1/customers/" + customerId, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> update(final UUID customerId, final CustomerUpdateRequest request) {
        return put("/api/v1/customers/" + customerId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> block(final UUID customerId) {
        return post("/api/v1/customers/" + customerId + "/block", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> activate(final UUID customerId) {
        return post("/api/v1/customers/" + customerId + "/activate", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerDetailResponse> anonymize(final UUID customerId) {
        return post("/api/v1/customers/" + customerId + "/anonymize", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerCreditSummaryResponse> creditSummary(final UUID customerId) {
        return get("/api/v1/customers/" + customerId + "/credit-summary", new TypeReference<>() {
        });
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }
}


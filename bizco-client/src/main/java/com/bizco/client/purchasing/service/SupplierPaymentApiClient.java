package com.bizco.client.purchasing.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptOutstandingSearchResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentSearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Supplier payments/allocations and the outstanding-balance statement (DevelopmentPlan.md Week 15). */
public class SupplierPaymentApiClient extends ApiClient {

    public SupplierPaymentApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<SupplierPaymentSearchResponse> search(final UUID supplierId, final int page,
                                                                    final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/supplier-payments?page=").append(page)
                .append("&size=").append(size);
        if (supplierId != null) {
            path.append("&supplierId=").append(supplierId);
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierPaymentResponse> get(final UUID id) {
        return get("/api/v1/supplier-payments/" + id, new TypeReference<>() {
        });
    }

    public CompletableFuture<SupplierPaymentResponse> record(final UUID idempotencyKey,
                                                              final RecordSupplierPaymentRequest request) {
        return postIdempotent("/api/v1/supplier-payments", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<GoodsReceiptOutstandingSearchResponse> outstanding(final UUID supplierId,
                                                                                 final boolean outstandingOnly,
                                                                                 final int page, final int size) {
        return get("/api/v1/goods-receipts/outstanding?outstandingOnly=" + outstandingOnly
                + (supplierId == null ? "" : "&supplierId=" + supplierId) + "&page=" + page + "&size=" + size,
                new TypeReference<>() {
                });
    }
}

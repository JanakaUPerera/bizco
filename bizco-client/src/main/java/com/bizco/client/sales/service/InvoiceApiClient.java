package com.bizco.client.sales.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class InvoiceApiClient extends ApiClient {

    public InvoiceApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<InvoiceSummaryResponse> createDraft(final CreateDraftInvoiceRequest request) {
        return post("/api/v1/invoices", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> get(final UUID invoiceId) {
        return get("/api/v1/invoices/" + invoiceId, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> updateHeader(final UUID invoiceId, final UpdateInvoiceHeaderRequest request) {
        return put("/api/v1/invoices/" + invoiceId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> addLine(final UUID invoiceId, final AddInvoiceLineRequest request) {
        return post("/api/v1/invoices/" + invoiceId + "/lines", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> updateLine(final UUID invoiceId, final UUID lineId,
                                                                final UpdateInvoiceLineRequest request) {
        return put("/api/v1/invoices/" + invoiceId + "/lines/" + lineId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> deleteLine(final UUID invoiceId, final UUID lineId) {
        return delete("/api/v1/invoices/" + invoiceId + "/lines/" + lineId, new TypeReference<>() {
        });
    }

    public CompletableFuture<InvoiceDetailResponse> preview(final UUID invoiceId) {
        return post("/api/v1/invoices/" + invoiceId + "/preview", new Object(), new TypeReference<>() {
        });
    }

    public CompletableFuture<SalesApprovalResponse> requestApproval(final UUID invoiceId, final SalesApprovalRequest request) {
        return post("/api/v1/invoices/" + invoiceId + "/approvals", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<PostInvoiceResponse> postInvoice(final UUID invoiceId, final UUID idempotencyKey,
                                                               final PostInvoiceRequest request) {
        return postIdempotent("/api/v1/invoices/" + invoiceId + "/post", request, idempotencyKey, new TypeReference<>() {
        });
    }
}

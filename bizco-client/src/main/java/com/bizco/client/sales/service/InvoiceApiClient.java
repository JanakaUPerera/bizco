package com.bizco.client.sales.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentSearchResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordInvoicePaymentRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSearchResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    public CompletableFuture<InvoiceSearchResponse> search(final String number, final UUID customerId, final String status,
                                                            final String type, final LocalDate fromDate,
                                                            final LocalDate toDate, final int page, final int size) {
        final StringBuilder path = new StringBuilder("/api/v1/invoices?page=").append(page).append("&size=").append(size);
        append(path, "number", number);
        if (customerId != null) path.append("&customerId=").append(customerId);
        append(path, "status", status);
        append(path, "type", type);
        if (fromDate != null) path.append("&fromDate=").append(fromDate);
        if (toDate != null) path.append("&toDate=").append(toDate);
        return get(path.toString(), new TypeReference<>() {
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

    public CompletableFuture<InvoiceDetailResponse> voidInvoice(final UUID invoiceId, final VoidInvoiceRequest request) {
        return post("/api/v1/invoices/" + invoiceId + "/void", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerPaymentResponse> recordPayment(final UUID invoiceId, final UUID idempotencyKey,
                                                                     final RecordInvoicePaymentRequest request) {
        return postIdempotent("/api/v1/invoices/" + invoiceId + "/payments", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerPaymentSearchResponse> payments(final UUID invoiceId) {
        return get("/api/v1/invoices/" + invoiceId + "/payments", new TypeReference<>() {
        });
    }

    public CompletableFuture<byte[]> receipt(final UUID invoiceId) {
        return getBytes("/api/v1/invoices/" + invoiceId + "/receipt");
    }

    public CompletableFuture<byte[]> reprintReceipt(final UUID invoiceId) {
        return postBytes("/api/v1/invoices/" + invoiceId + "/receipt/reprint");
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }
}

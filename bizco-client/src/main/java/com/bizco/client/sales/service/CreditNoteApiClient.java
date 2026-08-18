package com.bizco.client.sales.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteSearchResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CreditNoteApiClient extends ApiClient {

    public CreditNoteApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<ReturnEligibilityResponse> returnEligibility(final UUID invoiceId) {
        return get("/api/v1/invoices/" + invoiceId + "/return-eligibility", new TypeReference<>() {
        });
    }

    public CompletableFuture<CreditNoteResponse> issue(final UUID idempotencyKey, final CreateCreditNoteRequest request) {
        return postIdempotent("/api/v1/credit-notes", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<CreditNoteResponse> get(final UUID creditNoteId) {
        return get("/api/v1/credit-notes/" + creditNoteId, new TypeReference<>() {
        });
    }

    public CompletableFuture<CreditNoteSearchResponse> search(final UUID customerId, final UUID invoiceId,
                                                               final String number, final LocalDate fromDate,
                                                               final LocalDate toDate) {
        final StringBuilder path = new StringBuilder("/api/v1/credit-notes?");
        if (customerId != null) path.append("customerId=").append(customerId).append('&');
        if (invoiceId != null) path.append("invoiceId=").append(invoiceId).append('&');
        append(path, "number", number);
        if (fromDate != null) path.append("fromDate=").append(fromDate).append('&');
        if (toDate != null) path.append("toDate=").append(toDate).append('&');
        return get(path.toString(), new TypeReference<>() {
        });
    }

    private void append(final StringBuilder path, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            path.append(name).append('=').append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8)).append('&');
        }
    }
}

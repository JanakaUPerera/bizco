package com.bizco.client.sales.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentSearchResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordCustomerPaymentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PaymentApiClient extends ApiClient {

    public PaymentApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<CustomerPaymentResponse> recordForCustomer(final UUID idempotencyKey,
                                                                         final RecordCustomerPaymentRequest request) {
        return postIdempotent("/api/v1/customer-payments", request, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<CustomerPaymentSearchResponse> customerPayments(final UUID customerId) {
        return get("/api/v1/customers/" + customerId + "/payments", new TypeReference<>() {
        });
    }
}

package com.bizco.client.system.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.system.SystemRequests.TaxConfigurationRequest;
import com.bizco.common.dto.system.SystemResponses.TaxConfigurationResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;

public class TaxConfigurationApiClient extends ApiClient {

    public TaxConfigurationApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<TaxConfigurationResponse> getConfiguration() {
        return get("/api/v1/system/tax", new TypeReference<>() {
        });
    }

    public CompletableFuture<TaxConfigurationResponse> saveConfiguration(final TaxConfigurationRequest request) {
        return put("/api/v1/system/tax", request, new TypeReference<>() {
        });
    }
}

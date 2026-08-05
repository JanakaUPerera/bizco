package com.bizco.client.system.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.common.dto.system.SystemResponses.BusinessProfileResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class BusinessProfileApiClient extends ApiClient {

    public BusinessProfileApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<Optional<BusinessProfileResponse>> getProfile() {
        return getOptional("/api/settings/business", new TypeReference<>() {
        });
    }

    public CompletableFuture<BusinessProfileResponse> saveProfile(final BusinessProfileRequest request) {
        return put("/api/settings/business", request, new TypeReference<>() {
        });
    }
}

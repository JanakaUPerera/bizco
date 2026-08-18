package com.bizco.client.scheduling.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.scheduling.AppointmentDtos.TechnicianListResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;

/** ApiContracts.md &sect;28.1. */
public class TechnicianApiClient extends ApiClient {

    public TechnicianApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<TechnicianListResponse> listActive() {
        return get("/api/v1/staff/technicians?active=true", new TypeReference<>() {
        });
    }
}

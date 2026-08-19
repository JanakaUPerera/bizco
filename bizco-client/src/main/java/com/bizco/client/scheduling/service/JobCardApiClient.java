package com.bizco.client.scheduling.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobServiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateEstimateRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.EstimateResponseRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.GenerateServiceInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardSearchResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobPartResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.UpdateJobCardRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** ApiContracts.md &sect;29-33. */
public class JobCardApiClient extends ApiClient {

    public JobCardApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<JobCardSearchResponse> search(final String q, final String status,
                                                           final UUID technicianId) {
        final List<String> params = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            params.add("q=" + q);
        }
        if (status != null && !status.isBlank()) {
            params.add("status=" + status);
        }
        if (technicianId != null) {
            params.add("technicianId=" + technicianId);
        }
        final String path = "/api/v1/job-cards" + (params.isEmpty() ? "" : "?" + String.join("&", params));
        return get(path, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> get(final UUID jobCardId) {
        return get("/api/v1/job-cards/" + jobCardId, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> createWalkIn(final CreateJobCardRequest request) {
        return post("/api/v1/job-cards", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> update(final UUID jobCardId, final UpdateJobCardRequest request) {
        return put("/api/v1/job-cards/" + jobCardId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> changeStatus(final UUID jobCardId, final JobCardStatusRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/status", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> addService(final UUID jobCardId, final AddJobServiceRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/services", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> changeServiceStatus(final UUID jobCardId, final UUID jobServiceId,
                                                                   final JobServiceStatusRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/services/" + jobServiceId + "/status", request,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<JobCardResponse> createEstimate(final UUID jobCardId, final CreateEstimateRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/estimates", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<JobCardResponse> acceptEstimate(final UUID jobCardId, final UUID estimateId,
                                                              final EstimateResponseRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/estimates/" + estimateId + "/accept", request,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<JobCardResponse> declineEstimate(final UUID jobCardId, final UUID estimateId,
                                                               final EstimateResponseRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/estimates/" + estimateId + "/decline", request,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<JobPartResponse> addPart(final UUID jobCardId, final AddJobPartRequest request) {
        return postIdempotent("/api/v1/job-cards/" + jobCardId + "/parts", request, UUID.randomUUID(),
                new TypeReference<>() {
                });
    }

    public CompletableFuture<InvoiceDetailResponse> generateInvoice(final UUID jobCardId,
                                                                     final GenerateServiceInvoiceRequest request) {
        return post("/api/v1/job-cards/" + jobCardId + "/invoice", request, new TypeReference<>() {
        });
    }
}

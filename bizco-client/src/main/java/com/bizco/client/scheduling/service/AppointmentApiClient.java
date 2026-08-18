package com.bizco.client.scheduling.service;

import com.bizco.client.api.ApiClient;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentSearchResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.AvailabilityResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** ApiContracts.md &sect;27 (excludes &sect;27.7 convert-to-job - Week 11 scope). */
public class AppointmentApiClient extends ApiClient {

    public AppointmentApiClient(final ClientSession session) {
        super(session);
    }

    public CompletableFuture<AppointmentSearchResponse> search(final String status) {
        final String path = status == null || status.isBlank() ? "/api/v1/appointments"
                : "/api/v1/appointments?status=" + status;
        return get(path, new TypeReference<>() {
        });
    }

    public CompletableFuture<AppointmentResponse> get(final UUID appointmentId) {
        return get("/api/v1/appointments/" + appointmentId, new TypeReference<>() {
        });
    }

    public CompletableFuture<AvailabilityResponse> availability(final UUID serviceId, final UUID technicianId,
                                                                 final LocalDate date) {
        final StringBuilder path = new StringBuilder("/api/v1/appointments/availability?serviceId=" + serviceId
                + "&date=" + date);
        if (technicianId != null) {
            path.append("&technicianId=").append(technicianId);
        }
        return get(path.toString(), new TypeReference<>() {
        });
    }

    public CompletableFuture<AppointmentResponse> create(final CreateAppointmentRequest request) {
        return post("/api/v1/appointments", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<AppointmentResponse> reschedule(final UUID appointmentId,
                                                              final RescheduleAppointmentRequest request) {
        return put("/api/v1/appointments/" + appointmentId, request, new TypeReference<>() {
        });
    }

    public CompletableFuture<AppointmentResponse> changeStatus(final UUID appointmentId,
                                                                final AppointmentStatusRequest request) {
        return post("/api/v1/appointments/" + appointmentId + "/status", request, new TypeReference<>() {
        });
    }

    public CompletableFuture<AppointmentResponse> cancel(final UUID appointmentId, final String reason) {
        final String path = "/api/v1/appointments/" + appointmentId + "/cancel"
                + (reason == null || reason.isBlank() ? ""
                        : "?reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8));
        return post(path, new Object(), new TypeReference<>() {
        });
    }
}

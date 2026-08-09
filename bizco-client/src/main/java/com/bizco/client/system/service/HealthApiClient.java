package com.bizco.client.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HealthApiClient {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI healthUri;

    public HealthApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper(), serverUrl());
    }

    HealthApiClient(final HttpClient httpClient, final ObjectMapper objectMapper, final URI serverUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.healthUri = serverUrl.resolve("/api/v1/health");
    }

    public CompletableFuture<String> checkHealth() {
        final HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toReadyMessage);
    }

    private String toReadyMessage(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HealthApiException("Server health check failed with status " + response.statusCode() + ".");
        }
        try {
            final JsonNode root = objectMapper.readTree(response.body());
            final String status = root.path("status").asText();
            if (!"UP".equals(status)) {
                throw new HealthApiException("Server health check returned " + status + ".");
            }
            return "Server and database are ready.";
        } catch (final IOException exception) {
            throw new HealthApiException("Server health response could not be read.", exception);
        }
    }

    private static URI serverUrl() {
        return URI.create(System.getProperty("bizco.server.url", DEFAULT_SERVER_URL));
    }

    public static class HealthApiException extends RuntimeException {
        public HealthApiException(final String message) {
            super(message);
        }

        public HealthApiException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}

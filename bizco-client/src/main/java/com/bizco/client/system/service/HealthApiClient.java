package com.bizco.client.system.service;

import com.bizco.client.api.ApiClientException;
import com.bizco.client.api.DatabaseUnavailableException;
import com.bizco.client.api.ServerConfig;
import com.bizco.client.api.ServerUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Checks {@code /api/v1/health}, distinguishing "the server itself could not be reached" from
 * "the server responded but its database is down" so callers (splash screen, connection status
 * indicator) can show the right guidance for each.
 */
public class HealthApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI healthUri;

    public HealthApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper(), ServerConfig.serverUrl());
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
                .thenApply(this::toReadyMessage)
                .exceptionally(this::serverUnavailable);
    }

    private String toReadyMessage(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ServerUnavailableException(
                    "Server responded with an unexpected status (" + response.statusCode() + ").", null);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (final IOException exception) {
            throw new ServerUnavailableException("Server health response could not be read.", exception);
        }
        final String status = root.path("status").asText();
        final String databaseStatus = root.path("databaseStatus").asText();
        final String message = root.path("message").asText();
        if ("UP".equals(status) && "UP".equals(databaseStatus)) {
            return message.isBlank() ? "Server and database are ready." : message;
        }
        if (!"UP".equals(databaseStatus)) {
            throw new DatabaseUnavailableException(
                    message.isBlank() ? "Database connection is unavailable." : message);
        }
        throw new ServerUnavailableException(
                message.isBlank() ? "Server reported an unexpected status." : message, null);
    }

    private String serverUnavailable(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException
                && completionException.getCause() instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        if (throwable instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        throw new ServerUnavailableException("Cannot reach the Bizco server. Check that it is running and reachable.", throwable);
    }
}

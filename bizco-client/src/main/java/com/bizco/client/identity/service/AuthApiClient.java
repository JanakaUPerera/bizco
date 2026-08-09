package com.bizco.client.identity.service;

import com.bizco.client.api.ApiClientException;
import com.bizco.client.api.ServerUnavailableException;
import com.bizco.common.api.ApiHeaders;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AuthApiClient {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI loginUri;

    public AuthApiClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper(), serverUrl());
    }

    AuthApiClient(final HttpClient httpClient, final ObjectMapper objectMapper, final URI serverUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.loginUri = serverUrl.resolve("/api/v1/auth/login");
    }

    public CompletableFuture<ClientSession> login(final String username, final String password) {
        final LoginRequest loginRequest = new LoginRequest(username, password, clientId());
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(loginUri)
                    .header("Content-Type", "application/json")
                    .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(loginRequest)))
                    .build();
        } catch (final IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toSession)
                .exceptionally(this::serverUnavailable);
    }

    private ClientSession toSession(final HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw com.bizco.client.api.ApiErrorParser.toException(objectMapper, response);
        }
        try {
            final JsonNode root = objectMapper.readTree(response.body());
            final JsonNode data = root.path("data");
            final JsonNode user = data.path("user");
            final Set<String> permissions = new LinkedHashSet<>();
            user.path("effectivePermissions").forEach(permission -> permissions.add(permission.asText()));
            return new ClientSession(
                    data.path("sessionToken").asText(),
                    Instant.parse(data.path("expiresAt").asText()),
                    UUID.fromString(user.path("userId").asText()),
                    user.path("username").asText(),
                    user.path("displayName").asText(),
                    permissions);
        } catch (final RuntimeException | IOException exception) {
            throw new AuthApiException("Login response could not be read.", exception);
        }
    }

    private static URI serverUrl() {
        return URI.create(System.getProperty("bizco.server.url", DEFAULT_SERVER_URL));
    }

    private static String clientId() {
        return System.getProperty("user.name", "bizco-client") + "@" + System.getProperty("os.name", "desktop");
    }

    public static class AuthApiException extends RuntimeException {
        public AuthApiException(final String message) {
            super(message);
        }

        public AuthApiException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private ClientSession serverUnavailable(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException
                && completionException.getCause() instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        if (throwable instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        throw new ServerUnavailableException("Server is unavailable.", throwable);
    }
}

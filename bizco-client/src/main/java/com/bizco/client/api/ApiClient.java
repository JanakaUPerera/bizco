package com.bizco.client.api;

import com.bizco.client.identity.dto.ClientSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ApiClient {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI serverUrl;
    private final ClientSession session;

    public ApiClient(final ClientSession session) {
        this(HttpClient.newHttpClient(), objectMapper(), serverUrl(), session);
    }

    ApiClient(final HttpClient httpClient, final ObjectMapper objectMapper,
              final URI serverUrl, final ClientSession session) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.serverUrl = serverUrl;
        this.session = session;
    }

    protected <T> CompletableFuture<T> get(final String path, final TypeReference<T> type) {
        return send(request(path).GET().build(), type);
    }

    protected <T> CompletableFuture<Optional<T>> getOptional(final String path, final TypeReference<T> type) {
        return httpClient.sendAsync(request(path).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) {
                        return Optional.empty();
                    }
                    ensureSuccess(response);
                    return Optional.of(read(response.body(), type));
                });
    }

    protected <T> CompletableFuture<T> post(final String path, final Object body, final TypeReference<T> type) {
        return send(request(path).POST(jsonBody(body)).build(), type);
    }

    protected <T> CompletableFuture<T> put(final String path, final Object body, final TypeReference<T> type) {
        return send(request(path).PUT(jsonBody(body)).build(), type);
    }

    protected CompletableFuture<Void> delete(final String path) {
        return httpClient.sendAsync(request(path).DELETE().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    ensureSuccess(response);
                    return null;
                });
    }

    private <T> CompletableFuture<T> send(final HttpRequest request, final TypeReference<T> type) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    ensureSuccess(response);
                    return read(response.body(), type);
                });
    }

    private HttpRequest.Builder request(final String path) {
        return HttpRequest.newBuilder(serverUrl.resolve(path))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + session.token());
    }

    private HttpRequest.BodyPublisher jsonBody(final Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        } catch (final JsonProcessingException exception) {
            throw new ApiClientException("Request could not be written.", exception);
        }
    }

    private <T> T read(final String body, final TypeReference<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (final IOException exception) {
            throw new ApiClientException("Response could not be read.", exception);
        }
    }

    private void ensureSuccess(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiClientException("API request failed with status " + response.statusCode());
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static URI serverUrl() {
        return URI.create(System.getProperty("bizco.server.url", DEFAULT_SERVER_URL));
    }

    public static class ApiClientException extends RuntimeException {
        public ApiClientException(final String message) {
            super(message);
        }

        public ApiClientException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}

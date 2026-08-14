package com.bizco.client.api;

import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.api.ApiHeaders;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI serverUrl;
    private final ClientSession session;

    public ApiClient(final ClientSession session) {
        this(HttpClient.newHttpClient(), objectMapper(), ServerConfig.serverUrl(), session);
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
                        return Optional.<T>empty();
                    }
                    ensureSuccess(response);
                    return Optional.of(read(response.body(), type));
                })
                .exceptionally(throwable -> this.<Optional<T>>serverUnavailable(throwable));
    }

    protected <T> CompletableFuture<T> post(final String path, final Object body, final TypeReference<T> type) {
        return send(request(path).POST(jsonBody(body)).build(), type);
    }

    protected <T> CompletableFuture<T> postIdempotent(final String path, final Object body,
                                                      final UUID idempotencyKey, final TypeReference<T> type) {
        return send(request(path)
                .header(ApiHeaders.IDEMPOTENCY_KEY, idempotencyKey.toString())
                .POST(jsonBody(body))
                .build(), type);
    }

    protected <T> CompletableFuture<T> put(final String path, final Object body, final TypeReference<T> type) {
        return send(request(path).PUT(jsonBody(body)).build(), type);
    }

    protected CompletableFuture<Void> delete(final String path) {
        return httpClient.sendAsync(request(path).DELETE().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    ensureSuccess(response);
                    return (Void) null;
                })
                .exceptionally(throwable -> this.<Void>serverUnavailable(throwable));
    }

    private <T> CompletableFuture<T> send(final HttpRequest request, final TypeReference<T> type) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    ensureSuccess(response);
                    return read(response.body(), type);
                })
                .exceptionally(throwable -> this.<T>serverUnavailable(throwable));
    }

    private HttpRequest.Builder request(final String path) {
        return HttpRequest.newBuilder(serverUrl.resolve(path))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + session.token())
                .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString());
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
            throw ApiErrorParser.toException(objectMapper, response);
        }
    }

    private <T> T serverUnavailable(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException
                && completionException.getCause() instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        if (throwable instanceof ApiClientException apiClientException) {
            throw apiClientException;
        }
        throw new ServerUnavailableException("Server is unavailable.", throwable);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}

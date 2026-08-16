package com.bizco.client.identity.service;

import com.bizco.client.api.ApiClientException;
import com.bizco.client.api.ServerConfig;
import com.bizco.client.api.ServerUnavailableException;
import com.bizco.common.api.ApiHeaders;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
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

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI loginUri;
    private final URI meUri;
    private final URI changePasswordUri;

    public AuthApiClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper(), ServerConfig.serverUrl());
    }

    AuthApiClient(final HttpClient httpClient, final ObjectMapper objectMapper, final URI serverUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.loginUri = serverUrl.resolve("/api/v1/auth/login");
        this.meUri = serverUrl.resolve("/api/v1/auth/me");
        this.changePasswordUri = serverUrl.resolve("/api/v1/auth/change-password");
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

    /**
     * Re-fetches effective permissions and session expiry for an already-authenticated session,
     * keeping the same opaque token. Used to pick up secondary-role grants/revokes/expiry, and to
     * detect a session that the server has since expired or revoked, without requiring re-login.
     */
    public CompletableFuture<ClientSession> me(final ClientSession session) {
        final HttpRequest request = HttpRequest.newBuilder(meUri)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + session.token())
                .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> toRefreshedSession(session, response))
                .exceptionally(this::serverUnavailable);
    }

    /**
     * Changes the current session's user's password, e.g. to satisfy a server-enforced
     * {@code mustChangePassword} after a temporary/reset password. On success, revokes the
     * server-side session (matching {@code AuthService.changePassword}), so callers must send the
     * user back to the login screen afterward rather than continuing to use this token.
     */
    public CompletableFuture<Void> changePassword(final ClientSession session, final String currentPassword,
                                                   final String newPassword) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(changePasswordUri)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + session.token())
                    .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, newPassword))))
                    .build();
        } catch (final IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 204 && response.statusCode() != 200) {
                        throw com.bizco.client.api.ApiErrorParser.toException(objectMapper, response);
                    }
                    return (Void) null;
                })
                .exceptionally(this::serverUnavailable);
    }

    private ClientSession toRefreshedSession(final ClientSession current, final HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw com.bizco.client.api.ApiErrorParser.toException(objectMapper, response);
        }
        try {
            final JsonNode root = objectMapper.readTree(response.body());
            final Set<String> permissions = new LinkedHashSet<>();
            root.path("effectivePermissions").forEach(permission -> permissions.add(permission.asText()));
            return new ClientSession(
                    current.token(),
                    Instant.parse(root.path("expiresAt").asText()),
                    current.userId(),
                    root.path("username").asText(),
                    root.path("displayName").asText(),
                    permissions,
                    root.path("mustChangePassword").asBoolean(false));
        } catch (final RuntimeException | IOException exception) {
            throw new AuthApiException("Session refresh response could not be read.", exception);
        }
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
                    permissions,
                    user.path("mustChangePassword").asBoolean(false));
        } catch (final RuntimeException | IOException exception) {
            throw new AuthApiException("Login response could not be read.", exception);
        }
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
}

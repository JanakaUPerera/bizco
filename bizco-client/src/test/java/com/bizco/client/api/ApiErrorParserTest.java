package com.bizco.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bizco.common.api.ApiError;
import com.bizco.common.api.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ApiErrorParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void parsesSessionExpiredErrors() throws Exception {
        final ApiClientException exception = ApiErrorParser.toException(objectMapper,
                response(401, ApiError.of(ApiErrorCode.AUTH_SESSION_EXPIRED.code(), "Session expired",
                        "/api/v1/users", "correlation-1")));

        assertInstanceOf(SessionExpiredException.class, exception);
        assertEquals("correlation-1", exception.getCorrelationId());
    }

    @Test
    void parsesPermissionDeniedErrors() throws Exception {
        final ApiClientException exception = ApiErrorParser.toException(objectMapper,
                response(403, ApiError.of(ApiErrorCode.AUTH_PERMISSION_DENIED.code(), "Permission denied",
                        "/api/v1/users", "correlation-2")));

        assertInstanceOf(PermissionDeniedException.class, exception);
        assertEquals("Permission denied", exception.getMessage());
    }

    private HttpResponse<String> response(final int status, final ApiError error) throws Exception {
        final String body = objectMapper.writeValueAsString(error);
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(URI.create("http://localhost")).build();
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(java.util.Map.of(), (left, right) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost");
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }
        };
    }
}

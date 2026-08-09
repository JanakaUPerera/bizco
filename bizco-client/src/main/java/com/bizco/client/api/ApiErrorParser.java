package com.bizco.client.api;

import com.bizco.common.api.ApiError;
import com.bizco.common.api.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;

public final class ApiErrorParser {

    private ApiErrorParser() {
    }

    public static ApiClientException toException(final ObjectMapper objectMapper, final HttpResponse<String> response) {
        final ApiError apiError = parse(objectMapper, response.body());
        final String message = apiError == null || apiError.message() == null || apiError.message().isBlank()
                ? "API request failed with status " + response.statusCode() + "."
                : apiError.message();
        final String code = apiError == null ? null : apiError.code();
        if (response.statusCode() == 401 || ApiErrorCode.AUTH_SESSION_EXPIRED.code().equals(code)
                || ApiErrorCode.AUTH_SESSION_INVALID.code().equals(code)) {
            return new SessionExpiredException(message, response.statusCode(), apiError);
        }
        if (response.statusCode() == 403 || ApiErrorCode.AUTH_PERMISSION_DENIED.code().equals(code)) {
            return new PermissionDeniedException(message, response.statusCode(), apiError);
        }
        if (response.statusCode() == 503) {
            return new ServerUnavailableException(message, null);
        }
        return new ApiClientException(message, response.statusCode(), apiError);
    }

    private static ApiError parse(final ObjectMapper objectMapper, final String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, ApiError.class);
        } catch (final IOException exception) {
            return null;
        }
    }
}

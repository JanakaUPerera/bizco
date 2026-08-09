package com.bizco.client.api;

import com.bizco.common.api.ApiError;

public class ApiClientException extends RuntimeException {

    private final int statusCode;
    private final ApiError apiError;

    public ApiClientException(final String message) {
        this(message, 0, null, null);
    }

    public ApiClientException(final String message, final Throwable cause) {
        this(message, 0, null, cause);
    }

    public ApiClientException(final String message, final int statusCode, final ApiError apiError) {
        this(message, statusCode, apiError, null);
    }

    protected ApiClientException(final String message, final int statusCode, final ApiError apiError,
                                 final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.apiError = apiError;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public ApiError getApiError() {
        return apiError;
    }

    public String getCorrelationId() {
        return apiError == null ? null : apiError.correlationId();
    }
}

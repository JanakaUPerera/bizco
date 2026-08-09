package com.bizco.client.api;

import com.bizco.common.api.ApiError;

public class PermissionDeniedException extends ApiClientException {

    public PermissionDeniedException(final String message, final int statusCode, final ApiError apiError) {
        super(message, statusCode, apiError, null);
    }
}

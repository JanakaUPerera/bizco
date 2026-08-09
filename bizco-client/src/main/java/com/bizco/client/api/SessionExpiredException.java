package com.bizco.client.api;

import com.bizco.common.api.ApiError;

public class SessionExpiredException extends ApiClientException {

    public SessionExpiredException(final String message, final int statusCode, final ApiError apiError) {
        super(message, statusCode, apiError, null);
    }
}

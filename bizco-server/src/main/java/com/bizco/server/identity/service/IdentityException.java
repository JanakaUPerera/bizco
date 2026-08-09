package com.bizco.server.identity.service;

import com.bizco.common.api.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class IdentityException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    public IdentityException(final String message) {
        this(ApiErrorCode.IDENTITY_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public IdentityException(final ApiErrorCode code, final HttpStatus status, final String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}


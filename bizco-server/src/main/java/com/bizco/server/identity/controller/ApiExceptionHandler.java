package com.bizco.server.identity.controller;

import com.bizco.common.api.ApiError;
import com.bizco.server.identity.service.IdentityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ApiError> handleIdentityException(final IdentityException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("IDENTITY_ERROR", ex.getMessage()));
    }
}


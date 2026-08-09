package com.bizco.server.identity.controller;

import com.bizco.common.api.ApiError;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.ApiHeaders;
import com.bizco.common.api.FieldError;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ApiError> handleIdentityException(final IdentityException ex, final HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode().code(), ex.getMessage(),
                        request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(ApiValidationException.class)
    ResponseEntity<ApiError> handleValidationException(final ApiValidationException ex,
                                                       final HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.validation(ex.getFieldErrors(), request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(final HttpMessageNotReadableException ex,
                                                  final HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.validation(List.of(new FieldError("body", "MALFORMED_JSON",
                        "Request body is malformed.")), request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(final NoResourceFoundException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ApiErrorCode.RESOURCE_NOT_FOUND.code(), "Resource was not found.",
                        request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(final AuthenticationException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ApiErrorCode.AUTH_SESSION_INVALID.code(), "Session is invalid",
                        request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(final AccessDeniedException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ApiErrorCode.AUTH_PERMISSION_DENIED.code(), "Permission denied",
                        request.getRequestURI(), correlationId(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(final Exception ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiErrorCode.INTERNAL_ERROR.code(), "An unexpected server error occurred.",
                        request.getRequestURI(), correlationId(request)));
    }

    private String correlationId(final HttpServletRequest request) {
        final Object attribute = request.getAttribute(ApiHeaders.CORRELATION_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }
}


package com.bizco.server.identity.service;

import com.bizco.common.api.FieldError;
import java.util.List;

public class ApiValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public ApiValidationException(final List<FieldError> fieldErrors) {
        super("One or more fields are invalid.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}

package com.bizco.client.api;

/** Signals that the Bizco server was reached but reported its database dependency as unavailable. */
public class DatabaseUnavailableException extends ApiClientException {

    public DatabaseUnavailableException(final String message) {
        super(message);
    }

    public DatabaseUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

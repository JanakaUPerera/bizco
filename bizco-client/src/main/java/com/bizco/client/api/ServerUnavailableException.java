package com.bizco.client.api;

public class ServerUnavailableException extends ApiClientException {

    public ServerUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

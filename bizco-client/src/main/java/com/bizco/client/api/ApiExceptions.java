package com.bizco.client.api;

import java.util.concurrent.CompletionException;

/** Unwraps the single {@link CompletionException} layer async client chains add, so callers can inspect the real cause. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}

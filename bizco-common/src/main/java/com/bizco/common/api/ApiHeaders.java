package com.bizco.common.api;

public final class ApiHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";

    private ApiHeaders() {
    }
}

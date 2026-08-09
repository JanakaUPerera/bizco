package com.bizco.common.api;

import java.time.Instant;
import java.util.List;

public record PageResponse<T>(
        List<T> data,
        Page page,
        Meta meta
) {
    public record Page(int number, int size, long totalElements, int totalPages) {
    }

    public record Meta(Instant timestamp, String correlationId) {
    }
}

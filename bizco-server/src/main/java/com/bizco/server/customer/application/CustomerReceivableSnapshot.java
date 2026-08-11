package com.bizco.server.customer.application;

import java.math.BigDecimal;

public record CustomerReceivableSnapshot(
        BigDecimal outstandingReceivable,
        int oldestOutstandingDays,
        BigDecimal days0To30,
        BigDecimal days31To60,
        BigDecimal days61To90,
        BigDecimal days91Plus
) {
    public static CustomerReceivableSnapshot zero() {
        return new CustomerReceivableSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}


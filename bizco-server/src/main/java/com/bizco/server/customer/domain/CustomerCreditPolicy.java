package com.bizco.server.customer.domain;

import java.math.BigDecimal;

public class CustomerCreditPolicy {

    public CreditEligibility evaluate(final Customer customer, final BigDecimal currentReceivable,
                                      final int oldestOutstandingDays, final BigDecimal requestedCreditAmount) {
        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            return CreditEligibility.BLOCK_ALL;
        }
        final BigDecimal requested = requestedCreditAmount == null ? BigDecimal.ZERO : requestedCreditAmount;
        final BigDecimal receivable = currentReceivable == null ? BigDecimal.ZERO : currentReceivable;
        if (receivable.add(requested).compareTo(customer.getCreditLimit()) > 0) {
            return CreditEligibility.LIMIT_EXCEEDED;
        }
        if (oldestOutstandingDays >= 91) {
            return CreditEligibility.BLOCK_ALL;
        }
        if (oldestOutstandingDays >= 61) {
            return CreditEligibility.CASH_ONLY;
        }
        if (oldestOutstandingDays >= 31) {
            return CreditEligibility.WARNING;
        }
        return CreditEligibility.NORMAL;
    }
}


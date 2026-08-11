package com.bizco.server.customer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CustomerCreditPolicyTest {

    private final CustomerCreditPolicy policy = new CustomerCreditPolicy();

    @Test
    void agingPolicyMapsToExpectedEligibility() {
        final Customer customer = customer(BigDecimal.valueOf(1000));

        assertEquals(CreditEligibility.NORMAL, policy.evaluate(customer, BigDecimal.ZERO, 30, BigDecimal.ZERO));
        assertEquals(CreditEligibility.WARNING, policy.evaluate(customer, BigDecimal.ZERO, 31, BigDecimal.ZERO));
        assertEquals(CreditEligibility.CASH_ONLY, policy.evaluate(customer, BigDecimal.ZERO, 61, BigDecimal.ZERO));
        assertEquals(CreditEligibility.BLOCK_ALL, policy.evaluate(customer, BigDecimal.ZERO, 91, BigDecimal.ZERO));
    }

    @Test
    void creditLimitExceededTakesPrecedenceOverAgingWarning() {
        final Customer customer = customer(BigDecimal.valueOf(100));

        assertEquals(CreditEligibility.LIMIT_EXCEEDED,
                policy.evaluate(customer, BigDecimal.valueOf(80), 31, BigDecimal.valueOf(30)));
    }

    private Customer customer(final BigDecimal creditLimit) {
        return new Customer("CUS-TEST", "Test Customer", "0771234567", null, null, null, null,
                null, null, CustomerCategory.RETAIL, creditLimit, false, false);
    }
}


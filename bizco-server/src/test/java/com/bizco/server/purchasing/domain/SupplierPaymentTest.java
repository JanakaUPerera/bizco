package com.bizco.server.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.server.finance.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PUR-PAY-001..002 (DatabaseDesign.md &sect;17.10/17.11, DevelopmentPlan.md Week 15). */
class SupplierPaymentTest {

    private final UUID supplierId = UUID.randomUUID();
    private final UUID paidBy = UUID.randomUUID();

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new SupplierPayment(UUID.randomUUID(), supplierId, Instant.now(), PaymentMethod.CASH,
                BigDecimal.ZERO, null, paidBy, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allocateToAddsAllocation() {
        final SupplierPayment payment = new SupplierPayment(UUID.randomUUID(), supplierId, Instant.now(),
                PaymentMethod.BANK_TRANSFER, new BigDecimal("500.00"), "TXN-1", paidBy, null);
        payment.allocateTo(UUID.randomUUID(), new BigDecimal("300.00"));
        payment.allocateTo(UUID.randomUUID(), new BigDecimal("150.00"));

        assertThat(payment.getAllocations()).hasSize(2);
    }

    @Test
    void allocationRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new SupplierPaymentAllocation(UUID.randomUUID(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankReferenceNumberIsNormalizedToNull() {
        final SupplierPayment payment = new SupplierPayment(UUID.randomUUID(), supplierId, Instant.now(),
                PaymentMethod.CASH, BigDecimal.TEN, "  ", paidBy, null);
        assertThat(payment.getReferenceNumber()).isNull();
    }
}

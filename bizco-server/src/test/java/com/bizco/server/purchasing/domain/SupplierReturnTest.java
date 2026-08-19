package com.bizco.server.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PUR-RET-001..002 (DatabaseDesign.md &sect;17.8/17.9, DevelopmentPlan.md Week 15). */
class SupplierReturnTest {

    private final UUID supplierId = UUID.randomUUID();
    private final UUID goodsReceiptId = UUID.randomUUID();
    private final UUID goodsReceiptItemId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> new SupplierReturn(UUID.randomUUID(), "SRT-20260101-0001", supplierId,
                goodsReceiptId, " ", createdBy)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addItemAccumulatesTotal() {
        final SupplierReturn supplierReturn = new SupplierReturn(UUID.randomUUID(), "SRT-20260101-0001", supplierId,
                goodsReceiptId, "Damaged in transit", createdBy);
        supplierReturn.addItem(new SupplierReturnItem(goodsReceiptItemId, productId, new BigDecimal("3.000"),
                new BigDecimal("10.00")));
        supplierReturn.addItem(new SupplierReturnItem(UUID.randomUUID(), productId, new BigDecimal("2.000"),
                new BigDecimal("10.00")));

        assertThat(supplierReturn.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(supplierReturn.getItems()).hasSize(2);
    }

    @Test
    void itemRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new SupplierReturnItem(goodsReceiptItemId, productId, BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemComputesLineTotal() {
        final SupplierReturnItem item = new SupplierReturnItem(goodsReceiptItemId, productId, new BigDecimal("4.000"),
                new BigDecimal("12.50"));
        assertThat(item.getLineTotal()).isEqualByComparingTo("50.00");
    }
}

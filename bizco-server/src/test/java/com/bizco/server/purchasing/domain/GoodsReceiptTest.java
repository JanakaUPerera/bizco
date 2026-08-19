package com.bizco.server.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PUR-GRN-001..005 (DatabaseDesign.md &sect;17.5/17.6, DevelopmentPlan.md Week 14). */
class GoodsReceiptTest {

    private final UUID supplierId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    void newReceiptStartsDraftWithNoNumber() {
        final GoodsReceipt gr = newDraft();
        assertThat(gr.getStatus()).isEqualTo(GoodsReceiptStatus.DRAFT);
        assertThat(gr.getReceiptNumber()).isNull();
    }

    @Test
    void itemRejectsDamagedPlusRejectedExceedingReceived() {
        assertThatThrownBy(() -> new GoodsReceiptItem(null, productId, new BigDecimal("10.000"),
                new BigDecimal("6.000"), new BigDecimal("5.000"), new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usableQuantityExcludesDamagedAndRejected() {
        final GoodsReceiptItem item = new GoodsReceiptItem(null, productId, new BigDecimal("10.000"),
                new BigDecimal("2.000"), new BigDecimal("1.000"), new BigDecimal("10.00"));
        assertThat(item.usableQuantity()).isEqualByComparingTo("7.000");
        assertThat(item.getTotalCost()).isEqualByComparingTo("100.00");
    }

    @Test
    void cannotPostWithNoItems() {
        final GoodsReceipt gr = newDraft();
        assertThatThrownBy(() -> gr.post(UUID.randomUUID(), "GRN-20260101-0001", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void postAllocatesNumberAndTransitionsStatus() {
        final GoodsReceipt gr = newDraftWithOneItem();
        gr.post(UUID.randomUUID(), "GRN-20260101-0001", Instant.now());

        assertThat(gr.getStatus()).isEqualTo(GoodsReceiptStatus.POSTED);
        assertThat(gr.getReceiptNumber()).isEqualTo("GRN-20260101-0001");
        assertThat(gr.getPostedAt()).isNotNull();
    }

    @Test
    void cannotModifyOrPostAgainAfterPosting() {
        final GoodsReceipt gr = newDraftWithOneItem();
        gr.post(UUID.randomUUID(), "GRN-20260101-0002", Instant.now());

        assertThatThrownBy(() -> gr.addItem(new GoodsReceiptItem(null, productId, BigDecimal.ONE, null, null, BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gr.post(UUID.randomUUID(), "GRN-20260101-0003", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private GoodsReceipt newDraft() {
        return new GoodsReceipt(null, supplierId, null, LocalDate.now(), "test receipt", createdBy);
    }

    private GoodsReceipt newDraftWithOneItem() {
        final GoodsReceipt gr = newDraft();
        gr.addItem(new GoodsReceiptItem(null, productId, new BigDecimal("5.000"), null, null, new BigDecimal("20.00")));
        gr.applyCalculatedTotal(new BigDecimal("100.00"));
        return gr;
    }
}

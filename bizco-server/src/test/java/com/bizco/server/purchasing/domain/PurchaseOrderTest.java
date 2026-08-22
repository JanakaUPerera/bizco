package com.bizco.server.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PUR-PO-001..006 (DatabaseDesign.md &sect;17.3, DevelopmentPlan.md Week 13). */
class PurchaseOrderTest {

    private final UUID supplierId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID productVariantId = UUID.randomUUID();

    @Test
    void newOrderStartsDraftWithNoNumber() {
        final PurchaseOrder po = newDraft();
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(po.getPoNumber()).isNull();
    }

    @Test
    void addItemAccumulatesLineTotals() {
        final PurchaseOrder po = newDraft();
        po.addItem(new PurchaseOrderItem(productId, productVariantId, new BigDecimal("2.000"), new BigDecimal("10.00")));
        po.addItem(new PurchaseOrderItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("3.000"), new BigDecimal("5.00")));

        assertThat(po.getItems()).hasSize(2);
        assertThat(po.getItems().get(0).getLineNumber()).isEqualTo(1);
        assertThat(po.getItems().get(1).getLineNumber()).isEqualTo(2);
    }

    @Test
    void cannotAddItemAfterLeavingDraft() {
        final PurchaseOrder po = newDraftWithOneItem();
        po.send(UUID.randomUUID(), "PO-20260101-0001", Instant.now());

        assertThatThrownBy(() -> po.addItem(new PurchaseOrderItem(productId, productVariantId, BigDecimal.ONE, BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotApproveOrSendWithNoItems() {
        final PurchaseOrder po = newDraft();
        assertThatThrownBy(() -> po.approve(UUID.randomUUID(), "PO-20260101-0001", createdBy, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> po.send(UUID.randomUUID(), "PO-20260101-0001", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveAllocatesNumberAndSetsApprover() {
        final PurchaseOrder po = newDraftWithOneItem();
        final UUID approver = UUID.randomUUID();

        po.approve(UUID.randomUUID(), "PO-20260101-0001", approver, Instant.now());

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
        assertThat(po.getPoNumber()).isEqualTo("PO-20260101-0001");
        assertThat(po.getApprovedBy()).isEqualTo(approver);
        assertThat(po.getApprovedAt()).isNotNull();
    }

    @Test
    void sendFromDraftAllocatesNumberDirectly() {
        final PurchaseOrder po = newDraftWithOneItem();
        po.send(UUID.randomUUID(), "PO-20260101-0002", Instant.now());

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SENT);
        assertThat(po.getPoNumber()).isEqualTo("PO-20260101-0002");
    }

    @Test
    void sendAfterApproveKeepsTheSameNumber() {
        final PurchaseOrder po = newDraftWithOneItem();
        po.approve(UUID.randomUUID(), "PO-20260101-0003", createdBy, Instant.now());

        po.send(UUID.randomUUID(), "PO-SHOULD-NOT-BE-USED", Instant.now());

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SENT);
        assertThat(po.getPoNumber()).isEqualTo("PO-20260101-0003");
    }

    @Test
    void cannotApproveTwice() {
        final PurchaseOrder po = newDraftWithOneItem();
        po.approve(UUID.randomUUID(), "PO-20260101-0004", createdBy, Instant.now());

        assertThatThrownBy(() -> po.approve(UUID.randomUUID(), "PO-20260101-0005", createdBy, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelRequiresAReason() {
        final PurchaseOrder po = newDraftWithOneItem();
        assertThatThrownBy(() -> po.cancel("", Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> po.cancel(null, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelFromDraftOrSentSucceeds() {
        final PurchaseOrder draft = newDraftWithOneItem();
        draft.cancel("no longer needed", Instant.now());
        assertThat(draft.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);

        final PurchaseOrder sent = newDraftWithOneItem();
        sent.send(UUID.randomUUID(), "PO-20260101-0006", Instant.now());
        sent.cancel("supplier could not fulfil", Instant.now());
        assertThat(sent.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void cannotCancelAlreadyCancelledOrClosedOrder() {
        final PurchaseOrder po = newDraftWithOneItem();
        po.cancel("first cancel", Instant.now());
        assertThatThrownBy(() -> po.cancel("second cancel", Instant.now())).isInstanceOf(IllegalStateException.class);
    }

    private PurchaseOrder newDraft() {
        return new PurchaseOrder(supplierId, LocalDate.now(), null, null, "test PO", createdBy);
    }

    private PurchaseOrder newDraftWithOneItem() {
        final PurchaseOrder po = newDraft();
        po.addItem(new PurchaseOrderItem(productId, productVariantId, new BigDecimal("5.000"), new BigDecimal("20.00")));
        po.applyCalculatedTotals(new BigDecimal("100.00"), new BigDecimal("100.00"));
        return po;
    }
}

package com.bizco.server.purchasing.domain;

/** DatabaseDesign.md &sect;17.3. PARTIALLY_RECEIVED/FULLY_RECEIVED are set by goods-receipt
 *  posting (Week 14), not by this week's PurchaseOrderService. */
public enum PurchaseOrderStatus {
    DRAFT,
    APPROVED,
    SENT,
    PARTIALLY_RECEIVED,
    FULLY_RECEIVED,
    CLOSED,
    CANCELLED
}

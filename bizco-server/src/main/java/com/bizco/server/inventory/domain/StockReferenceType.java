package com.bizco.server.inventory.domain;

/** The aggregate a {@link StockMovement#getReferenceId()} points at. V019 CHECK constraint. */
public enum StockReferenceType {
    INVOICE,
    GRN,
    CREDIT_NOTE,
    SUPPLIER_RETURN,
    JOB_CARD,
    STOCK_ADJUSTMENT
}

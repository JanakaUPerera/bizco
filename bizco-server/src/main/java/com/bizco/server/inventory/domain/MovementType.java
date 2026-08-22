package com.bizco.server.inventory.domain;

/** DatabaseDesign.md &sect;15.1 / V019 CHECK constraint. */
public enum MovementType {
    GRN,
    GRN_REVERSAL,
    SALE,
    SALE_VOID,
    CUSTOMER_RETURN,
    SUPPLIER_RETURN,
    JOB_PART,
    JOB_PART_REVERSAL,
    ADJUSTMENT,
    ADJUSTMENT_REVERSAL,
    PRODUCTION_IN,
    PRODUCTION_OUT
}

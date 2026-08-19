package com.bizco.server.inventory.domain;

/** DatabaseDesign.md &sect;16.1. DAMAGE is a distinct reason from NEGATIVE for reporting, but both
 *  reduce stock the same way in {@link StockAdjustment#signedQuantity()}. */
public enum AdjustmentType {
    POSITIVE,
    NEGATIVE,
    DAMAGE
}

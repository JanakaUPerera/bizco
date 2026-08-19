package com.bizco.server.catalog.application;

import java.math.BigDecimal;

/** A snapshot of one product's stock, as read from Inventory's {@code v_available_stock}
 *  (DatabaseDesign.md &sect;15.6) through {@link ProductStockQueryPort}. */
public record ProductStockLevel(BigDecimal physicalStock, BigDecimal reservedStock, BigDecimal availableStock) {

    public static ProductStockLevel zero() {
        return new ProductStockLevel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

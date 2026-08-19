package com.bizco.server.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the {@code v_available_stock} read model, joined with the product it describes. */
public record StockLevelRow(UUID productId, String sku, String name, BigDecimal reorderPoint,
                            BigDecimal physicalStock, BigDecimal reservedStock, BigDecimal availableStock) {

    public static StockLevelRow zero(final UUID productId, final String sku, final String name,
                                     final BigDecimal reorderPoint) {
        return new StockLevelRow(productId, sku, name, reorderPoint, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}

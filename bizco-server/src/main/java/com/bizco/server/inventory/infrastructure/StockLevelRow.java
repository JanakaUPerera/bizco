package com.bizco.server.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the {@code v_available_stock} read model (default-variant granularity, Phase 6
 *  Week 18), joined with the product it describes. */
public record StockLevelRow(UUID productId, UUID productVariantId, String sku, String name,
                            BigDecimal reorderPoint, BigDecimal physicalStock, BigDecimal reservedStock,
                            BigDecimal availableStock) {

    public static StockLevelRow zero(final UUID productId, final UUID productVariantId, final String sku,
                                     final String name, final BigDecimal reorderPoint) {
        return new StockLevelRow(productId, productVariantId, sku, name, reorderPoint, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

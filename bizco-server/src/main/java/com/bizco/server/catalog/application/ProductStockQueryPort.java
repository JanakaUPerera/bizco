package com.bizco.server.catalog.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only boundary onto Inventory's stock data, mirroring
 * {@code CustomerCreditQueryPort}'s role between Customer and Sales. Catalog needs a product's
 * current stock levels to populate {@code ProductSummaryResponse}, but Inventory's
 * {@code StockPostingService} already depends on Catalog's {@code ProductRepository} - a direct
 * Catalog -&gt; Inventory Java dependency the other way would make that a cycle. This interface lets
 * Catalog declare the one thing it needs without depending on Inventory's code; the implementation
 * (in {@code catalog.infrastructure}) satisfies it by reading Inventory's {@code v_available_stock}
 * view directly over JDBC instead.
 *
 * <p>Batched rather than one-product-at-a-time (unlike {@code CustomerCreditQueryPort}) because its
 * main caller is a paged product listing - one query per page, not one per row.
 */
public interface ProductStockQueryPort {

    /** Phase 6 Week 18 (DatabaseDesign.md §56.4): {@code ids} are now product *variant* ids, since
     *  stock lives at variant granularity — callers resolve each product's variant(s) before
     *  calling. Only entries for variants that are actually stock-tracked (their product is
     *  INVENTORY type) are expected; a requested id with no movements yet is present with
     *  {@link ProductStockLevel#zero()}, not absent. */
    Map<UUID, ProductStockLevel> levelsFor(Collection<UUID> ids);
}

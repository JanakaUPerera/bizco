package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.application.ProductStockLevel;
import com.bizco.server.catalog.application.ProductStockQueryPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Real {@link ProductStockQueryPort}, backed by {@code v_available_stock} (DatabaseDesign.md
 * &sect;15.6, V019) read straight over JDBC - same pattern as
 * {@code InvoiceCustomerCreditQueryAdapter} reading {@code v_invoice_balances} for Customer. No
 * import from the {@code inventory} package: this class only knows the view's column names, not
 * Inventory's Java types, which is what keeps Catalog -&gt; Inventory from becoming a compile-time
 * dependency (see {@link ProductStockQueryPort}'s Javadoc).
 */
@Component
public class InventoryProductStockQueryAdapter implements ProductStockQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public InventoryProductStockQueryAdapter(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, ProductStockLevel> levelsFor(final Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        final String placeholders = productIds.stream().map(id -> "?").collect(Collectors.joining(","));
        final String sql = """
                select product_id,
                       coalesce(physical_stock, 0) as physical_stock,
                       coalesce(reserved_stock, 0) as reserved_stock,
                       coalesce(available_stock, 0) as available_stock
                from v_available_stock
                where product_id in (%s)
                """.formatted(placeholders);
        final Map<UUID, ProductStockLevel> found = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            found.put(rs.getObject("product_id", UUID.class), new ProductStockLevel(rs.getBigDecimal("physical_stock"),
                    rs.getBigDecimal("reserved_stock"), rs.getBigDecimal("available_stock")));
        }, productIds.toArray());
        // A product with no rows in v_available_stock yet (no movements posted) still counts as
        // tracked stock at zero, not "not applicable" - only SERVICE-type products (which the caller
        // never asks about) should end up with no entry at all.
        for (final UUID id : productIds) {
            found.putIfAbsent(id, ProductStockLevel.zero());
        }
        return found;
    }
}

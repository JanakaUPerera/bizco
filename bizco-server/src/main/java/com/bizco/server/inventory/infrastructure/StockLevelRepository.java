package com.bizco.server.inventory.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads {@code v_available_stock} (V019, repointed to variant granularity by V028 — Phase 6 Week
 * 18, DatabaseDesign.md &sect;56.4), joined with product identity so callers get one browsable row
 * per variant instead of a bare number. Follows the {@code InvoiceCustomerCreditQueryAdapter}
 * convention of reading a view straight through {@link JdbcTemplate} rather than mapping it as a
 * JPA entity.
 *
 * <p>Phase 6 Week 19 (task 19.3): {@link #search}/{@link #lowStock}/{@link #lowStockCount} now
 * return one row per variant (every variant of every INVENTORY product), not one row per product —
 * the real per-variant browsing DatabaseDesign.md §56.3 Step 4's Week 18 comment on this class
 * deferred to this week. {@link #levelFor} is the one exception, deliberately kept product+default-
 * variant scoped (an explicit {@code pv.is_default} filter) since its {@code /stock/levels/{id}}
 * endpoint contract is keyed by {@code productId}, not a variant id. {@link #levelForVariant} stays
 * the real, variant-scoped accessor {@code StockPostingService} locks/checks against.
 */
@Repository
public class StockLevelRepository {

    private static final String ROW_SELECT = """
            select p.product_id, pv.product_variant_id, pv.sku, p.name, pv.reorder_point,
                   coalesce(v.physical_stock, 0) as physical_stock,
                   coalesce(v.reserved_stock, 0) as reserved_stock,
                   coalesce(v.available_stock, 0) as available_stock
            from products p
            join product_variants pv on pv.product_id = p.product_id
            left join v_available_stock v on v.product_variant_id = pv.product_variant_id
            where p.product_type = 'INVENTORY'
            """;

    private final JdbcTemplate jdbcTemplate;

    public StockLevelRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The real, variant-scoped level — used by {@code StockPostingService} for
     *  locking/availability checks, never for display. Returns an all-zero row for a variant with
     *  no movements yet. */
    public VariantStockLevel levelForVariant(final UUID productVariantId) {
        final List<VariantStockLevel> rows = jdbcTemplate.query(
                "select coalesce(physical_stock, 0) as physical_stock, coalesce(reserved_stock, 0) as reserved_stock,"
                        + " coalesce(available_stock, 0) as available_stock from v_available_stock"
                        + " where product_variant_id = ?",
                (rs, rowNum) -> new VariantStockLevel(rs.getBigDecimal("physical_stock"),
                        rs.getBigDecimal("reserved_stock"), rs.getBigDecimal("available_stock")),
                productVariantId);
        return rows.isEmpty() ? VariantStockLevel.zero() : rows.get(0);
    }

    /** Single-product level (default variant), used for the stock card/detail screen. Returns an
     *  all-zero row for a product with no movements yet. */
    public StockLevelRow levelFor(final UUID productId) {
        final List<StockLevelRow> rows = jdbcTemplate.query(
                ROW_SELECT + " and pv.is_default = true and p.product_id = ?", this::mapRow, productId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return jdbcTemplate.query("select sku, name, reorder_point from products where product_id = ?", rs -> {
            if (!rs.next()) {
                return StockLevelRow.zero(productId, null, null, null, BigDecimal.ZERO);
            }
            return StockLevelRow.zero(productId, null, rs.getString("sku"), rs.getString("name"),
                    rs.getBigDecimal("reorder_point"));
        }, productId);
    }

    /** Browsable stock levels, optionally filtered by name/SKU (12.3, stock movement history screen). */
    public Page<StockLevelRow> search(final String q, final Pageable pageable) {
        final String filter = " and (? is null or lower(p.name) like lower(concat('%', ?, '%')) or lower(pv.sku) = lower(?))";
        final String countSql = "select count(*) from (" + ROW_SELECT + filter + ") counted";
        final Long total = jdbcTemplate.queryForObject(countSql, Long.class, q, q, q);
        final String pageSql = ROW_SELECT + filter + " order by p.name limit ? offset ?";
        final List<StockLevelRow> rows = jdbcTemplate.query(pageSql, this::mapRow, q, q, q, pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** REC-STK-003: available stock at or below the (default variant's) configured reorder point.
     *  Both the low-stock dashboard count and the low-stock report must read this same query. */
    public Page<StockLevelRow> lowStock(final Pageable pageable) {
        final String filter = " and coalesce(v.available_stock, 0) <= pv.reorder_point";
        final String countSql = "select count(*) from (" + ROW_SELECT + filter + ") counted";
        final Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        final String pageSql = ROW_SELECT + filter + " order by p.name limit ? offset ?";
        final List<StockLevelRow> rows = jdbcTemplate.query(pageSql, this::mapRow, pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public long lowStockCount() {
        final Long count = jdbcTemplate.queryForObject(
                "select count(*) from products p"
                        + " join product_variants pv on pv.product_id = p.product_id"
                        + " left join v_available_stock v on v.product_variant_id = pv.product_variant_id"
                        + " where p.product_type = 'INVENTORY' and coalesce(v.available_stock, 0) <= pv.reorder_point",
                Long.class);
        return count == null ? 0 : count;
    }

    private StockLevelRow mapRow(final ResultSet rs, final int rowNum) throws SQLException {
        return new StockLevelRow(rs.getObject("product_id", UUID.class),
                rs.getObject("product_variant_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getBigDecimal("reorder_point"), rs.getBigDecimal("physical_stock"),
                rs.getBigDecimal("reserved_stock"), rs.getBigDecimal("available_stock"));
    }

    public record VariantStockLevel(BigDecimal physicalStock, BigDecimal reservedStock, BigDecimal availableStock) {
        public static VariantStockLevel zero() {
            return new VariantStockLevel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}

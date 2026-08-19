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
 * Reads {@code v_available_stock} (V019 / DatabaseDesign.md &sect;15.6), the same
 * physical/reserved/available formula the posting services rely on, joined with product identity so
 * callers get one browsable row per product instead of a bare number. Follows the
 * {@code InvoiceCustomerCreditQueryAdapter} convention of reading a view straight through
 * {@link JdbcTemplate} rather than mapping it as a JPA entity.
 */
@Repository
public class StockLevelRepository {

    private static final String ROW_SELECT = """
            select p.product_id, p.sku, p.name, p.reorder_point,
                   coalesce(v.physical_stock, 0) as physical_stock,
                   coalesce(v.reserved_stock, 0) as reserved_stock,
                   coalesce(v.available_stock, 0) as available_stock
            from products p
            left join v_available_stock v on v.product_id = p.product_id
            where p.product_type = 'INVENTORY'
            """;

    private final JdbcTemplate jdbcTemplate;

    public StockLevelRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Single-product level, used to validate availability at posting time and for the stock
     *  card/detail screen. Returns an all-zero row for a product with no movements yet. */
    public StockLevelRow levelFor(final UUID productId) {
        final List<StockLevelRow> rows = jdbcTemplate.query(ROW_SELECT + " and p.product_id = ?", this::mapRow,
                productId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return jdbcTemplate.query("select sku, name, reorder_point from products where product_id = ?", rs -> {
            if (!rs.next()) {
                return StockLevelRow.zero(productId, null, null, BigDecimal.ZERO);
            }
            return StockLevelRow.zero(productId, rs.getString("sku"), rs.getString("name"),
                    rs.getBigDecimal("reorder_point"));
        }, productId);
    }

    /** Browsable stock levels, optionally filtered by name/SKU (12.3, stock movement history screen). */
    public Page<StockLevelRow> search(final String q, final Pageable pageable) {
        final String filter = " and (? is null or lower(p.name) like lower(concat('%', ?, '%')) or lower(p.sku) = lower(?))";
        final String countSql = "select count(*) from (" + ROW_SELECT + filter + ") counted";
        final Long total = jdbcTemplate.queryForObject(countSql, Long.class, q, q, q);
        final String pageSql = ROW_SELECT + filter + " order by p.name limit ? offset ?";
        final List<StockLevelRow> rows = jdbcTemplate.query(pageSql, this::mapRow, q, q, q, pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** REC-STK-003: available stock at or below the product's configured reorder point. Both the
     *  low-stock dashboard count and the low-stock report must read this same query. */
    public Page<StockLevelRow> lowStock(final Pageable pageable) {
        final String filter = " and coalesce(v.available_stock, 0) <= p.reorder_point";
        final String countSql = "select count(*) from (" + ROW_SELECT + filter + ") counted";
        final Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        final String pageSql = ROW_SELECT + filter + " order by p.name limit ? offset ?";
        final List<StockLevelRow> rows = jdbcTemplate.query(pageSql, this::mapRow, pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public long lowStockCount() {
        final Long count = jdbcTemplate.queryForObject(
                "select count(*) from products p left join v_available_stock v on v.product_id = p.product_id "
                        + "where p.product_type = 'INVENTORY' and coalesce(v.available_stock, 0) <= p.reorder_point",
                Long.class);
        return count == null ? 0 : count;
    }

    private StockLevelRow mapRow(final ResultSet rs, final int rowNum) throws SQLException {
        return new StockLevelRow(rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getBigDecimal("reorder_point"), rs.getBigDecimal("physical_stock"),
                rs.getBigDecimal("reserved_stock"), rs.getBigDecimal("available_stock"));
    }
}

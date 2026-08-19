package com.bizco.server.purchasing.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads {@code v_goods_receipt_outstanding} (V023 / DatabaseDesign.md &sect;18) - the derived,
 * never-cached payable balance per POSTED goods receipt - shared by {@code SupplierReturnService}
 * and {@code SupplierPaymentService} to validate against the current outstanding amount rather than
 * trusting a client-supplied figure, and by the supplier statement screen (Week 15 task 15.5).
 * Follows the {@code StockLevelRepository}/{@code InvoiceBalanceRepository} convention of reading a
 * view straight through {@link JdbcTemplate} rather than mapping it as a JPA entity.
 */
@Repository
public class GoodsReceiptOutstandingRepository {

    private static final String ROW_SELECT = """
            select v.goods_receipt_id, v.receipt_number, v.supplier_id, s.name as supplier_name, v.receipt_date,
                   v.total_amount, v.returned_amount, v.paid_amount, v.outstanding_amount
            from v_goods_receipt_outstanding v
            join suppliers s on s.supplier_id = v.supplier_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public GoodsReceiptOutstandingRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Empty if the goods receipt is not POSTED (or does not exist) - the view only covers POSTED
     *  receipts, matching how payables can only ever apply to a posted document. */
    public Optional<Row> findById(final UUID goodsReceiptId) {
        final List<Row> rows = jdbcTemplate.query(ROW_SELECT + " where v.goods_receipt_id = ?", this::mapRow,
                goodsReceiptId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Page<Row> search(final UUID supplierId, final boolean outstandingOnly, final Pageable pageable) {
        final String filter = " where (? is null or v.supplier_id = ?) and (not ? or v.outstanding_amount > 0)";
        final String countSql = "select count(*) from (" + ROW_SELECT + filter + ") counted";
        final Long total = jdbcTemplate.queryForObject(countSql, Long.class, supplierId, supplierId, outstandingOnly);
        final String pageSql = ROW_SELECT + filter + " order by v.receipt_date desc limit ? offset ?";
        final List<Row> rows = jdbcTemplate.query(pageSql, this::mapRow, supplierId, supplierId, outstandingOnly,
                pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    private Row mapRow(final ResultSet rs, final int rowNum) throws SQLException {
        return new Row(rs.getObject("goods_receipt_id", UUID.class), rs.getString("receipt_number"),
                rs.getObject("supplier_id", UUID.class), rs.getString("supplier_name"),
                rs.getObject("receipt_date", LocalDate.class), rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("returned_amount"), rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("outstanding_amount"));
    }

    public record Row(UUID goodsReceiptId, String receiptNumber, UUID supplierId, String supplierName,
                      LocalDate receiptDate, BigDecimal totalAmount, BigDecimal returnedAmount,
                      BigDecimal paidAmount, BigDecimal outstandingAmount) {
    }
}

package com.bizco.server.sales.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the live, derived balance for one invoice from {@code v_invoice_balances}
 * (DatabaseDesign.md &sect;23.3) - shared by {@code PaymentAllocationService} and
 * {@code CreditNoteService}, both of which need to validate an amount against an invoice's
 * current outstanding balance rather than trusting a client-supplied figure.
 */
@Repository
public class InvoiceBalanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceBalanceRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Zero if the invoice isn't POSTED/VOIDED yet (v_invoice_balances only covers those statuses). */
    public BigDecimal balanceDue(final UUID invoiceId) {
        final java.util.List<BigDecimal> rows = jdbcTemplate.query(
                "select balance_due from v_invoice_balances where invoice_id = ?",
                (rs, rowNum) -> rs.getBigDecimal("balance_due"), invoiceId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }
}

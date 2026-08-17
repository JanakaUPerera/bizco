package com.bizco.server.customer.infrastructure;

import com.bizco.server.customer.application.CustomerCreditQueryPort;
import com.bizco.server.customer.application.CustomerReceivableSnapshot;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Real {@link CustomerCreditQueryPort} backed by {@code v_invoice_balances} (DatabaseDesign.md
 * &sect;23.3), now that Sales/PostSaleService produces the POSTED invoices and payments that view
 * reads. Replaces the pre-Sales stub that always returned zero.
 *
 * <p>Aging buckets are computed from {@code invoice_date}, not {@code due_date}: MVP.md's credit
 * policy tiers (SRS.md &sect;6.2.5) are phrased as "days outstanding since sale," which this
 * codebase treats as invoice age, matching {@code CustomerCreditPolicy}'s existing
 * {@code oldestOutstandingDays} parameter.
 */
@Component
public class InvoiceCustomerCreditQueryAdapter implements CustomerCreditQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceCustomerCreditQueryAdapter(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CustomerReceivableSnapshot snapshotFor(final UUID customerId) {
        return jdbcTemplate.query("""
                select
                    coalesce(sum(balance_due), 0) as outstanding,
                    coalesce(max(current_date - invoice_date) filter (where balance_due > 0), 0) as oldest_days,
                    coalesce(sum(balance_due) filter (where (current_date - invoice_date) between 0 and 30), 0) as days_0_30,
                    coalesce(sum(balance_due) filter (where (current_date - invoice_date) between 31 and 60), 0) as days_31_60,
                    coalesce(sum(balance_due) filter (where (current_date - invoice_date) between 61 and 90), 0) as days_61_90,
                    coalesce(sum(balance_due) filter (where (current_date - invoice_date) > 90), 0) as days_91_plus
                from v_invoice_balances
                where customer_id = ?
                  and payment_status not in ('VOIDED', 'PAID')
                """, resultSet -> {
                    if (!resultSet.next()) {
                        return CustomerReceivableSnapshot.zero();
                    }
                    return new CustomerReceivableSnapshot(
                            resultSet.getBigDecimal("outstanding"),
                            resultSet.getInt("oldest_days"),
                            money(resultSet.getBigDecimal("days_0_30")),
                            money(resultSet.getBigDecimal("days_31_60")),
                            money(resultSet.getBigDecimal("days_61_90")),
                            money(resultSet.getBigDecimal("days_91_plus")));
                }, customerId);
    }

    private BigDecimal money(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

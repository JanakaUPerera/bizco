package com.bizco.server.sales.infrastructure;

import com.bizco.server.sales.domain.CreditNote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    /**
     * Total quantity already returned against one original invoice line, across every credit
     * note regardless of settlement status - a return is a real event the moment it's issued, not
     * only once settled (DatabaseDesign.md &sect;14.2's cumulative-return-quantity rule).
     */
    @Query("""
            select coalesce(sum(line.quantityReturned), 0)
            from CreditNote cn join cn.lines line
            where line.originalInvoiceLineId = :originalInvoiceLineId
            """)
    BigDecimal sumReturnedQuantity(@Param("originalInvoiceLineId") UUID originalInvoiceLineId);

    /** InvoiceVoidService's "avoid duplicate economic reversal" check (StateMachines.md &sect;4.5). */
    boolean existsByOriginalInvoiceId(UUID originalInvoiceId);

    // :number is cast explicitly - an all-null String bind used inside concat()/like without one
    // hits a real Hibernate 6 + pgjdbc quirk ("function lower(bytea) does not exist"): with no
    // non-null occurrence of the parameter to infer a type from, pgjdbc sends it untyped and
    // Postgres's own inference for that position inside `||` picks bytea instead of text.
    @Query("""
            select cn from CreditNote cn
            where (:customerId is null or cn.customerId = :customerId)
              and (:invoiceId is null or cn.originalInvoiceId = :invoiceId)
              and (:number is null or lower(cn.creditNoteNumber) like lower(concat('%', cast(:number as string), '%')))
              and (:fromDate is null or cast(cn.issuedAt as date) >= :fromDate)
              and (:toDate is null or cast(cn.issuedAt as date) <= :toDate)
            order by cn.issuedAt desc
            """)
    List<CreditNote> search(@Param("customerId") UUID customerId, @Param("invoiceId") UUID invoiceId,
                            @Param("number") String number, @Param("fromDate") LocalDate fromDate,
                            @Param("toDate") LocalDate toDate);
}

package com.bizco.server.sales.infrastructure;

import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.domain.InvoiceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    /**
     * Row-locks the invoice for the rest of the transaction: credit-note issuance needs to read
     * and validate cumulative already-returned quantity against this invoice's lines without a
     * concurrent credit note for the same invoice racing it (DatabaseDesign.md &sect;14.2 "enforced
     * transactionally using row locks on the original invoice line and prior credit-note lines"),
     * the same pattern as {@code CustomerRepository.findByIdForUpdate} for CRD-CON-001.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    // :number is cast explicitly - see CreditNoteRepository.search's comment for why an all-null
    // String bind inside concat()/like needs this with Hibernate 6 + pgjdbc.
    @Query("""
            select i from Invoice i
            where (:number is null or lower(i.invoiceNumber) like lower(concat('%', cast(:number as string), '%')))
              and (:customerId is null or i.customerId = :customerId)
              and (:status is null or i.status = :status)
              and (:type is null or i.invoiceType = :type)
              and (:fromDate is null or i.invoiceDate >= :fromDate)
              and (:toDate is null or i.invoiceDate <= :toDate)
            order by i.createdAt desc
            """)
    Page<Invoice> search(@Param("number") String number, @Param("customerId") UUID customerId,
                         @Param("status") InvoiceStatus status, @Param("type") InvoiceType type,
                         @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                         Pageable pageable);
}

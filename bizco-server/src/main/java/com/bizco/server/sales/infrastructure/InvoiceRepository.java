package com.bizco.server.sales.infrastructure;

import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.domain.InvoiceType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("""
            select i from Invoice i
            where (:number is null or lower(i.invoiceNumber) like lower(concat('%', :number, '%')))
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

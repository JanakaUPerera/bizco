package com.bizco.server.finance.infrastructure;

import com.bizco.server.finance.domain.CustomerPayment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, UUID> {

    @Query("""
            select p from CustomerPayment p
            where p.customerId = :customerId
            order by p.paymentDate desc
            """)
    List<CustomerPayment> findByCustomerId(@Param("customerId") UUID customerId);

    @Query("""
            select distinct p from CustomerPayment p join p.allocations allocation
            where allocation.invoiceId = :invoiceId
            order by p.paymentDate desc
            """)
    List<CustomerPayment> findByInvoiceId(@Param("invoiceId") UUID invoiceId);
}

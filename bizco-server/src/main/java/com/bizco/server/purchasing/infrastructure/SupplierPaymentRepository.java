package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.SupplierPayment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {

    @Query("""
            select sp from SupplierPayment sp
            where (:supplierId is null or sp.supplierId = :supplierId)
            order by sp.createdAt desc
            """)
    Page<SupplierPayment> search(@Param("supplierId") UUID supplierId, Pageable pageable);
}

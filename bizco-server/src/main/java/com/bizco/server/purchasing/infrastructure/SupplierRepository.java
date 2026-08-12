package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    @Query("""
            select s from Supplier s
            where (:q is null or lower(s.name) like lower(concat('%', :q, '%'))
                or lower(s.supplierCode) = lower(:q) or s.phone = :q)
            and (:status is null or s.status = :status)
            """)
    Page<Supplier> search(@Param("q") String q, @Param("status") SupplierStatus status, Pageable pageable);
}

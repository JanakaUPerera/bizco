package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.PurchaseOrder;
import com.bizco.server.purchasing.domain.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /** Serializes concurrent goods receipts posting against the same PO - DevelopmentPlan.md
     *  Week 14 task 14.4, same {@code findByIdForUpdate} pattern used throughout this codebase. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select po from PurchaseOrder po
            where (:supplierId is null or po.supplierId = :supplierId)
              and (:status is null or po.status = :status)
            order by po.createdAt desc
            """)
    Page<PurchaseOrder> search(@Param("supplierId") UUID supplierId, @Param("status") PurchaseOrderStatus status,
                               Pageable pageable);
}

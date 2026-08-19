package com.bizco.server.inventory.infrastructure;

import com.bizco.server.inventory.domain.StockAdjustment;
import com.bizco.server.inventory.domain.StockAdjustmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    /**
     * Row-locks the adjustment for the decide transaction (STK-ADJ-004): two concurrent
     * approve/reject calls on the same PENDING adjustment must serialize so the second one sees the
     * first's committed decision and is rejected as already-decided, rather than both reading
     * PENDING and both posting a movement. Same pattern as {@code CustomerRepository.findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAdjustment a where a.id = :id")
    Optional<StockAdjustment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a from StockAdjustment a
            where (:productId is null or a.productId = :productId)
              and (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    Page<StockAdjustment> search(@Param("productId") UUID productId, @Param("status") StockAdjustmentStatus status,
                                 Pageable pageable);
}

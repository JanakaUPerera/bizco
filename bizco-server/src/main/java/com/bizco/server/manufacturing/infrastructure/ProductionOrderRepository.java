package com.bizco.server.manufacturing.infrastructure;

import com.bizco.server.manufacturing.domain.ProductionOrder;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {
    @Query("select p from ProductionOrder p where (:bomId is null or p.bomId = :bomId) "
            + "and (:finishedVariantId is null or p.finishedVariantId = :finishedVariantId) "
            + "order by p.createdAt desc")
    Page<ProductionOrder> search(@Param("bomId") UUID bomId, @Param("finishedVariantId") UUID finishedVariantId,
                                 Pageable pageable);
}

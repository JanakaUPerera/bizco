package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.ProductCostHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCostHistoryRepository extends JpaRepository<ProductCostHistory, UUID> {
    Page<ProductCostHistory> findByProductIdOrderByEffectiveAtDesc(UUID productId, Pageable pageable);
}

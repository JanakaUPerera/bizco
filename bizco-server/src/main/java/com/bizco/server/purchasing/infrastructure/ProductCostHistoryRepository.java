package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.ProductCostHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCostHistoryRepository extends JpaRepository<ProductCostHistory, UUID> {
    Page<ProductCostHistory> findByProductIdOrderByEffectiveAtDesc(UUID productId, Pageable pageable);

    /** Phase 6 Week 18: not called by any current endpoint (cost-history search stays
     *  product-scoped, per the DTO-shape decision) — added alongside the product-scoped query for
     *  future variant-level cost reporting (Week 19+). */
    Page<ProductCostHistory> findByProductVariantIdOrderByEffectiveAtDesc(UUID productVariantId, Pageable pageable);
}

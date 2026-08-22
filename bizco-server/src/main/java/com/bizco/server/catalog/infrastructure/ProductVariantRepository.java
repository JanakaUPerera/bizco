package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.ProductVariant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findAllByProductIdOrderByVariantLabelAsc(UUID productId);
    Optional<ProductVariant> findByProductIdAndDefaultVariantTrue(UUID productId);
    long countByProductId(UUID productId);

    /** Phase 6 Week 19 (task 19.1): a scanned barcode may belong to a specific variant, not just
     *  its parent product - checked first since it's the more specific match. */
    Optional<ProductVariant> findByBarcode(String barcode);

    /** Batched default-variant resolution for a page of products (Phase 6 Week 18) — one query
     *  per page instead of one per row, same batching rationale as
     *  {@code ProductStockQueryPort}'s Javadoc. */
    List<ProductVariant> findAllByProductIdInAndDefaultVariantTrue(Collection<UUID> productIds);

    /** Single-variant row lock for the stock-posting transaction (Phase 6 Week 18 —
     *  DatabaseDesign.md §56.3 Step 4 repoints {@code StockPostingService} to lock variant rows
     *  instead of product rows). Mirrors {@code ProductRepository.findByIdForUpdate} exactly. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") UUID id);

    /** Locks every variant in {@code ids} for a multi-line stock posting, in a single query
     *  ordered by {@code product_variant_id} (STK-CON-003 deadlock avoidance) — mirrors
     *  {@code ProductRepository.lockForStockUpdate} exactly, at variant granularity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id in :ids order by v.id")
    List<ProductVariant> lockForStockUpdate(@Param("ids") Collection<UUID> ids);
}

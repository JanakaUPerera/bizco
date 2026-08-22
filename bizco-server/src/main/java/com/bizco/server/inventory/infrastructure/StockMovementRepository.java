package com.bizco.server.inventory.infrastructure;

import com.bizco.server.inventory.domain.MovementType;
import com.bizco.server.inventory.domain.StockMovement;
import com.bizco.server.inventory.domain.StockReferenceType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    /** Phase 6 Week 19 (task 19.3): the variant-scoped drill-down the new stock screen uses once a
     *  product's variant-level rows are being browsed individually, rather than its whole history. */
    Page<StockMovement> findByProductVariantIdOrderByCreatedAtDesc(UUID productVariantId, Pageable pageable);

    Page<StockMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(StockReferenceType referenceType,
                                                                              UUID referenceId, Pageable pageable);

    Optional<StockMovement> findByMovementTypeAndSourceLineId(MovementType movementType, UUID sourceLineId);

    /** REC-STK-001/STK-LEDGER-001: authoritative physical stock for one product. */
    @Query("select coalesce(sum(m.quantity), 0) from StockMovement m where m.productId = :productId")
    BigDecimal physicalStockFor(@Param("productId") UUID productId);
}

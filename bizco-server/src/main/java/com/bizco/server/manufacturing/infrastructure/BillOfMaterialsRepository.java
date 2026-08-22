package com.bizco.server.manufacturing.infrastructure;

import com.bizco.server.manufacturing.domain.BillOfMaterials;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillOfMaterialsRepository extends JpaRepository<BillOfMaterials, UUID> {
    Optional<BillOfMaterials> findByFinishedVariantId(UUID finishedVariantId);

    @Query("select b from BillOfMaterials b where (:activeOnly = false or b.active = true)")
    Page<BillOfMaterials> search(@Param("activeOnly") boolean activeOnly, Pageable pageable);
}

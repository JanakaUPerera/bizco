package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.SupplierProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {

    Optional<SupplierProduct> findBySupplierIdAndProductId(UUID supplierId, UUID productId);

    List<SupplierProduct> findByProductId(UUID productId);

    @Query("""
            select sp from SupplierProduct sp
            where (:supplierId is null or sp.supplierId = :supplierId)
              and (:productId is null or sp.productId = :productId)
            """)
    Page<SupplierProduct> search(@Param("supplierId") UUID supplierId, @Param("productId") UUID productId,
                                 Pageable pageable);
}

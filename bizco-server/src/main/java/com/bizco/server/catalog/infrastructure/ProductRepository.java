package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByBarcode(String barcode);
    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select p from Product p
            join fetch p.category
            join fetch p.uom
            where (:q is null or lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.sku) = lower(:q) or p.barcode = :q)
            and (:categoryId is null or p.category.id = :categoryId)
            and (:type is null or p.productType = :type)
            and (:active is null or p.active = :active)
            """)
    Page<Product> search(@Param("q") String q, @Param("categoryId") Long categoryId,
                         @Param("type") ProductType type, @Param("active") Boolean active, Pageable pageable);
}

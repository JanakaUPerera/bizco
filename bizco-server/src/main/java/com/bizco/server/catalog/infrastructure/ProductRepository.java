package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByBarcode(String barcode);
    boolean existsByCategoryId(Long categoryId);

    /** Single-product row lock for the stock-posting transaction (job part, adjustment approval). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locks every product in {@code ids} for a multi-line stock posting (a sale cart, a GRN), in a
     * single query ordered by {@code product_id} (STK-CON-003). PostgreSQL applies {@code FOR UPDATE}
     * after sorting, so rows are locked in that same ascending-id order regardless of the caller's
     * cart/line order - two concurrent postings that share some products then always attempt to
     * acquire those locks in the same relative order, which is what rules out a lock-order deadlock
     * between them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockForStockUpdate(@Param("ids") Collection<UUID> ids);

    @Query("""
            select p from Product p
            join fetch p.category
            join fetch p.uom
            where (:q is null or lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.sku) = lower(:q) or p.barcode = :q)
            and (:categoryId is null or p.category.id = :categoryId)
            and (:type is null or p.productType = :type)
            and (:active is null or p.active = :active)
            and (:brandId is null or p.brand.id = :brandId)
            and (:attributeValueId is null or exists (
                select 1 from VariantAttributeValue vav
                where vav.productVariant.product = p and vav.attributeValue.id = :attributeValueId))
            """)
    Page<Product> search(@Param("q") String q, @Param("categoryId") Long categoryId,
                         @Param("type") ProductType type, @Param("active") Boolean active,
                         @Param("brandId") Long brandId, @Param("attributeValueId") Long attributeValueId,
                         Pageable pageable);
}

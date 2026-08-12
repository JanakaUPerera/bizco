package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.ProductCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByOrderByNameAsc();
    boolean existsByParentId(Long parentId);
}

package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.CategoryAttribute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, Long> {
    List<CategoryAttribute> findAllByCategoryIdOrderByAttributeNameAsc(Long categoryId);
    Optional<CategoryAttribute> findByCategoryIdAndAttributeId(Long categoryId, Long attributeId);
    boolean existsByCategoryIdAndAttributeId(Long categoryId, Long attributeId);
}

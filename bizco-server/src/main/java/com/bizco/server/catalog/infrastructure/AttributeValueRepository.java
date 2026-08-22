package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.AttributeValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {
    List<AttributeValue> findAllByAttributeIdOrderByValueAsc(Long attributeId);
}

package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.VariantAttributeValue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantAttributeValueRepository extends JpaRepository<VariantAttributeValue, UUID> {
    List<VariantAttributeValue> findAllByProductVariantId(UUID productVariantId);
}

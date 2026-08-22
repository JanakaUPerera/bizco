package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.Attribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {
    List<Attribute> findAllByOrderByNameAsc();
}

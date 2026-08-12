package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.Uom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UomRepository extends JpaRepository<Uom, Long> {
    List<Uom> findByActiveTrueOrderByCodeAsc();
}

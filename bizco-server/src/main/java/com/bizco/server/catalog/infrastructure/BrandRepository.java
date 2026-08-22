package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.Brand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findAllByOrderByNameAsc();
}

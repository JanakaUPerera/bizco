package com.bizco.server.catalog.infrastructure;

import com.bizco.server.catalog.domain.ServiceDefinition;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID> {
    @Query("""
            select s from ServiceDefinition s
            where (:q is null or lower(s.name) like lower(concat('%', :q, '%'))
                or lower(s.serviceCode) = lower(:q))
            and (:active is null or s.active = :active)
            """)
    Page<ServiceDefinition> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}

package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.StaffProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {

    boolean existsByUserIdAndTechnicianTrue(UUID userId);

    /** ApiContracts.md &sect;27.1: eligible technicians, optionally filtered by active status and a name search. */
    @Query("""
            select p from StaffProfile p join User u on u.id = p.userId
            where p.technician = true
            and (:active is null or u.active = :active)
            and (:q is null or lower(u.displayName) like lower(concat('%', :q, '%')))
            order by u.displayName
            """)
    List<StaffProfile> searchTechnicians(@Param("active") Boolean active, @Param("q") String q);
}

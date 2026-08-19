package com.bizco.server.scheduling.infrastructure;

import com.bizco.server.scheduling.domain.JobCard;
import com.bizco.server.scheduling.domain.JobCardStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No separate repository exists for {@code JobService}/{@code JobPart}/{@code JobEstimate} - they are managed only through the owning {@link JobCard} aggregate's cascaded collections, same convention as {@code HeldSaleItem}/{@code InvoiceLine}. */
public interface JobCardRepository extends JpaRepository<JobCard, UUID> {

    Optional<JobCard> findByAppointmentId(UUID appointmentId);

    /** ApiContracts.md &sect;29.1. */
    @Query("""
            select j from JobCard j
            where (:q is null or lower(j.jobNumber) like lower(concat('%', :q, '%'))
                or lower(j.serialNumber) like lower(concat('%', :q, '%')))
              and (:status is null or j.status = :status)
              and (:customerId is null or j.customerId = :customerId)
              and (:technicianId is null or j.technicianId = :technicianId)
              and (:from is null or j.createdAt >= :from)
              and (:to is null or j.createdAt < :to)
            order by j.createdAt desc
            """)
    Page<JobCard> search(@Param("q") String q, @Param("status") JobCardStatus status,
                         @Param("customerId") UUID customerId, @Param("technicianId") UUID technicianId,
                         @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}

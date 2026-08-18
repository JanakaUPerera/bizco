package com.bizco.server.scheduling.infrastructure;

import com.bizco.server.scheduling.domain.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** ApiContracts.md &sect;27.1: calendar/list query. */
    @Query("""
            select a from Appointment a
            where (:from is null or a.startAt >= :from)
              and (:to is null or a.startAt < :to)
              and (:technicianId is null or a.technicianId = :technicianId)
              and (:customerId is null or a.customerId = :customerId)
              and (:status is null or a.status = :status)
            order by a.startAt
            """)
    List<Appointment> search(@Param("from") Instant from, @Param("to") Instant to,
                             @Param("technicianId") UUID technicianId, @Param("customerId") UUID customerId,
                             @Param("status") com.bizco.server.scheduling.domain.AppointmentStatus status);

    /**
     * DatabaseDesign.md &sect;39 / DomainModel.md &sect;13.6: an advisory pre-check only, so the
     * caller can return a clean 409 for the common case. The
     * {@code ex_appointments_technician_overlap} GiST exclusion constraint - not this query - is
     * the transactionally-authoritative guard against double-booking under concurrency.
     */
    @Query(value = """
            select count(*) from appointments
            where technician_id = :technicianId
              and status in ('SCHEDULED','CONFIRMED','IN_PROGRESS')
              and (:excludeId is null or appointment_id <> :excludeId)
              and tstzrange(start_at, blocked_until_at, '[)') && tstzrange(:startAt, :blockedUntilAt, '[)')
            """, nativeQuery = true)
    long countOverlapping(@Param("technicianId") UUID technicianId, @Param("startAt") Instant startAt,
                          @Param("blockedUntilAt") Instant blockedUntilAt, @Param("excludeId") UUID excludeId);
}

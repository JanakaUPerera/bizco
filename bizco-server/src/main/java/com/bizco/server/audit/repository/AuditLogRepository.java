package com.bizco.server.audit.repository;

import com.bizco.server.audit.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:actionCode is null or a.actionCode = :actionCode)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:from is null or a.occurredAt >= :from)
              and (:to is null or a.occurredAt <= :to)
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("entityType") String entityType, @Param("actionCode") String actionCode,
                          @Param("actorUserId") UUID actorUserId, @Param("from") Instant from,
                          @Param("to") Instant to, Pageable pageable);
}

package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.RoleChangeAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, UUID> {
}


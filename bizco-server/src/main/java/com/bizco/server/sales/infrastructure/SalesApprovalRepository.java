package com.bizco.server.sales.infrastructure;

import com.bizco.server.sales.domain.SalesApproval;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesApprovalRepository extends JpaRepository<SalesApproval, UUID> {
}

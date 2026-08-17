package com.bizco.server.finance.infrastructure;

import com.bizco.server.finance.domain.CustomerRefund;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, UUID> {
}

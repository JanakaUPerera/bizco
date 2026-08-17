package com.bizco.server.finance.infrastructure;

import com.bizco.server.finance.domain.CustomerPayment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, UUID> {
}

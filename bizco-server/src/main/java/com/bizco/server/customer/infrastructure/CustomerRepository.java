package com.bizco.server.customer.infrastructure;

import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.domain.CustomerCategory;
import com.bizco.server.customer.domain.CustomerStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCustomerCode(String customerCode);

    @Lock(LockModeType.OPTIMISTIC)
    Optional<Customer> findWithLockById(UUID id);

    @Query("""
            select c from Customer c
            where (:category is null or c.category = :category)
              and (:status is null or c.status = :status)
              and (
                :q is null
                or lower(c.customerCode) like lower(concat('%', :q, '%'))
                or lower(c.name) like lower(concat('%', :q, '%'))
                or c.phone like concat('%', :q, '%')
              )
            order by
              case when :q is not null and lower(c.customerCode) = lower(:q) then 0 else 1 end,
              c.customerCode asc
            """)
    Page<Customer> search(@Param("q") String q, @Param("category") CustomerCategory category,
                          @Param("status") CustomerStatus status, Pageable pageable);
}


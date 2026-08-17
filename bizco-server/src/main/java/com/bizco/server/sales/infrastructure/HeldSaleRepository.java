package com.bizco.server.sales.infrastructure;

import com.bizco.server.sales.domain.HeldSale;
import com.bizco.server.sales.domain.HeldSaleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HeldSaleRepository extends JpaRepository<HeldSale, UUID> {

    @Query("""
            select h from HeldSale h
            where (:status is null or h.status = :status)
              and (:cashierId is null or h.cashierId = :cashierId)
            order by h.heldAt desc
            """)
    List<HeldSale> search(@Param("status") HeldSaleStatus status, @Param("cashierId") UUID cashierId);

    Optional<HeldSale> findByConvertedInvoiceId(UUID invoiceId);

    @Query("""
            select h from HeldSale h
            where h.status in (com.bizco.server.sales.domain.HeldSaleStatus.HELD, com.bizco.server.sales.domain.HeldSaleStatus.RESUMED)
              and h.expiresAt is not null and h.expiresAt < :now
            """)
    List<HeldSale> findExpirable(@Param("now") Instant now);
}

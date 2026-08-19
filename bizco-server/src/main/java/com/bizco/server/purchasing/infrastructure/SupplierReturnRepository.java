package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.SupplierReturn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, UUID> {

    /** Every prior return against one goods receipt, to compute cumulative returned quantity per
     *  line before accepting a new one (PUR-RET-002/PUR-RET-CON-001) - a goods receipt realistically
     *  has a handful of returns, so summing in Java mirrors {@code GoodsReceiptRepository}'s own
     *  {@code findByPurchaseOrderIdAndStatus} convention. */
    List<SupplierReturn> findByGoodsReceiptId(UUID goodsReceiptId);

    @Query("""
            select sr from SupplierReturn sr
            where (:supplierId is null or sr.supplierId = :supplierId)
              and (:goodsReceiptId is null or sr.goodsReceiptId = :goodsReceiptId)
            order by sr.createdAt desc
            """)
    Page<SupplierReturn> search(@Param("supplierId") UUID supplierId, @Param("goodsReceiptId") UUID goodsReceiptId,
                                Pageable pageable);
}

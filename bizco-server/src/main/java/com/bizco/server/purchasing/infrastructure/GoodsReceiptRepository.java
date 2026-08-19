package com.bizco.server.purchasing.infrastructure;

import com.bizco.server.purchasing.domain.GoodsReceipt;
import com.bizco.server.purchasing.domain.GoodsReceiptStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    /** Every posted receipt against one PO, to compute cumulative received qty per line
     *  (PurchaseOrderService/GoodsReceiptService - DevelopmentPlan.md Week 14 task 14.4). A PO
     *  realistically has a handful of receipts, so summing in Java over this list is simpler than a
     *  grouped JPQL query and performs fine at SME scale. */
    List<GoodsReceipt> findByPurchaseOrderIdAndStatus(UUID purchaseOrderId, GoodsReceiptStatus status);

    @Query("""
            select gr from GoodsReceipt gr
            where (:supplierId is null or gr.supplierId = :supplierId)
              and (:status is null or gr.status = :status)
            order by gr.createdAt desc
            """)
    Page<GoodsReceipt> search(@Param("supplierId") UUID supplierId, @Param("status") GoodsReceiptStatus status,
                              Pageable pageable);
}

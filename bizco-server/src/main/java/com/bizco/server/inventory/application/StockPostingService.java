package com.bizco.server.inventory.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.domain.MovementType;
import com.bizco.server.inventory.domain.StockMovement;
import com.bizco.server.inventory.domain.StockReferenceType;
import com.bizco.server.inventory.infrastructure.StockLevelRepository;
import com.bizco.server.inventory.infrastructure.StockMovementRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write path onto {@code stock_movements} (DatabaseDesign.md &sect;15, DevelopmentPlan.md
 * Week 12). Every module that affects physical stock - Sales (SALE/SALE_VOID/CUSTOMER_RETURN),
 * Scheduling (JOB_PART), Purchasing (GRN/SUPPLIER_RETURN, Week 13/14) and this module's own stock
 * adjustments - goes through this service rather than inserting a {@link StockMovement} directly, so
 * the lock-then-check-then-post sequence below is applied uniformly.
 *
 * <p><b>Locking contract</b> (STK-CON-001..003): every method here must run inside a transaction
 * that has already row-locked every {@code Product} it is about to post a movement for, via
 * {@link #lockProduct} or {@link #lockProducts}. {@link #lockProducts} always locks in ascending
 * {@code product_id} order regardless of the caller's own line/cart order (delegated to
 * {@link ProductRepository#lockForStockUpdate}), so two concurrent multi-line postings that share
 * some products always attempt to acquire those locks in the same relative order - this is what
 * rules out a lock-order deadlock between them (STK-CON-003). Once a product is locked, any other
 * transaction that also needs to touch that product's stock (a sale, a hold, an adjustment) blocks
 * until this one commits or rolls back, so the availability read in {@link #requireAvailable} is
 * safe from a concurrent lost update (STK-CON-001, STK-CON-002).
 *
 * <p>All methods require an existing transaction ({@link Propagation#MANDATORY}) - callers post
 * stock movements as one atomic step of their own larger transaction (e.g. invoice posting), never
 * as a side transaction that could commit independently of the aggregate it belongs to.
 */
@Service
public class StockPostingService {

    private final StockMovementRepository stockMovementRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;

    public StockPostingService(final StockMovementRepository stockMovementRepository,
                               final StockLevelRepository stockLevelRepository,
                               final ProductRepository productRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Product lockProduct(final UUID productId) {
        return productRepository.findByIdForUpdate(productId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found"));
    }

    /** Locks every product in {@code productIds} in a single, stably-ordered query. Duplicate ids
     *  are locked once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Product> lockProducts(final Collection<UUID> productIds) {
        final TreeSet<UUID> distinct = new TreeSet<>(productIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        final List<Product> locked = productRepository.lockForStockUpdate(distinct);
        final Map<UUID, Product> byId = new LinkedHashMap<>();
        for (final Product product : locked) {
            byId.put(product.getId(), product);
        }
        for (final UUID id : distinct) {
            if (!byId.containsKey(id)) {
                throw new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product was not found: " + id);
            }
        }
        return byId;
    }

    /** Physical - reserved, as of the calling transaction's already-acquired product lock. Caller
     *  must hold the product's row lock (see class Javadoc). */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal availableStock(final UUID productId) {
        return stockLevelRepository.levelFor(productId).availableStock();
    }

    /** SALE-004/STK-ADJ-005: rejects a deduction that would take available stock below zero. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(final UUID productId, final BigDecimal quantity) {
        if (availableStock(productId).compareTo(quantity) < 0) {
            throw new IdentityException(ApiErrorCode.STOCK_INSUFFICIENT, HttpStatus.CONFLICT,
                    "Insufficient available stock for product " + productId);
        }
    }

    /**
     * Posts one signed movement. {@code sourceLineId} plus {@code movementType} must uniquely
     * identify the source row (STK-SOURCE-001, {@code uq_stock_movement_source}); every call site in
     * this codebase reaches here only once per source row because it is itself wrapped by
     * {@code IdempotencyService} or guarded by a one-way domain state transition, so the unique index
     * is a defense-in-depth backstop, not a path this method recovers from - a genuine violation is
     * left to roll back the whole posting transaction like any other unexpected constraint failure.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement post(final UUID productId, final MovementType movementType, final BigDecimal signedQuantity,
                              final StockReferenceType referenceType, final UUID referenceId, final UUID sourceLineId,
                              final String notes, final UUID actorId) {
        return stockMovementRepository.save(new StockMovement(productId, movementType, signedQuantity, referenceType,
                referenceId, sourceLineId, notes, actorId));
    }

    /** SALE (StateMachines.md &sect;4.4): one negative movement per PRODUCT invoice line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSale(final UUID productId, final UUID invoiceId, final UUID invoiceLineId,
                         final BigDecimal quantity, final UUID actorId) {
        post(productId, MovementType.SALE, quantity.negate(), StockReferenceType.INVOICE, invoiceId, invoiceLineId,
                null, actorId);
    }

    /** SALE_VOID (StateMachines.md &sect;4.5): reverses a prior SALE with the opposite sign, keyed
     *  by the same invoice line under the distinct SALE_VOID movement type. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSaleVoid(final UUID productId, final UUID invoiceId, final UUID invoiceLineId,
                             final BigDecimal quantity, final UUID actorId) {
        post(productId, MovementType.SALE_VOID, quantity, StockReferenceType.INVOICE, invoiceId, invoiceLineId,
                "Invoice voided", actorId);
    }

    /** CUSTOMER_RETURN (StateMachines.md &sect;6): restockable credit-note line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postCustomerReturn(final UUID productId, final UUID creditNoteId, final UUID creditNoteLineId,
                                   final BigDecimal quantity, final UUID actorId) {
        post(productId, MovementType.CUSTOMER_RETURN, quantity, StockReferenceType.CREDIT_NOTE, creditNoteId,
                creditNoteLineId, null, actorId);
    }

    /** JOB_PART (StateMachines.md &sect;13): part consumed on a job card. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postJobPart(final UUID productId, final UUID jobCardId, final UUID jobPartId,
                            final BigDecimal quantity, final UUID actorId) {
        post(productId, MovementType.JOB_PART, quantity.negate(), StockReferenceType.JOB_CARD, jobCardId, jobPartId,
                null, actorId);
    }

    /** GRN (StateMachines.md &sect;14, DevelopmentPlan.md Week 14): the usable quantity
     *  (received - damaged - rejected) from one goods-receipt line. Purely additive, so no
     *  availability check - the enum/DB constant stays {@code GRN} (from V019, already applied)
     *  even though the business-facing document is now called a Goods Receipt (v1.4). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postGoodsReceipt(final UUID productId, final UUID goodsReceiptId, final UUID goodsReceiptItemId,
                                 final BigDecimal usableQuantity, final UUID actorId) {
        post(productId, MovementType.GRN, usableQuantity, StockReferenceType.GRN, goodsReceiptId, goodsReceiptItemId,
                null, actorId);
    }

    /** ADJUSTMENT (StateMachines.md &sect;17.4): the adjustment is both the reference aggregate and
     *  its own source row. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postAdjustment(final UUID productId, final UUID stockAdjustmentId, final BigDecimal signedQuantity,
                               final UUID actorId) {
        post(productId, MovementType.ADJUSTMENT, signedQuantity, StockReferenceType.STOCK_ADJUSTMENT,
                stockAdjustmentId, stockAdjustmentId, null, actorId);
    }
}

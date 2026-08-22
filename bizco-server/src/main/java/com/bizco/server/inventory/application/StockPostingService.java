package com.bizco.server.inventory.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
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
 * <p><b>Phase 6 Week 18</b> (DatabaseDesign.md &sect;56.3 Step 4): this service now locks/aggregates
 * at {@code product_variants} row granularity, not {@code products} - every product has exactly one
 * variant even without real variation (Week 17), so this is a transparent repoint for the common
 * case and the real granularity change once a product has more than one variant. Every posted
 * {@link StockMovement} still carries the row's parent {@code productId} too (resolved from the
 * variant), purely for display/reporting - nothing reads or locks on it anymore.
 *
 * <p><b>Locking contract</b> (STK-CON-001..003): every method here must run inside a transaction
 * that has already row-locked every {@code ProductVariant} it is about to post a movement for, via
 * {@link #lockVariant} or {@link #lockVariants}. {@link #lockVariants} always locks in ascending
 * {@code product_variant_id} order regardless of the caller's own line/cart order (delegated to
 * {@link ProductVariantRepository#lockForStockUpdate}), so two concurrent multi-line postings that
 * share some variants always attempt to acquire those locks in the same relative order - this is
 * what rules out a lock-order deadlock between them (STK-CON-003). Once a variant is locked, any
 * other transaction that also needs to touch that variant's stock (a sale, a hold, an adjustment)
 * blocks until this one commits or rolls back, so the availability read in {@link #requireAvailable}
 * is safe from a concurrent lost update (STK-CON-001, STK-CON-002).
 *
 * <p>All methods require an existing transaction ({@link Propagation#MANDATORY}) - callers post
 * stock movements as one atomic step of their own larger transaction (e.g. invoice posting), never
 * as a side transaction that could commit independently of the aggregate it belongs to.
 */
@Service
public class StockPostingService {

    private final StockMovementRepository stockMovementRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductVariantRepository variantRepository;

    public StockPostingService(final StockMovementRepository stockMovementRepository,
                               final StockLevelRepository stockLevelRepository,
                               final ProductVariantRepository variantRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProductVariant lockVariant(final UUID productVariantId) {
        return variantRepository.findByIdForUpdate(productVariantId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product variant was not found"));
    }

    /** Locks every variant in {@code productVariantIds} in a single, stably-ordered query.
     *  Duplicate ids are locked once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, ProductVariant> lockVariants(final Collection<UUID> productVariantIds) {
        final TreeSet<UUID> distinct = new TreeSet<>(productVariantIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        final List<ProductVariant> locked = variantRepository.lockForStockUpdate(distinct);
        final Map<UUID, ProductVariant> byId = new LinkedHashMap<>();
        for (final ProductVariant variant : locked) {
            byId.put(variant.getId(), variant);
        }
        for (final UUID id : distinct) {
            if (!byId.containsKey(id)) {
                throw new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product variant was not found: " + id);
            }
        }
        return byId;
    }

    /** Physical - reserved, as of the calling transaction's already-acquired variant lock. Caller
     *  must hold the variant's row lock (see class Javadoc). */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal availableStock(final UUID productVariantId) {
        return stockLevelRepository.levelForVariant(productVariantId).availableStock();
    }

    /** SALE-004/STK-ADJ-005: rejects a deduction that would take available stock below zero. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(final UUID productVariantId, final BigDecimal quantity) {
        if (availableStock(productVariantId).compareTo(quantity) < 0) {
            throw new IdentityException(ApiErrorCode.STOCK_INSUFFICIENT, HttpStatus.CONFLICT,
                    "Insufficient available stock for variant " + productVariantId);
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
    public StockMovement post(final UUID productVariantId, final MovementType movementType,
                              final BigDecimal signedQuantity, final StockReferenceType referenceType,
                              final UUID referenceId, final UUID sourceLineId, final String notes,
                              final UUID actorId) {
        return stockMovementRepository.save(new StockMovement(parentProductId(productVariantId), productVariantId,
                movementType, signedQuantity, referenceType, referenceId, sourceLineId, notes, actorId));
    }

    /** SALE (StateMachines.md &sect;4.4): one negative movement per PRODUCT invoice line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSale(final UUID productVariantId, final UUID invoiceId, final UUID invoiceLineId,
                         final BigDecimal quantity, final UUID actorId) {
        post(productVariantId, MovementType.SALE, quantity.negate(), StockReferenceType.INVOICE, invoiceId,
                invoiceLineId, null, actorId);
    }

    /** SALE_VOID (StateMachines.md &sect;4.5): reverses a prior SALE with the opposite sign, keyed
     *  by the same invoice line under the distinct SALE_VOID movement type. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSaleVoid(final UUID productVariantId, final UUID invoiceId, final UUID invoiceLineId,
                             final BigDecimal quantity, final UUID actorId) {
        post(productVariantId, MovementType.SALE_VOID, quantity, StockReferenceType.INVOICE, invoiceId, invoiceLineId,
                "Invoice voided", actorId);
    }

    /** CUSTOMER_RETURN (StateMachines.md &sect;6): restockable credit-note line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postCustomerReturn(final UUID productVariantId, final UUID creditNoteId, final UUID creditNoteLineId,
                                   final BigDecimal quantity, final UUID actorId) {
        post(productVariantId, MovementType.CUSTOMER_RETURN, quantity, StockReferenceType.CREDIT_NOTE, creditNoteId,
                creditNoteLineId, null, actorId);
    }

    /** JOB_PART (StateMachines.md &sect;13): part consumed on a job card. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postJobPart(final UUID productVariantId, final UUID jobCardId, final UUID jobPartId,
                            final BigDecimal quantity, final UUID actorId) {
        post(productVariantId, MovementType.JOB_PART, quantity.negate(), StockReferenceType.JOB_CARD, jobCardId,
                jobPartId, null, actorId);
    }

    /** GRN (StateMachines.md &sect;14, DevelopmentPlan.md Week 14): the usable quantity
     *  (received - damaged - rejected) from one goods-receipt line. Purely additive, so no
     *  availability check - the enum/DB constant stays {@code GRN} (from V019, already applied)
     *  even though the business-facing document is now called a Goods Receipt (v1.4). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postGoodsReceipt(final UUID productVariantId, final UUID goodsReceiptId,
                                 final UUID goodsReceiptItemId, final BigDecimal usableQuantity,
                                 final UUID actorId) {
        post(productVariantId, MovementType.GRN, usableQuantity, StockReferenceType.GRN, goodsReceiptId,
                goodsReceiptItemId, null, actorId);
    }

    /** SUPPLIER_RETURN (DevelopmentPlan.md Week 15): goods sent back to a supplier out of a POSTED
     *  goods receipt. Deducts stock, so it goes through the same availability check as SALE - a
     *  variant can only be returned to its supplier if it is still physically on hand. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSupplierReturn(final UUID productVariantId, final UUID supplierReturnId,
                                   final UUID supplierReturnItemId, final BigDecimal quantity,
                                   final UUID actorId) {
        requireAvailable(productVariantId, quantity);
        post(productVariantId, MovementType.SUPPLIER_RETURN, quantity.negate(), StockReferenceType.SUPPLIER_RETURN,
                supplierReturnId, supplierReturnItemId, null, actorId);
    }

    /** ADJUSTMENT (StateMachines.md &sect;17.4): the adjustment is both the reference aggregate and
     *  its own source row. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postAdjustment(final UUID productVariantId, final UUID stockAdjustmentId,
                               final BigDecimal signedQuantity, final UUID actorId) {
        post(productVariantId, MovementType.ADJUSTMENT, signedQuantity, StockReferenceType.STOCK_ADJUSTMENT,
                stockAdjustmentId, stockAdjustmentId, null, actorId);
    }

    /** PRODUCTION_IN (Phase 7 Week 21, SRS.md &sect;6.4.11.2 step 5): the finished variant produced
     *  by a BOM's Produce transaction. Purely additive - no availability check, mirrors
     *  {@link #postGoodsReceipt}. Only called for a {@code STOCKED} production (task 21.3) - a
     *  {@code MADE_TO_ORDER} production never calls this, so nothing sits in finished-goods stock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postProductionIn(final UUID productVariantId, final UUID productionOrderId, final BigDecimal quantity,
                                 final UUID actorId) {
        post(productVariantId, MovementType.PRODUCTION_IN, quantity, StockReferenceType.PRODUCTION_ORDER,
                productionOrderId, productionOrderId, null, actorId);
    }

    /** PRODUCTION_OUT (Phase 7 Week 21): one negative movement per BOM component consumed by a
     *  Produce transaction. The caller must already have validated availability for every
     *  component across the whole production BEFORE posting any movement for it (SRS.md
     *  &sect;6.4.11.2 step 4 - reject the whole production, never a partial one) - this method does
     *  not call {@link #requireAvailable} itself, so it must never be called first. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postProductionOut(final UUID productVariantId, final UUID productionOrderId,
                                  final UUID productionOrderItemId, final BigDecimal quantity, final UUID actorId) {
        post(productVariantId, MovementType.PRODUCTION_OUT, quantity.negate(), StockReferenceType.PRODUCTION_ORDER,
                productionOrderId, productionOrderItemId, null, actorId);
    }

    /** Resolves the variant's parent product id for {@link StockMovement}'s dual-write
     *  (DatabaseDesign.md &sect;56.3 Step 4's "both columns populated and consistent" during
     *  cutover) - cheap within this transaction since the variant was already loaded/locked by
     *  {@link #lockVariant}/{@link #lockVariants} moments earlier, so this hits Hibernate's
     *  persistence-context cache rather than issuing a second query. */
    private UUID parentProductId(final UUID productVariantId) {
        return variantRepository.findById(productVariantId)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product variant was not found: " + productVariantId))
                .getProduct().getId();
    }
}

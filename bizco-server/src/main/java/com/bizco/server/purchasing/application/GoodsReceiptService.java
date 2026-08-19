package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptItemResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSearchResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSummaryResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.ProductCostHistoryResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.ProductCostHistorySearchResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.purchasing.domain.GoodsReceipt;
import com.bizco.server.purchasing.domain.GoodsReceiptItem;
import com.bizco.server.purchasing.domain.GoodsReceiptStatus;
import com.bizco.server.purchasing.domain.ProductCostHistory;
import com.bizco.server.purchasing.domain.PurchaseOrder;
import com.bizco.server.purchasing.domain.PurchaseOrderItem;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierProduct;
import com.bizco.server.purchasing.infrastructure.GoodsReceiptRepository;
import com.bizco.server.purchasing.infrastructure.ProductCostHistoryRepository;
import com.bizco.server.purchasing.infrastructure.PurchaseOrderRepository;
import com.bizco.server.purchasing.infrastructure.SupplierProductRepository;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goods Receipt draft/post workflow (DatabaseDesign.md &sect;17.5-17.7, DevelopmentPlan.md Week 14):
 * the document that actually moves stock. Posting is one atomic transaction covering stock, cost
 * history, product/supplier-product cost, and the linked Purchase Order's receiving progress -
 * StateMachines.md &sect;14, MVP.md &sect;30.3.
 */
@Service
public class GoodsReceiptService {

    private final GoodsReceiptRepository repository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductCostHistoryRepository costHistoryRepository;
    private final StockPostingService stockPostingService;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public GoodsReceiptService(final GoodsReceiptRepository repository, final PurchaseOrderRepository purchaseOrderRepository,
                               final SupplierRepository supplierRepository, final ProductRepository productRepository,
                               final SupplierProductRepository supplierProductRepository,
                               final ProductCostHistoryRepository costHistoryRepository,
                               final StockPostingService stockPostingService,
                               final DocumentSequenceRepository documentSequenceRepository,
                               final IdempotencyService idempotencyService, final UserRepository userRepository,
                               final AuditService auditService) {
        this.repository = repository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.costHistoryRepository = costHistoryRepository;
        this.stockPostingService = stockPostingService;
        this.documentSequenceRepository = documentSequenceRepository;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public GoodsReceiptDetailResponse createDraft(final CreateGoodsReceiptRequest request,
                                                   final Authentication authentication) {
        requireSupplier(request.supplierId());
        if (request.purchaseOrderId() != null && !purchaseOrderRepository.existsById(request.purchaseOrderId())) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Purchase order was not found");
        }
        final UUID actorId = actor(authentication);
        final GoodsReceipt gr = new GoodsReceipt(request.purchaseOrderId(), request.supplierId(),
                request.supplierReference(), request.receiptDate() == null ? LocalDate.now() : request.receiptDate(),
                request.notes(), actorId);
        final GoodsReceipt saved = repository.save(gr);
        auditService.record("GOODS_RECEIPT", saved.getId().toString(), "GOODS_RECEIPT_DRAFT_CREATED", actorId,
                Map.of("supplierId", request.supplierId().toString()));
        return toDetail(saved);
    }

    @Transactional
    public GoodsReceiptDetailResponse addItem(final UUID id, final AddGoodsReceiptItemRequest request) {
        final GoodsReceipt gr = load(id);
        final List<FieldError> errors = new ArrayList<>();
        if (request.productId() == null) {
            errors.add(new FieldError("productId", "REQUIRED", "Product is required."));
        }
        if (request.quantityReceived() == null || request.quantityReceived().signum() <= 0) {
            errors.add(new FieldError("quantityReceived", "INVALID", "Quantity received must be greater than zero."));
        }
        if (request.unitCost() == null || request.unitCost().signum() < 0) {
            errors.add(new FieldError("unitCost", "INVALID", "Unit cost must be zero or greater."));
        }
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
        final Product product = productRepository.findById(request.productId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found"));
        gr.addItem(new GoodsReceiptItem(request.purchaseOrderItemId(), product.getId(), request.quantityReceived(),
                request.quantityDamaged(), request.quantityRejected(), request.unitCost()));
        recalculate(gr);
        repository.flush();
        auditService.record("GOODS_RECEIPT", id.toString(), "GOODS_RECEIPT_ITEM_ADDED", null,
                Map.of("productId", product.getId().toString()));
        return toDetail(gr);
    }

    @Transactional
    public GoodsReceiptDetailResponse removeItem(final UUID id, final UUID itemId) {
        final GoodsReceipt gr = load(id);
        gr.removeItem(itemId);
        recalculate(gr);
        repository.flush();
        auditService.record("GOODS_RECEIPT", id.toString(), "GOODS_RECEIPT_ITEM_REMOVED", null,
                Map.of("goodsReceiptItemId", itemId.toString()));
        return toDetail(gr);
    }

    /**
     * DRAFT -&gt; POSTED. Atomic: stock (usable qty only), cost history, product/supplier-product
     * cost, and - if linked to a PO - that PO's receiving progress, all in one transaction
     * (MVP.md &sect;30.3). Idempotent (one of Week 14's critical posting commands).
     */
    @Transactional
    public IdempotentResult<GoodsReceiptDetailResponse> post(final UUID idempotencyKey, final UUID id,
                                                              final PostGoodsReceiptRequest request,
                                                              final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "goods_receipt.post",
                Map.of("goodsReceiptId", id, "request", request), GoodsReceiptDetailResponse.class,
                () -> doPost(idempotencyKey, id, request, actorId));
    }

    private GoodsReceiptDetailResponse doPost(final UUID idempotencyKey, final UUID id,
                                              final PostGoodsReceiptRequest request, final UUID actorId) {
        final GoodsReceipt gr = load(id);
        assertVersion(gr, request.version());

        final Map<UUID, Product> lockedProducts = stockPostingService.lockProducts(
                gr.getItems().stream().map(GoodsReceiptItem::getProductId).distinct().toList());

        final String receiptNumber = documentSequenceRepository.formatDaily("GRN", gr.getReceiptDate(),
                documentSequenceRepository.nextDailyValue("GRN", gr.getReceiptDate(), "GRN", 4), 4);
        try {
            gr.post(idempotencyKey, receiptNumber, Instant.now());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_DRAFT, HttpStatus.CONFLICT, ex.getMessage());
        }

        for (final GoodsReceiptItem item : gr.getItems()) {
            final BigDecimal usable = item.usableQuantity();
            if (usable.signum() > 0) {
                stockPostingService.postGoodsReceipt(item.getProductId(), gr.getId(), item.getId(), usable, actorId);
            }
            costHistoryRepository.save(new ProductCostHistory(item.getProductId(), item.getId(), item.getUnitCost()));
            lockedProducts.get(item.getProductId()).recordPurchaseCost(item.getUnitCost());
            supplierProductRepository.findBySupplierIdAndProductId(gr.getSupplierId(), item.getProductId())
                    .ifPresent(sp -> sp.recordPurchase(item.getUnitCost()));
        }

        if (gr.getPurchaseOrderId() != null) {
            updatePurchaseOrderProgress(gr.getPurchaseOrderId());
        }

        try {
            repository.flush();
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.GOODS_RECEIPT_SUPPLIER_REFERENCE_DUPLICATE, HttpStatus.CONFLICT,
                    "This supplier reference was already used on another posted goods receipt");
        }
        auditService.record("GOODS_RECEIPT", gr.getId().toString(), "GOODS_RECEIPT_POSTED", actorId,
                Map.of("receiptNumber", receiptNumber, "totalAmount", gr.getTotalAmount().toPlainString()));
        return toDetail(gr);
    }

    /** DevelopmentPlan.md Week 14 task 14.4: locks the PO, compares cumulative posted-receipt
     *  quantity against each line's ordered quantity, and records partial/full progress. */
    private void updatePurchaseOrderProgress(final UUID purchaseOrderId) {
        final PurchaseOrder po = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.PURCHASE_ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Purchase order was not found"));
        final Map<UUID, BigDecimal> receivedByPoItem = new HashMap<>();
        for (final GoodsReceipt receipt : repository.findByPurchaseOrderIdAndStatus(purchaseOrderId, GoodsReceiptStatus.POSTED)) {
            for (final GoodsReceiptItem item : receipt.getItems()) {
                if (item.getPurchaseOrderItemId() != null) {
                    receivedByPoItem.merge(item.getPurchaseOrderItemId(), item.getQuantityReceived(), BigDecimal::add);
                }
            }
        }
        boolean anyReceived = false;
        boolean fullyReceived = true;
        for (final PurchaseOrderItem item : po.getItems()) {
            final BigDecimal received = receivedByPoItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            if (received.signum() > 0) {
                anyReceived = true;
            }
            if (received.compareTo(item.getQuantityOrdered()) < 0) {
                fullyReceived = false;
            }
        }
        if (anyReceived) {
            try {
                po.recordReceiptProgress(fullyReceived, Instant.now());
            } catch (final IllegalStateException ex) {
                throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_INVALID_TRANSITION, HttpStatus.CONFLICT,
                        ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDetailResponse get(final UUID id) {
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public GoodsReceiptSearchResponse search(final UUID supplierId, final String status, final int page,
                                             final int size) {
        final Pageable pageable = pageable(page, size);
        final GoodsReceiptStatus statusFilter = status == null || status.isBlank() ? null
                : GoodsReceiptStatus.valueOf(status.trim().toUpperCase());
        final Page<GoodsReceipt> result = repository.search(supplierId, statusFilter, pageable);
        return new GoodsReceiptSearchResponse(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductCostHistorySearchResponse costHistory(final UUID productId, final int page, final int size) {
        final Page<ProductCostHistory> result = costHistoryRepository.findByProductIdOrderByEffectiveAtDesc(productId,
                pageable(page, size));
        final List<ProductCostHistoryResponse> data = result.getContent().stream()
                .map(h -> new ProductCostHistoryResponse(h.getId(), h.getProductId(), h.getGoodsReceiptItemId(),
                        h.getUnitCost(), h.getEffectiveAt()))
                .toList();
        return new ProductCostHistorySearchResponse(data, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private void recalculate(final GoodsReceipt gr) {
        final BigDecimal total = gr.getItems().stream().map(GoodsReceiptItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        gr.applyCalculatedTotal(total);
    }

    private void requireSupplier(final UUID supplierId) {
        if (supplierId == null || !supplierRepository.existsById(supplierId)) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_NOT_FOUND, HttpStatus.NOT_FOUND, "Supplier was not found");
        }
    }

    private void assertVersion(final GoodsReceipt gr, final long expectedVersion) {
        if (gr.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Goods receipt was modified by another user");
        }
    }

    private GoodsReceipt load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Goods receipt was not found"));
    }

    private Pageable pageable(final int page, final int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private GoodsReceiptDetailResponse toDetail(final GoodsReceipt gr) {
        final Supplier supplier = supplierRepository.findById(gr.getSupplierId()).orElse(null);
        final PurchaseOrder po = gr.getPurchaseOrderId() == null ? null
                : purchaseOrderRepository.findById(gr.getPurchaseOrderId()).orElse(null);
        final List<GoodsReceiptItemResponse> items = gr.getItems().stream().map(this::toItemResponse).toList();
        return new GoodsReceiptDetailResponse(gr.getId(), gr.getReceiptNumber(), gr.getPurchaseOrderId(),
                po == null ? null : po.getPoNumber(), gr.getSupplierId(), supplier == null ? null : supplier.getName(),
                gr.getSupplierReference(), gr.getReceiptDate(), gr.getStatus().name(), gr.getTotalAmount(),
                gr.getNotes(), gr.getCreatedBy(), gr.getCreatedAt(), gr.getPostedAt(), gr.getVersion(), items);
    }

    private GoodsReceiptItemResponse toItemResponse(final GoodsReceiptItem item) {
        final Product product = productRepository.findById(item.getProductId()).orElse(null);
        return new GoodsReceiptItemResponse(item.getId(), item.getLineNumber(), item.getPurchaseOrderItemId(),
                item.getProductId(), product == null ? null : product.getSku(), product == null ? null : product.getName(),
                item.getQuantityReceived(), item.getQuantityDamaged(), item.getQuantityRejected(),
                item.usableQuantity(), item.getUnitCost(), item.getTotalCost());
    }

    private GoodsReceiptSummaryResponse toSummary(final GoodsReceipt gr) {
        final Supplier supplier = supplierRepository.findById(gr.getSupplierId()).orElse(null);
        return new GoodsReceiptSummaryResponse(gr.getId(), gr.getReceiptNumber(), gr.getSupplierId(),
                supplier == null ? null : supplier.getName(), gr.getReceiptDate(), gr.getStatus().name(),
                gr.getTotalAmount(), gr.getVersion());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

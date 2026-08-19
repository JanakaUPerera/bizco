package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CancelPurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderItemResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderSearchResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderSummaryResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.UpdatePurchaseOrderHeaderRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.domain.PurchaseOrder;
import com.bizco.server.purchasing.domain.PurchaseOrderItem;
import com.bizco.server.purchasing.domain.PurchaseOrderStatus;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.infrastructure.PurchaseOrderRepository;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import com.bizco.server.system.entity.SystemConfigEntry;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import com.bizco.server.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase Order draft/approve/send/cancel workflow (DatabaseDesign.md &sect;17.3, DevelopmentPlan.md
 * Week 13). A PO whose {@code total_amount} is at or above the configurable
 * {@code purchasing.po_approval_threshold} must reach {@link PurchaseOrderStatus#APPROVED}
 * (requiring {@code purchasing.po.approve}) before it can be {@link PurchaseOrderStatus#SENT}; below
 * the threshold, {@code purchasing.po.create} alone is enough to send directly from DRAFT.
 */
@Service
public class PurchaseOrderService {

    private static final String THRESHOLD_KEY = "purchasing.po_approval_threshold";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("50000.00");

    private final PurchaseOrderRepository repository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PurchaseOrderService(final PurchaseOrderRepository repository, final SupplierRepository supplierRepository,
                                final ProductRepository productRepository,
                                final SystemConfigRepository systemConfigRepository,
                                final DocumentSequenceRepository documentSequenceRepository,
                                final IdempotencyService idempotencyService, final UserRepository userRepository,
                                final AuditService auditService, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PurchaseOrderDetailResponse createDraft(final CreatePurchaseOrderRequest request,
                                                    final Authentication authentication) {
        requireSupplier(request.supplierId());
        final UUID actorId = actor(authentication);
        final PurchaseOrder po = new PurchaseOrder(request.supplierId(), request.poDate() == null ? LocalDate.now()
                : request.poDate(), request.expectedDate(), request.validUntil(), request.notes(), actorId);
        final PurchaseOrder saved = repository.save(po);
        auditService.record("PURCHASE_ORDER", saved.getId().toString(), "PURCHASE_ORDER_DRAFT_CREATED", actorId,
                java.util.Map.of("supplierId", request.supplierId().toString()));
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDetailResponse get(final UUID id) {
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderSearchResponse search(final UUID supplierId, final String status, final int page,
                                              final int size) {
        final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final PurchaseOrderStatus statusFilter = status == null || status.isBlank() ? null
                : PurchaseOrderStatus.valueOf(status.trim().toUpperCase());
        final var result = repository.search(supplierId, statusFilter, pageable);
        return new PurchaseOrderSearchResponse(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public PurchaseOrderDetailResponse updateHeader(final UUID id, final UpdatePurchaseOrderHeaderRequest request) {
        final PurchaseOrder po = load(id);
        assertVersion(po, request.version());
        po.updateHeader(request.poDate(), request.expectedDate(), request.validUntil(), request.notes());
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_DRAFT_UPDATED", null, java.util.Map.of());
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDetailResponse addItem(final UUID id, final AddPurchaseOrderItemRequest request) {
        final PurchaseOrder po = load(id);
        final List<FieldError> errors = new ArrayList<>();
        if (request.productId() == null) {
            errors.add(new FieldError("productId", "REQUIRED", "Product is required."));
        }
        if (request.quantityOrdered() == null || request.quantityOrdered().signum() <= 0) {
            errors.add(new FieldError("quantityOrdered", "INVALID", "Quantity must be greater than zero."));
        }
        if (request.unitPrice() == null || request.unitPrice().signum() < 0) {
            errors.add(new FieldError("unitPrice", "INVALID", "Unit price must be zero or greater."));
        }
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
        final Product product = productRepository.findById(request.productId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found"));
        po.addItem(new PurchaseOrderItem(product.getId(), request.quantityOrdered(), request.unitPrice()));
        recalculate(po);
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_ITEM_ADDED", null,
                java.util.Map.of("productId", product.getId().toString()));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDetailResponse removeItem(final UUID id, final UUID itemId) {
        final PurchaseOrder po = load(id);
        po.removeItem(itemId);
        recalculate(po);
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_ITEM_REMOVED", null,
                java.util.Map.of("purchaseOrderItemId", itemId.toString()));
        return toDetail(po);
    }

    /** DRAFT -&gt; APPROVED. Idempotent - one of DevelopmentPlan.md Week 13's critical posting-style
     *  commands. */
    @Transactional
    public IdempotentResult<PurchaseOrderDetailResponse> approve(final UUID idempotencyKey, final UUID id,
                                                                  final DecidePurchaseOrderRequest request,
                                                                  final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "purchase_order.approve",
                java.util.Map.of("purchaseOrderId", id, "request", request), PurchaseOrderDetailResponse.class,
                () -> doApprove(idempotencyKey, id, request, actorId));
    }

    private PurchaseOrderDetailResponse doApprove(final UUID idempotencyKey, final UUID id,
                                                  final DecidePurchaseOrderRequest request, final UUID actorId) {
        final PurchaseOrder po = load(id);
        assertVersion(po, request.version());
        final String poNumber = allocateNumber(po);
        try {
            po.approve(idempotencyKey, poNumber, actorId, Instant.now());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_NOT_DRAFT, HttpStatus.CONFLICT, ex.getMessage());
        }
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_APPROVED", actorId,
                java.util.Map.of("poNumber", poNumber, "totalAmount", po.getTotalAmount().toPlainString()));
        return toDetail(po);
    }

    /** DRAFT (below threshold) or APPROVED -&gt; SENT. Idempotent. */
    @Transactional
    public IdempotentResult<PurchaseOrderDetailResponse> send(final UUID idempotencyKey, final UUID id,
                                                               final DecidePurchaseOrderRequest request,
                                                               final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "purchase_order.send",
                java.util.Map.of("purchaseOrderId", id, "request", request), PurchaseOrderDetailResponse.class,
                () -> doSend(idempotencyKey, id, request, actorId));
    }

    private PurchaseOrderDetailResponse doSend(final UUID idempotencyKey, final UUID id,
                                               final DecidePurchaseOrderRequest request, final UUID actorId) {
        final PurchaseOrder po = load(id);
        assertVersion(po, request.version());
        if (po.getStatus() == PurchaseOrderStatus.DRAFT && po.getTotalAmount().compareTo(approvalThreshold()) >= 0) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_APPROVAL_REQUIRED, HttpStatus.CONFLICT,
                    "This purchase order's total requires manager/owner approval before it can be sent");
        }
        final String poNumber = allocateNumber(po);
        try {
            po.send(idempotencyKey, poNumber, Instant.now());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        }
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_SENT", actorId,
                java.util.Map.of("poNumber", poNumber));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDetailResponse cancel(final UUID id, final CancelPurchaseOrderRequest request,
                                              final Authentication authentication) {
        final PurchaseOrder po = load(id);
        assertVersion(po, request.version());
        try {
            po.cancel(request.reason(), Instant.now());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        } catch (final IllegalArgumentException ex) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_CANCELLED", actor(authentication),
                java.util.Map.of("reason", request.reason()));
        return toDetail(po);
    }

    /** DevelopmentPlan.md Week 14 task 14.4 "remaining balance cancel" - writes off what a
     *  PARTIALLY_RECEIVED PO will never receive the rest of. */
    @Transactional
    public PurchaseOrderDetailResponse closeRemainingBalance(final UUID id, final CancelPurchaseOrderRequest request,
                                                              final Authentication authentication) {
        final PurchaseOrder po = load(id);
        assertVersion(po, request.version());
        try {
            po.closeRemainingBalance(request.reason(), Instant.now());
        } catch (final IllegalStateException ex) {
            throw new IdentityException(ApiErrorCode.PURCHASE_ORDER_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    ex.getMessage());
        } catch (final IllegalArgumentException ex) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        repository.flush();
        auditService.record("PURCHASE_ORDER", id.toString(), "PURCHASE_ORDER_CLOSED", actor(authentication),
                java.util.Map.of("reason", request.reason()));
        return toDetail(po);
    }

    /** Business-configurable value threshold - DatabaseDesign.md &sect;17.3, seeded by V021. */
    private BigDecimal approvalThreshold() {
        return systemConfigRepository.findById(THRESHOLD_KEY).map(this::decodeThreshold).orElse(DEFAULT_THRESHOLD);
    }

    private BigDecimal decodeThreshold(final SystemConfigEntry entry) {
        try {
            return objectMapper.readValue(entry.getConfigValueJson(), BigDecimal.class);
        } catch (final Exception ex) {
            return DEFAULT_THRESHOLD;
        }
    }

    private String allocateNumber(final PurchaseOrder po) {
        if (po.getPoNumber() != null) {
            return po.getPoNumber();
        }
        final long next = documentSequenceRepository.nextDailyValue("PO", po.getPoDate(), "PO", 4);
        return documentSequenceRepository.formatDaily("PO", po.getPoDate(), next, 4);
    }

    private void recalculate(final PurchaseOrder po) {
        final BigDecimal subtotal = po.getItems().stream().map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.applyCalculatedTotals(subtotal, subtotal);
    }

    private void requireSupplier(final UUID supplierId) {
        if (supplierId == null || !supplierRepository.existsById(supplierId)) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_NOT_FOUND, HttpStatus.NOT_FOUND, "Supplier was not found");
        }
    }

    private void assertVersion(final PurchaseOrder po, final long expectedVersion) {
        if (po.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Purchase order was modified by another user");
        }
    }

    private PurchaseOrder load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.PURCHASE_ORDER_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Purchase order was not found"));
    }

    private PurchaseOrderDetailResponse toDetail(final PurchaseOrder po) {
        final Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        final List<PurchaseOrderItemResponse> items = po.getItems().stream().map(this::toItemResponse).toList();
        return new PurchaseOrderDetailResponse(po.getId(), po.getPoNumber(), po.getSupplierId(),
                supplier == null ? null : supplier.getName(), po.getPoDate(), po.getExpectedDate(),
                po.getValidUntil(), po.getStatus().name(), po.getSubtotal(), po.getTotalAmount(), po.getApprovedBy(),
                po.getApprovedAt(), po.getNotes(), po.getCreatedAt(), po.getUpdatedAt(), po.getVersion(), items);
    }

    private PurchaseOrderItemResponse toItemResponse(final PurchaseOrderItem item) {
        final Product product = productRepository.findById(item.getProductId()).orElse(null);
        return new PurchaseOrderItemResponse(item.getId(), item.getLineNumber(), item.getProductId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                item.getQuantityOrdered(), item.getUnitPrice(), item.getLineTotal());
    }

    private PurchaseOrderSummaryResponse toSummary(final PurchaseOrder po) {
        final Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return new PurchaseOrderSummaryResponse(po.getId(), po.getPoNumber(), po.getSupplierId(),
                supplier == null ? null : supplier.getName(), po.getPoDate(), po.getStatus().name(),
                po.getTotalAmount(), po.getVersion());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

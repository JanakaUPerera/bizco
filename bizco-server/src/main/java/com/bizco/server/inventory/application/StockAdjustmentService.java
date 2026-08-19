package com.bizco.server.inventory.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentResponse;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentSearchResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductType;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.domain.AdjustmentType;
import com.bizco.server.inventory.domain.StockAdjustment;
import com.bizco.server.inventory.domain.StockAdjustmentStatus;
import com.bizco.server.inventory.infrastructure.StockAdjustmentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING -&gt; APPROVED/REJECTED workflow for manual stock corrections (StateMachines.md
 * &sect;17, DatabaseDesign.md &sect;16, STK-ADJ-001..005). {@code create} never touches
 * {@code stock_movements} - only {@link #decide} does, and only on the APPROVED branch, exactly
 * once (segregation of duties: {@code inventory.adjustment.create} vs
 * {@code inventory.adjustment.approve}, already seeded in V009).
 */
@Service
public class StockAdjustmentService {

    private final StockAdjustmentRepository repository;
    private final ProductRepository productRepository;
    private final StockPostingService stockPostingService;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public StockAdjustmentService(final StockAdjustmentRepository repository, final ProductRepository productRepository,
                                  final StockPostingService stockPostingService,
                                  final IdempotencyService idempotencyService, final UserRepository userRepository,
                                  final AuditService auditService) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.stockPostingService = stockPostingService;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** StateMachines.md &sect;17.3: always lands PENDING with no stock effect. */
    @Transactional
    public StockAdjustmentResponse create(final CreateStockAdjustmentRequest request, final Authentication authentication) {
        final UUID actorId = actor(authentication);
        final AdjustmentType type = validate(request);
        final Product product = productRepository.findById(request.productId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found"));
        if (product.getProductType() != ProductType.INVENTORY) {
            throw new IdentityException(ApiErrorCode.STOCK_ADJUSTMENT_PRODUCT_NOT_INVENTORY, HttpStatus.BAD_REQUEST,
                    "Only inventory products can be stock-adjusted");
        }
        final StockAdjustment adjustment = new StockAdjustment(UUID.randomUUID(), product.getId(), type,
                request.quantity(), request.reason(), actorId, null);
        final StockAdjustment saved = repository.save(adjustment);
        auditService.record("STOCK_ADJUSTMENT", saved.getId().toString(), "STOCK_ADJUSTMENT_CREATED", actorId,
                Map.of("productId", product.getId().toString(), "adjustmentType", type.name()));
        return toResponse(saved, product);
    }

    /** StateMachines.md &sect;17.4. Idempotent (the "stock adjustment approval" critical command in
     *  {@code IdempotencyService}'s Javadoc). */
    @Transactional
    public IdempotentResult<StockAdjustmentResponse> approve(final UUID idempotencyKey, final UUID adjustmentId,
                                                              final DecideStockAdjustmentRequest request,
                                                              final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "stock_adjustment.approve",
                Map.of("adjustmentId", adjustmentId, "request", request), StockAdjustmentResponse.class,
                () -> doDecide(adjustmentId, request, true, actorId));
    }

    /** StateMachines.md &sect;17.5. Idempotent, same shape as {@link #approve} for consistency even
     *  though a reject never posts a movement. */
    @Transactional
    public IdempotentResult<StockAdjustmentResponse> reject(final UUID idempotencyKey, final UUID adjustmentId,
                                                             final DecideStockAdjustmentRequest request,
                                                             final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "stock_adjustment.reject",
                Map.of("adjustmentId", adjustmentId, "request", request), StockAdjustmentResponse.class,
                () -> doDecide(adjustmentId, request, false, actorId));
    }

    private StockAdjustmentResponse doDecide(final UUID adjustmentId, final DecideStockAdjustmentRequest request,
                                             final boolean approve, final UUID actorId) {
        // Row-locked (STK-ADJ-004): a concurrent second decide on the same adjustment blocks here
        // until the first commits, then observes the now-APPROVED/REJECTED status and is rejected.
        final StockAdjustment adjustment = repository.findByIdForUpdate(adjustmentId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.STOCK_ADJUSTMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Stock adjustment was not found"));
        if (adjustment.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Stock adjustment was modified by another user");
        }
        final Product product = productRepository.findById(adjustment.getProductId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found"));
        try {
            if (approve) {
                // Locked before the availability read/write so a concurrent sale/hold/adjustment on
                // the same product serializes behind this decision (StockPostingService Javadoc).
                stockPostingService.lockProduct(adjustment.getProductId());
                if (adjustment.signedQuantity().signum() < 0) {
                    // STK-ADJ-005: a NEGATIVE/DAMAGE adjustment must not take available stock below zero.
                    stockPostingService.requireAvailable(adjustment.getProductId(), adjustment.getQuantity());
                }
                adjustment.approve(actorId, request.decisionReason(), Instant.now());
                stockPostingService.postAdjustment(adjustment.getProductId(), adjustment.getId(),
                        adjustment.signedQuantity(), actorId);
                auditService.record("STOCK_ADJUSTMENT", adjustment.getId().toString(), "STOCK_ADJUSTMENT_APPROVED",
                        actorId, Map.of("productId", product.getId().toString(),
                                "signedQuantity", adjustment.signedQuantity().toPlainString()));
            } else {
                adjustment.reject(actorId, request.decisionReason(), Instant.now());
                auditService.record("STOCK_ADJUSTMENT", adjustment.getId().toString(), "STOCK_ADJUSTMENT_REJECTED",
                        actorId, Map.of("productId", product.getId().toString()));
            }
        } catch (final IllegalStateException exception) {
            throw new IdentityException(ApiErrorCode.STOCK_ADJUSTMENT_ALREADY_DECIDED, HttpStatus.CONFLICT,
                    exception.getMessage());
        }
        repository.flush();
        return toResponse(adjustment, product);
    }

    @Transactional(readOnly = true)
    public StockAdjustmentResponse get(final UUID adjustmentId) {
        final StockAdjustment adjustment = repository.findById(adjustmentId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.STOCK_ADJUSTMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Stock adjustment was not found"));
        return toResponse(adjustment, productRepository.findById(adjustment.getProductId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public StockAdjustmentSearchResponse search(final UUID productId, final String status, final int page,
                                                final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final StockAdjustmentStatus statusFilter = status == null || status.isBlank() ? null
                : StockAdjustmentStatus.valueOf(status.trim().toUpperCase());
        final Page<StockAdjustment> result = repository.search(productId, statusFilter, pageable);
        final List<StockAdjustmentResponse> data = result.getContent().stream()
                .map(adjustment -> toResponse(adjustment, productRepository.findById(adjustment.getProductId()).orElse(null)))
                .toList();
        return new StockAdjustmentSearchResponse(data, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    private AdjustmentType validate(final CreateStockAdjustmentRequest request) {
        final List<FieldError> errors = new ArrayList<>();
        if (request.productId() == null) {
            errors.add(new FieldError("productId", "REQUIRED", "Product is required."));
        }
        AdjustmentType type = null;
        try {
            type = request.adjustmentType() == null ? null : AdjustmentType.valueOf(request.adjustmentType().trim().toUpperCase());
        } catch (final IllegalArgumentException exception) {
            errors.add(new FieldError("adjustmentType", "INVALID", "Adjustment type must be POSITIVE, NEGATIVE or DAMAGE."));
        }
        if (type == null && request.adjustmentType() == null) {
            errors.add(new FieldError("adjustmentType", "REQUIRED", "Adjustment type is required."));
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new FieldError("quantity", "INVALID", "Quantity must be greater than zero."));
        }
        if (request.reason() == null || request.reason().isBlank()) {
            errors.add(new FieldError("reason", "REQUIRED", "Reason is required."));
        }
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
        return type;
    }

    private StockAdjustmentResponse toResponse(final StockAdjustment adjustment, final Product product) {
        return new StockAdjustmentResponse(adjustment.getId(), adjustment.getProductId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                adjustment.getAdjustmentType().name(), adjustment.getQuantity(), adjustment.getReason(),
                adjustment.getStatus().name(), adjustment.getCreatedBy(), adjustment.getCreatedAt(),
                adjustment.getDecidedBy(), adjustment.getDecidedAt(), adjustment.getDecisionReason(),
                adjustment.getReversesAdjustmentId(), adjustment.getVersion());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

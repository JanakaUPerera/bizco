package com.bizco.server.manufacturing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderItemResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSearchResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSummaryResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.manufacturing.domain.BillOfMaterials;
import com.bizco.server.manufacturing.domain.BomItem;
import com.bizco.server.manufacturing.domain.ProductionMode;
import com.bizco.server.manufacturing.domain.ProductionOrder;
import com.bizco.server.manufacturing.domain.ProductionOrderItem;
import com.bizco.server.manufacturing.infrastructure.BillOfMaterialsRepository;
import com.bizco.server.manufacturing.infrastructure.ProductionOrderRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Produce transaction (DevelopmentPlan.md Week 21, SRS.md &sect;6.4.11.2): locks every
 *  distinct component variant, validates availability for every component BEFORE posting any
 *  movement, then posts PRODUCTION_OUT per component and (for a {@code STOCKED} production)
 *  PRODUCTION_IN for the finished variant, all in one transaction. Idempotent, like every other
 *  posting command in this codebase ({@code GoodsReceiptService.post}). */
@Service
public class ProductionService {

    private final BillOfMaterialsRepository bomRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final ProductVariantRepository variantRepository;
    private final StockPostingService stockPostingService;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProductionService(final BillOfMaterialsRepository bomRepository,
                             final ProductionOrderRepository productionOrderRepository,
                             final ProductVariantRepository variantRepository,
                             final StockPostingService stockPostingService,
                             final DocumentSequenceRepository documentSequenceRepository,
                             final IdempotencyService idempotencyService, final UserRepository userRepository,
                             final AuditService auditService) {
        this.bomRepository = bomRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.variantRepository = variantRepository;
        this.stockPostingService = stockPostingService;
        this.documentSequenceRepository = documentSequenceRepository;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public IdempotentResult<ProductionOrderResponse> produce(final UUID idempotencyKey, final ProduceRequest request,
                                                              final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "production-order.produce",
                Map.of("request", request),
                ProductionOrderResponse.class, () -> doProduce(request, actorId));
    }

    private ProductionOrderResponse doProduce(final ProduceRequest request, final UUID actorId) {
        final List<FieldError> errors = new ArrayList<>();
        if (request.bomId() == null) {
            errors.add(new FieldError("bomId", "REQUIRED", "Bill of Materials is required."));
        }
        if (request.quantityToProduce() == null || request.quantityToProduce().signum() <= 0) {
            errors.add(new FieldError("quantityToProduce", "INVALID", "Quantity to produce must be greater than zero."));
        }
        final ProductionMode mode = parseMode(request.productionMode(), errors);
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }

        final BillOfMaterials bom = bomRepository.findById(request.bomId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.BOM_NOT_FOUND, HttpStatus.NOT_FOUND, "Bill of Materials was not found"));
        if (!bom.isActive()) {
            throw new IdentityException(ApiErrorCode.BOM_NOT_ACTIVE, HttpStatus.CONFLICT,
                    "This Bill of Materials is not active.");
        }
        if (bom.getItems().isEmpty()) {
            throw new IdentityException(ApiErrorCode.BOM_HAS_NO_COMPONENTS, HttpStatus.CONFLICT,
                    "This Bill of Materials has no components to consume.");
        }

        // Lock every distinct component AND the finished variant in one call, so the whole
        // touched-variant set for this transaction shares a single true ascending-order lock
        // acquisition (STK-CON-003 deadlock avoidance) - mirrors GoodsReceiptService.doPost's
        // lockVariants call. A separate, subsequent lockVariant(finishedVariantId) call would NOT
        // be covered by that guarantee: nothing in the schema stops a finished variant of one BOM
        // from also being a component of another (multi-level/sub-assembly manufacturing), so two
        // concurrent produce() calls whose BOMs cross that way could each lock their own component
        // first and then block on the other's finished-variant lock in reversed order - a classic
        // deadlock the whole lockVariants ordering discipline exists to rule out.
        final List<UUID> componentIds = bom.getItems().stream().map(BomItem::getComponentVariantId).distinct().toList();
        final Set<UUID> touchedVariantIds = new LinkedHashSet<>(componentIds);
        touchedVariantIds.add(bom.getFinishedVariantId());
        final Map<UUID, ProductVariant> lockedVariants = stockPostingService.lockVariants(touchedVariantIds);

        // SRS.md §6.4.11.2 step 3/4: validate EVERY component before posting ANY movement, so an
        // insufficient-stock component never leaves an earlier component partially consumed.
        for (final BomItem item : bom.getItems()) {
            final BigDecimal required = requiredQuantity(item, request.quantityToProduce());
            stockPostingService.requireAvailable(item.getComponentVariantId(), required);
        }

        final long sequenceValue = documentSequenceRepository.nextDailyValue("PRODUCTION_ORDER", LocalDate.now(), "MO", 4);
        final String productionNumber = documentSequenceRepository.formatDaily("MO", LocalDate.now(), sequenceValue, 4);

        final ProductionOrder order = new ProductionOrder(productionNumber, bom.getId(), bom.getFinishedVariantId(),
                request.quantityToProduce(), mode, request.notes(), actorId);
        BigDecimal totalComponentCost = BigDecimal.ZERO;
        for (final BomItem item : bom.getItems()) {
            final BigDecimal required = requiredQuantity(item, request.quantityToProduce());
            final BigDecimal unitCost = lockedVariants.get(item.getComponentVariantId()).getCostPrice();
            final BigDecimal lineCost = unitCost.multiply(required);
            order.addItem(new ProductionOrderItem(item.getComponentVariantId(), required, unitCost, lineCost));
            totalComponentCost = totalComponentCost.add(lineCost);
        }
        order.applyTotalComponentCost(totalComponentCost);
        final ProductionOrder saved = productionOrderRepository.saveAndFlush(order);

        for (final ProductionOrderItem item : saved.getItems()) {
            stockPostingService.postProductionOut(item.getComponentVariantId(), saved.getId(), item.getId(),
                    item.getQuantityConsumed(), actorId);
        }
        // Task 21.3: a MADE_TO_ORDER production never posts PRODUCTION_IN - the BOM still records
        // what was consumed and costed, but nothing sits in finished-goods stock beforehand
        // (SRS.md §6.4.11.3).
        if (saved.getProductionMode() == ProductionMode.STOCKED) {
            stockPostingService.postProductionIn(saved.getFinishedVariantId(), saved.getId(),
                    request.quantityToProduce(), actorId);
        }

        auditService.record("PRODUCTION_ORDER", saved.getId().toString(), "PRODUCTION_ORDER_POSTED", actorId,
                Map.of("productionNumber", productionNumber, "bomId", bom.getId().toString()));
        return toResponse(saved, bom);
    }

    @Transactional(readOnly = true)
    public ProductionOrderResponse get(final UUID productionOrderId) {
        final ProductionOrder order = productionOrderRepository.findById(productionOrderId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.PRODUCTION_ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Production order was not found"));
        final BillOfMaterials bom = bomRepository.findById(order.getBomId()).orElse(null);
        return toResponse(order, bom);
    }

    @Transactional(readOnly = true)
    public ProductionOrderSearchResponse search(final UUID bomId, final UUID finishedVariantId, final int page,
                                                final int size) {
        final Page<ProductionOrder> result = productionOrderRepository.search(bomId, finishedVariantId,
                pageable(page, size));
        final List<ProductionOrderSummaryResponse> data = result.getContent().stream().map(order -> {
            final ProductVariant finished = variantRepository.findById(order.getFinishedVariantId()).orElse(null);
            return new ProductionOrderSummaryResponse(order.getId(), order.getProductionNumber(),
                    order.getFinishedVariantId(), finished == null ? null : finished.getSku(),
                    finished == null ? null : displayName(finished), order.getQuantityProduced(),
                    order.getProductionMode().name(), order.getTotalComponentCost(), order.getCreatedAt());
        }).toList();
        return new ProductionOrderSearchResponse(data, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    /** Physical consumption includes wastage (both the recipe quantity and the offcut are
     *  physically taken off the shelf); the *estimated* cost shown on the BOM does not
     *  (BillOfMaterialsService's own Javadoc) - this is exactly why SRS.md §6.4.11.4 says actual
     *  production cost may differ from the estimate. */
    private BigDecimal requiredQuantity(final BomItem item, final BigDecimal quantityToProduce) {
        return item.getQuantity().add(item.getWastageQty()).multiply(quantityToProduce);
    }

    private ProductionMode parseMode(final String productionMode, final List<FieldError> errors) {
        if (productionMode == null || productionMode.isBlank()) {
            errors.add(new FieldError("productionMode", "REQUIRED", "Production mode is required."));
            return null;
        }
        try {
            return ProductionMode.valueOf(productionMode.trim().toUpperCase());
        } catch (final IllegalArgumentException ex) {
            errors.add(new FieldError("productionMode", "INVALID", "Production mode must be STOCKED or MADE_TO_ORDER."));
            return null;
        }
    }

    private ProductionOrderResponse toResponse(final ProductionOrder order, final BillOfMaterials bom) {
        final ProductVariant finished = variantRepository.findById(order.getFinishedVariantId()).orElse(null);
        final List<ProductionOrderItemResponse> items = order.getItems().stream().map(item -> {
            final ProductVariant component = variantRepository.findById(item.getComponentVariantId()).orElse(null);
            return new ProductionOrderItemResponse(item.getId(), item.getComponentVariantId(),
                    component == null ? null : component.getSku(), component == null ? null : displayName(component),
                    item.getQuantityConsumed(), item.getUnitCostAtProduction(), item.getTotalCost());
        }).toList();
        return new ProductionOrderResponse(order.getId(), order.getProductionNumber(), order.getBomId(),
                bom == null ? null : bom.getName(), order.getFinishedVariantId(), finished == null ? null : finished.getSku(),
                finished == null ? null : displayName(finished), order.getQuantityProduced(),
                order.getProductionMode().name(), order.getTotalComponentCost(), order.getNotes(), order.getCreatedBy(),
                order.getCreatedAt(), items);
    }

    private String displayName(final ProductVariant variant) {
        final String productName = variant.getProduct().getName();
        return variant.isDefault() || variant.getVariantLabel() == null ? productName
                : productName + " - " + variant.getVariantLabel();
    }

    private Pageable pageable(final int page, final int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(u -> u.getId()).orElse(null);
    }
}

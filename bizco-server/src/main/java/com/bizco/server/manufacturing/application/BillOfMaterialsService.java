package com.bizco.server.manufacturing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSearchResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSummaryResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.UpdateBomRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.manufacturing.domain.BillOfMaterials;
import com.bizco.server.manufacturing.domain.BomItem;
import com.bizco.server.manufacturing.infrastructure.BillOfMaterialsRepository;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bill of Materials CRUD, cost roll-up, and circular-reference guard (DevelopmentPlan.md Week 20,
 *  DatabaseDesign.md &sect;57, SRS.md &sect;6.4.11). */
@Service
public class BillOfMaterialsService {

    private final BillOfMaterialsRepository bomRepository;
    private final ProductVariantRepository variantRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public BillOfMaterialsService(final BillOfMaterialsRepository bomRepository,
                                  final ProductVariantRepository variantRepository, final AuditService auditService,
                                  final UserRepository userRepository) {
        this.bomRepository = bomRepository;
        this.variantRepository = variantRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional
    public BomDetailResponse create(final CreateBomRequest request, final Authentication authentication) {
        final List<FieldError> errors = new ArrayList<>();
        if (request.finishedVariantId() == null) {
            errors.add(new FieldError("finishedVariantId", "REQUIRED", "Finished variant is required."));
        }
        if (request.name() == null || request.name().isBlank()) {
            errors.add(new FieldError("name", "REQUIRED", "Name is required."));
        }
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
        final ProductVariant finished = variant(request.finishedVariantId());
        try {
            final BillOfMaterials saved = bomRepository.saveAndFlush(
                    new BillOfMaterials(finished.getId(), request.name()));
            auditService.record("BILL_OF_MATERIALS", saved.getId().toString(), "BOM_CREATED", actor(authentication),
                    Map.of("finishedVariantId", finished.getId().toString()));
            return toDetail(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.BOM_ALREADY_EXISTS_FOR_VARIANT, HttpStatus.CONFLICT,
                    "This variant already has a Bill of Materials.");
        }
    }

    @Transactional
    public BomDetailResponse update(final UUID bomId, final UpdateBomRequest request,
                                    final Authentication authentication) {
        final BillOfMaterials bom = load(bomId);
        assertVersion(bom.getVersion(), request.version());
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiValidationException(List.of(new FieldError("name", "REQUIRED", "Name is required.")));
        }
        bom.rename(request.name(), request.active());
        bomRepository.flush();
        auditService.record("BILL_OF_MATERIALS", bomId.toString(), "BOM_UPDATED", actor(authentication),
                Map.of("active", String.valueOf(request.active())));
        return toDetail(bom);
    }

    /** Task 20.4: rejects a component that is, directly or transitively, itself built from the
     *  BOM's own finished variant, before adding the line. */
    @Transactional
    public BomDetailResponse addItem(final UUID bomId, final BomItemRequest request,
                                     final Authentication authentication) {
        final BillOfMaterials bom = load(bomId);
        if (request.componentVariantId() == null) {
            throw new ApiValidationException(
                    List.of(new FieldError("componentVariantId", "REQUIRED", "Component is required.")));
        }
        final ProductVariant component = variant(request.componentVariantId());
        assertNoCycle(bom.getFinishedVariantId(), component.getId());
        try {
            bom.addItem(new BomItem(component.getId(), request.quantity(), request.wastageQty()));
            bomRepository.flush();
        } catch (final IllegalArgumentException ex) {
            throw new ApiValidationException(List.of(new FieldError("quantity", "INVALID", ex.getMessage())));
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.BOM_ITEM_DUPLICATE_COMPONENT, HttpStatus.CONFLICT,
                    "This component is already on the Bill of Materials.");
        }
        auditService.record("BILL_OF_MATERIALS", bomId.toString(), "BOM_ITEM_ADDED", actor(authentication),
                Map.of("componentVariantId", component.getId().toString()));
        return toDetail(bom);
    }

    @Transactional
    public BomDetailResponse updateItem(final UUID bomId, final UUID bomItemId, final BomItemRequest request,
                                        final Authentication authentication) {
        final BillOfMaterials bom = load(bomId);
        final BomItem item = bom.getItems().stream().filter(i -> i.getId().equals(bomItemId)).findFirst()
                .orElseThrow(() -> new IdentityException(ApiErrorCode.BOM_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "BOM item was not found"));
        try {
            item.update(request.quantity(), request.wastageQty());
        } catch (final IllegalArgumentException ex) {
            throw new ApiValidationException(List.of(new FieldError("quantity", "INVALID", ex.getMessage())));
        }
        bomRepository.flush();
        auditService.record("BILL_OF_MATERIALS", bomId.toString(), "BOM_ITEM_UPDATED", actor(authentication),
                Map.of("bomItemId", bomItemId.toString()));
        return toDetail(bom);
    }

    @Transactional
    public BomDetailResponse removeItem(final UUID bomId, final UUID bomItemId, final Authentication authentication) {
        final BillOfMaterials bom = load(bomId);
        try {
            bom.removeItem(bomItemId);
        } catch (final IllegalArgumentException ex) {
            throw new IdentityException(ApiErrorCode.BOM_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND, ex.getMessage());
        }
        bomRepository.flush();
        auditService.record("BILL_OF_MATERIALS", bomId.toString(), "BOM_ITEM_REMOVED", actor(authentication),
                Map.of("bomItemId", bomItemId.toString()));
        return toDetail(bom);
    }

    @Transactional(readOnly = true)
    public BomDetailResponse get(final UUID bomId) {
        return toDetail(load(bomId));
    }

    @Transactional(readOnly = true)
    public BomDetailResponse byFinishedVariant(final UUID finishedVariantId) {
        return toDetail(bomRepository.findByFinishedVariantId(finishedVariantId).orElseThrow(
                () -> new IdentityException(ApiErrorCode.BOM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "This variant has no Bill of Materials.")));
    }

    @Transactional(readOnly = true)
    public BomSearchResponse search(final boolean activeOnly, final int page, final int size) {
        final Page<BillOfMaterials> result = bomRepository.search(activeOnly, pageable(page, size));
        return new BomSearchResponse(result.getContent().stream().map(this::toSummary).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** SRS.md &sect;6.4.11.1: walks {@code candidateComponentId}'s own BOM tree (and its
     *  components' BOMs, and so on) looking for {@code finishedVariantId}. A variant with no BOM
     *  of its own is a dead end - no cycle is possible through it, so the walk simply stops there. */
    private void assertNoCycle(final UUID finishedVariantId, final UUID candidateComponentId) {
        if (finishedVariantId.equals(candidateComponentId)) {
            throw new IdentityException(ApiErrorCode.BOM_ITEM_CIRCULAR_REFERENCE, HttpStatus.CONFLICT,
                    "A component cannot be the same variant as the finished product it belongs to.");
        }
        final Set<UUID> visited = new HashSet<>();
        final Deque<UUID> toVisit = new ArrayDeque<>();
        toVisit.add(candidateComponentId);
        while (!toVisit.isEmpty()) {
            final UUID current = toVisit.poll();
            if (!visited.add(current)) {
                continue;
            }
            final Optional<BillOfMaterials> currentBom = bomRepository.findByFinishedVariantId(current);
            if (currentBom.isEmpty()) {
                continue;
            }
            for (final BomItem item : currentBom.get().getItems()) {
                final UUID next = item.getComponentVariantId();
                if (next.equals(finishedVariantId)) {
                    throw new IdentityException(ApiErrorCode.BOM_ITEM_CIRCULAR_REFERENCE, HttpStatus.CONFLICT,
                            "This component is, directly or transitively, built from the finished product itself.");
                }
                toVisit.add(next);
            }
        }
    }

    private BillOfMaterials load(final UUID bomId) {
        return bomRepository.findById(bomId).orElseThrow(() -> new IdentityException(ApiErrorCode.BOM_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Bill of Materials was not found"));
    }

    private ProductVariant variant(final UUID id) {
        return variantRepository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Product variant was not found"));
    }

    private void assertVersion(final long current, final long expected) {
        if (current != expected) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Bill of Materials was modified by another user");
        }
    }

    private Pageable pageable(final int page, final int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    /** Task 20.3 cost roll-up: every read recomputes each item's estimated cost from the
     *  component's *current* cost price, so the figure shown is always "refreshed" (SRS.md
     *  &sect;6.4.11.4) without a cross-module hook reacting to purchasing cost-price changes
     *  elsewhere. */
    private BomDetailResponse toDetail(final BillOfMaterials bom) {
        final ProductVariant finished = variantRepository.findById(bom.getFinishedVariantId()).orElse(null);
        final List<BomItemResponse> items = new ArrayList<>();
        BigDecimal totalEstimatedCost = BigDecimal.ZERO;
        for (final BomItem item : bom.getItems()) {
            final ProductVariant component = variantRepository.findById(item.getComponentVariantId()).orElse(null);
            final BigDecimal componentCost = component == null ? BigDecimal.ZERO : component.getCostPrice();
            final BigDecimal estimatedCost = componentCost.multiply(item.getQuantity());
            item.applyEstimatedCost(estimatedCost);
            totalEstimatedCost = totalEstimatedCost.add(estimatedCost);
            items.add(new BomItemResponse(item.getId(), item.getComponentVariantId(),
                    component == null ? null : component.getSku(), component == null ? null : displayName(component),
                    item.getQuantity(), item.getWastageQty(), componentCost, estimatedCost));
        }
        return new BomDetailResponse(bom.getId(), bom.getFinishedVariantId(), finished == null ? null : finished.getSku(),
                finished == null ? null : displayName(finished), bom.getName(), bom.isActive(), totalEstimatedCost,
                bom.getCreatedAt(), bom.getUpdatedAt(), bom.getVersion(), items);
    }

    private BomSummaryResponse toSummary(final BillOfMaterials bom) {
        final ProductVariant finished = variantRepository.findById(bom.getFinishedVariantId()).orElse(null);
        BigDecimal totalEstimatedCost = BigDecimal.ZERO;
        for (final BomItem item : bom.getItems()) {
            final ProductVariant component = variantRepository.findById(item.getComponentVariantId()).orElse(null);
            final BigDecimal componentCost = component == null ? BigDecimal.ZERO : component.getCostPrice();
            totalEstimatedCost = totalEstimatedCost.add(componentCost.multiply(item.getQuantity()));
        }
        return new BomSummaryResponse(bom.getId(), bom.getFinishedVariantId(), finished == null ? null : finished.getSku(),
                finished == null ? null : displayName(finished), bom.getName(), bom.isActive(), totalEstimatedCost,
                bom.getVersion());
    }

    private String displayName(final ProductVariant variant) {
        final String productName = variant.getProduct().getName();
        return variant.isDefault() || variant.getVariantLabel() == null ? productName
                : productName + " - " + variant.getVariantLabel();
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(u -> u.getId()).orElse(null);
    }
}

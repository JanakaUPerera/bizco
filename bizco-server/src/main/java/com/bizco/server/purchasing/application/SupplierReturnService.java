package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnItemResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnSearchResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.purchasing.domain.GoodsReceipt;
import com.bizco.server.purchasing.domain.GoodsReceiptItem;
import com.bizco.server.purchasing.domain.GoodsReceiptStatus;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierReturn;
import com.bizco.server.purchasing.domain.SupplierReturnItem;
import com.bizco.server.purchasing.infrastructure.GoodsReceiptRepository;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import com.bizco.server.purchasing.infrastructure.SupplierReturnRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Returns goods to a supplier out of one POSTED {@link GoodsReceipt} (DatabaseDesign.md
 * &sect;17.8/17.9, DevelopmentPlan.md Week 15 tasks 15.1-15.2). One-shot, like
 * {@code CreditNoteService}: created and settled - stock deducted - in a single idempotency-wrapped
 * request (PUR-RET-001).
 *
 * <p>Locks the goods receipt for the duration (PUR-RET-CON-001, same {@code findByIdForUpdate}
 * pattern {@code GoodsReceiptService.updatePurchaseOrderProgress} uses) so two concurrent returns
 * against the same receipt's lines serialize - the cumulative-return-cannot-exceed-received check
 * (PUR-RET-002) would otherwise race.
 */
@Service
public class SupplierReturnService {

    private final SupplierReturnRepository repository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockPostingService stockPostingService;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SupplierReturnService(final SupplierReturnRepository repository,
                                 final GoodsReceiptRepository goodsReceiptRepository,
                                 final SupplierRepository supplierRepository, final ProductRepository productRepository,
                                 final StockPostingService stockPostingService,
                                 final DocumentSequenceRepository documentSequenceRepository,
                                 final IdempotencyService idempotencyService, final UserRepository userRepository,
                                 final AuditService auditService) {
        this.repository = repository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.stockPostingService = stockPostingService;
        this.documentSequenceRepository = documentSequenceRepository;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public IdempotentResult<SupplierReturnResponse> create(final UUID idempotencyKey,
                                                            final CreateSupplierReturnRequest request,
                                                            final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "supplier_return.create", request, SupplierReturnResponse.class,
                () -> doCreate(idempotencyKey, request, actorId));
    }

    private SupplierReturnResponse doCreate(final UUID idempotencyKey, final CreateSupplierReturnRequest request,
                                            final UUID actorId) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw domainRejected("A return reason is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw domainRejected("A supplier return must have at least one item");
        }
        // Row-locked for PUR-RET-CON-001 - see class Javadoc.
        final GoodsReceipt goodsReceipt = goodsReceiptRepository.findByIdForUpdate(request.goodsReceiptId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Goods receipt was not found"));
        if (goodsReceipt.getStatus() != GoodsReceiptStatus.POSTED) {
            throw new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_POSTED, HttpStatus.CONFLICT,
                    "Only a POSTED goods receipt can be returned against");
        }
        if (!goodsReceipt.getSupplierId().equals(request.supplierId())) {
            throw domainRejected("Goods receipt does not belong to this supplier");
        }
        final Map<UUID, BigDecimal> alreadyReturned = new HashMap<>();
        for (final SupplierReturn prior : repository.findByGoodsReceiptId(goodsReceipt.getId())) {
            for (final SupplierReturnItem item : prior.getItems()) {
                alreadyReturned.merge(item.getGoodsReceiptItemId(), item.getQuantityReturned(), BigDecimal::add);
            }
        }

        final long next = documentSequenceRepository.nextDailyValue("SRT", LocalDate.now(), "SRT", 4);
        final String returnNumber = documentSequenceRepository.formatDaily("SRT", LocalDate.now(), next, 4);
        final SupplierReturn supplierReturn = new SupplierReturn(idempotencyKey, returnNumber, request.supplierId(),
                goodsReceipt.getId(), request.reason(), actorId);

        record Deduction(UUID productId, BigDecimal quantity, SupplierReturnItem item) {
        }
        final List<Deduction> deductions = new ArrayList<>();
        for (final CreateSupplierReturnItemRequest itemRequest : request.items()) {
            if (itemRequest.goodsReceiptItemId() == null) {
                throw domainRejected("goodsReceiptItemId is required for every return line");
            }
            final BigDecimal quantityReturned = itemRequest.quantityReturned();
            if (quantityReturned == null || quantityReturned.signum() <= 0) {
                throw domainRejected("quantityReturned must be greater than zero");
            }
            final GoodsReceiptItem grItem = goodsReceipt.getItems().stream()
                    .filter(candidate -> candidate.getId().equals(itemRequest.goodsReceiptItemId())).findFirst()
                    .orElseThrow(() -> new IdentityException(ApiErrorCode.GOODS_RECEIPT_ITEM_NOT_FOUND,
                            HttpStatus.NOT_FOUND, "Goods receipt item was not found on this receipt"));
            final BigDecimal priorQuantity = alreadyReturned.getOrDefault(grItem.getId(), BigDecimal.ZERO);
            if (priorQuantity.add(quantityReturned).compareTo(grItem.usableQuantity()) > 0) {
                throw new IdentityException(ApiErrorCode.RETURN_QUANTITY_EXCEEDED, HttpStatus.CONFLICT,
                        "Requested return quantity exceeds the remaining received quantity for this line");
            }
            final SupplierReturnItem item = new SupplierReturnItem(grItem.getId(), grItem.getProductId(),
                    quantityReturned, grItem.getUnitCost());
            supplierReturn.addItem(item);
            deductions.add(new Deduction(grItem.getProductId(), quantityReturned, item));
        }

        final SupplierReturn saved = repository.save(supplierReturn);

        stockPostingService.lockProducts(deductions.stream().map(Deduction::productId).distinct().toList());
        for (final Deduction deduction : deductions) {
            stockPostingService.postSupplierReturn(deduction.productId(), saved.getId(), deduction.item().getId(),
                    deduction.quantity(), actorId);
        }

        auditService.record("SUPPLIER_RETURN", saved.getId().toString(), "SUPPLIER_RETURN_CREATED", actorId,
                Map.of("returnNumber", returnNumber, "goodsReceiptId", goodsReceipt.getId().toString(),
                        "totalAmount", saved.getTotalAmount().toPlainString()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierReturnResponse get(final UUID id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public SupplierReturnSearchResponse search(final UUID supplierId, final UUID goodsReceiptId, final int page,
                                               final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<SupplierReturn> result = repository.search(supplierId, goodsReceiptId, pageable);
        return new SupplierReturnSearchResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private SupplierReturn load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.SUPPLIER_RETURN_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Supplier return was not found"));
    }

    private SupplierReturnResponse toResponse(final SupplierReturn supplierReturn) {
        final Supplier supplier = supplierRepository.findById(supplierReturn.getSupplierId()).orElse(null);
        final GoodsReceipt goodsReceipt = goodsReceiptRepository.findById(supplierReturn.getGoodsReceiptId()).orElse(null);
        final List<SupplierReturnItemResponse> items = supplierReturn.getItems().stream()
                .map(this::toItemResponse).toList();
        return new SupplierReturnResponse(supplierReturn.getId(), supplierReturn.getReturnNumber(),
                supplierReturn.getSupplierId(), supplier == null ? null : supplier.getName(),
                supplierReturn.getGoodsReceiptId(), goodsReceipt == null ? null : goodsReceipt.getReceiptNumber(),
                supplierReturn.getTotalAmount(), supplierReturn.getReason(), supplierReturn.getCreatedBy(),
                supplierReturn.getCreatedAt(), items);
    }

    private SupplierReturnItemResponse toItemResponse(final SupplierReturnItem item) {
        final Product product = productRepository.findById(item.getProductId()).orElse(null);
        return new SupplierReturnItemResponse(item.getId(), item.getGoodsReceiptItemId(), item.getProductId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                item.getQuantityReturned(), item.getUnitCost(), item.getLineTotal());
    }

    private IdentityException domainRejected(final String message) {
        return new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST, message);
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentAllocationRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentAllocationResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentSearchResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.finance.domain.CashDirection;
import com.bizco.server.finance.domain.CashSourceType;
import com.bizco.server.finance.domain.CashbookEntry;
import com.bizco.server.finance.domain.PaymentMethod;
import com.bizco.server.finance.infrastructure.CashbookEntryRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.domain.GoodsReceipt;
import com.bizco.server.purchasing.domain.GoodsReceiptStatus;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierPayment;
import com.bizco.server.purchasing.domain.SupplierPaymentAllocation;
import com.bizco.server.purchasing.infrastructure.GoodsReceiptOutstandingRepository;
import com.bizco.server.purchasing.infrastructure.GoodsReceiptRepository;
import com.bizco.server.purchasing.infrastructure.SupplierPaymentRepository;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a payment to a supplier, optionally allocated across one or more of that supplier's
 * outstanding goods receipts (DatabaseDesign.md &sect;17.10/17.11, DevelopmentPlan.md Week 15 tasks
 * 15.3-15.4). One-shot, like {@code PaymentAllocationService} on the sales side, and wires a matching
 * {@code CashbookEntry} (CashDirection.OUT / CashSourceType.SUPPLIER_PAYMENT) in the same
 * transaction (FIN-AP-001..003).
 *
 * <p>Locks every goods receipt named in the request, in ascending id order (PUR-PAY-CON-001, the
 * same stable-ordering rule {@code StockPostingService.lockProducts} documents for STK-CON-003), so
 * two concurrent payments that both target some of the same receipts serialize rather than
 * deadlock, and the "allocated amount &lt;= outstanding" check below can't race.
 */
@Service
public class SupplierPaymentService {

    private final SupplierPaymentRepository repository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptOutstandingRepository outstandingRepository;
    private final SupplierRepository supplierRepository;
    private final CashbookEntryRepository cashbookEntryRepository;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SupplierPaymentService(final SupplierPaymentRepository repository,
                                  final GoodsReceiptRepository goodsReceiptRepository,
                                  final GoodsReceiptOutstandingRepository outstandingRepository,
                                  final SupplierRepository supplierRepository,
                                  final CashbookEntryRepository cashbookEntryRepository,
                                  final IdempotencyService idempotencyService, final UserRepository userRepository,
                                  final AuditService auditService) {
        this.repository = repository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.outstandingRepository = outstandingRepository;
        this.supplierRepository = supplierRepository;
        this.cashbookEntryRepository = cashbookEntryRepository;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public IdempotentResult<SupplierPaymentResponse> record(final UUID idempotencyKey,
                                                             final RecordSupplierPaymentRequest request,
                                                             final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "supplier_payment.record", request,
                SupplierPaymentResponse.class, () -> doRecord(idempotencyKey, request, actorId));
    }

    private SupplierPaymentResponse doRecord(final UUID idempotencyKey, final RecordSupplierPaymentRequest request,
                                             final UUID actorId) {
        if (request.supplierId() == null || !supplierRepository.existsById(request.supplierId())) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Supplier was not found");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw domainRejected("amount must be greater than zero");
        }
        final PaymentMethod method = paymentMethod(request.paymentMethod());
        final List<SupplierPaymentAllocationRequest> allocationRequests = request.allocations() == null
                ? List.of() : request.allocations();

        final Map<UUID, GoodsReceipt> locked = lockGoodsReceipts(allocationRequests);
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (final SupplierPaymentAllocationRequest allocation : allocationRequests) {
            if (allocation.amount() == null || allocation.amount().signum() <= 0) {
                throw domainRejected("Each allocation needs a positive amount");
            }
            final GoodsReceipt goodsReceipt = locked.get(allocation.goodsReceiptId());
            if (goodsReceipt.getStatus() != GoodsReceiptStatus.POSTED) {
                throw new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_POSTED, HttpStatus.CONFLICT,
                        "Only a POSTED goods receipt can receive a payment allocation");
            }
            if (!goodsReceipt.getSupplierId().equals(request.supplierId())) {
                throw domainRejected("Goods receipt does not belong to this supplier");
            }
            final BigDecimal outstanding = outstandingRepository.findById(goodsReceipt.getId())
                    .map(GoodsReceiptOutstandingRepository.Row::outstandingAmount).orElse(BigDecimal.ZERO);
            if (allocation.amount().compareTo(outstanding) > 0) {
                throw new IdentityException(ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE, HttpStatus.CONFLICT,
                        "Allocation exceeds goods receipt " + goodsReceipt.getReceiptNumber() + "'s outstanding balance");
            }
            allocatedTotal = allocatedTotal.add(allocation.amount());
        }
        if (allocatedTotal.compareTo(request.amount()) > 0) {
            throw domainRejected("Allocations cannot exceed the payment amount");
        }

        final Instant paymentDate = request.paymentDate() == null ? Instant.now() : request.paymentDate();
        final SupplierPayment payment = new SupplierPayment(idempotencyKey, request.supplierId(), paymentDate, method,
                request.amount(), request.referenceNumber(), actorId, request.notes());
        allocationRequests.forEach(allocation -> payment.allocateTo(allocation.goodsReceiptId(), allocation.amount()));
        final SupplierPayment saved = repository.save(payment);

        cashbookEntryRepository.save(new CashbookEntry(idempotencyKey, paymentDate, CashDirection.OUT,
                CashSourceType.SUPPLIER_PAYMENT, saved.getAmount(), method, null, saved.getId(), null, actorId));

        auditService.record("SUPPLIER_PAYMENT", saved.getId().toString(), "SUPPLIER_PAYMENT_RECORDED", actorId,
                Map.of("supplierId", request.supplierId().toString(), "amount", saved.getAmount().toPlainString(),
                        "allocationCount", String.valueOf(allocationRequests.size())));
        return toResponse(saved);
    }

    /** Locks every distinct goods receipt named in the request, in ascending id order - see class
     *  Javadoc for the deadlock-avoidance rationale. */
    private Map<UUID, GoodsReceipt> lockGoodsReceipts(final List<SupplierPaymentAllocationRequest> allocations) {
        final TreeSet<UUID> distinctIds = new TreeSet<>();
        for (final SupplierPaymentAllocationRequest allocation : allocations) {
            if (allocation.goodsReceiptId() == null) {
                throw domainRejected("Each allocation needs a goodsReceiptId");
            }
            if (!distinctIds.add(allocation.goodsReceiptId())) {
                throw domainRejected("Each goods receipt can only be allocated once per payment");
            }
        }
        final Map<UUID, GoodsReceipt> locked = new LinkedHashMap<>();
        for (final UUID id : distinctIds) {
            final GoodsReceipt goodsReceipt = goodsReceiptRepository.findByIdForUpdate(id).orElseThrow(
                    () -> new IdentityException(ApiErrorCode.GOODS_RECEIPT_NOT_FOUND, HttpStatus.NOT_FOUND,
                            "Goods receipt was not found: " + id));
            locked.put(id, goodsReceipt);
        }
        return locked;
    }

    @Transactional(readOnly = true)
    public SupplierPaymentResponse get(final UUID id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public SupplierPaymentSearchResponse search(final UUID supplierId, final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<SupplierPayment> result = repository.search(supplierId, pageable);
        return new SupplierPaymentSearchResponse(result.getContent().stream().map(this::toResponse).toList());
    }

    private SupplierPayment load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.SUPPLIER_PAYMENT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Supplier payment was not found"));
    }

    private SupplierPaymentResponse toResponse(final SupplierPayment payment) {
        final Supplier supplier = supplierRepository.findById(payment.getSupplierId()).orElse(null);
        final List<SupplierPaymentAllocationResponse> allocations = payment.getAllocations().stream()
                .map(this::toAllocationResponse).toList();
        return new SupplierPaymentResponse(payment.getId(), payment.getSupplierId(),
                supplier == null ? null : supplier.getName(), payment.getPaymentDate(),
                payment.getPaymentMethod().name(), payment.getAmount(), payment.getReferenceNumber(),
                payment.getNotes(), payment.getPaidBy(), payment.getCreatedAt(), allocations);
    }

    private SupplierPaymentAllocationResponse toAllocationResponse(final SupplierPaymentAllocation allocation) {
        final GoodsReceipt goodsReceipt = goodsReceiptRepository.findById(allocation.getGoodsReceiptId()).orElse(null);
        final BigDecimal outstandingAfter = outstandingRepository.findById(allocation.getGoodsReceiptId())
                .map(GoodsReceiptOutstandingRepository.Row::outstandingAmount).orElse(BigDecimal.ZERO);
        return new SupplierPaymentAllocationResponse(allocation.getGoodsReceiptId(),
                goodsReceipt == null ? null : goodsReceipt.getReceiptNumber(), allocation.getAllocatedAmount(),
                outstandingAfter);
    }

    private PaymentMethod paymentMethod(final String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException | NullPointerException exception) {
            throw domainRejected("Unsupported payment method: " + value);
        }
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

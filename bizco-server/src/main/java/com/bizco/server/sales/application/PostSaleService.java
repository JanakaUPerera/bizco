package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.customer.application.CustomerCreditQueryPort;
import com.bizco.server.customer.application.CustomerReceivableSnapshot;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.domain.CreditEligibility;
import com.bizco.server.customer.domain.CustomerCreditPolicy;
import com.bizco.server.customer.domain.CustomerStatus;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.finance.domain.CashDirection;
import com.bizco.server.finance.domain.CashSourceType;
import com.bizco.server.finance.domain.CashbookEntry;
import com.bizco.server.finance.domain.CustomerPayment;
import com.bizco.server.finance.domain.PaymentMethod;
import com.bizco.server.finance.infrastructure.CashbookEntryRepository;
import com.bizco.server.finance.infrastructure.CustomerPaymentRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.sales.domain.DiscountType;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceLine;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.domain.LineType;
import com.bizco.server.sales.domain.HeldSale;
import com.bizco.server.sales.domain.SalesApproval;
import com.bizco.server.sales.domain.SalesApprovalType;
import com.bizco.server.sales.infrastructure.HeldSaleRepository;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import com.bizco.server.sales.infrastructure.SalesApprovalRepository;
import com.bizco.server.system.entity.BusinessProfile;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import com.bizco.server.system.repository.BusinessProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the DRAFT -&gt; POSTED transaction boundary (DomainModel.md &sect;9.10,
 * StateMachines.md &sect;4.4): validate -&gt; number -&gt; snapshot -&gt; payment/receivable -&gt;
 * cashbook -&gt; audit -&gt; commit, all inside one PostgreSQL transaction wrapped by
 * {@link IdempotencyService} so a retried POST with the same {@code Idempotency-Key} replays the
 * original result instead of posting twice.
 *
 * <p><b>Known gap:</b> this does not create {@code SALE} stock movements or validate available
 * stock for PRODUCT lines. The stock ledger (DatabaseDesign.md &sect;13, Week 12 per
 * DevelopmentPlan.md, migration number TBD) does not exist yet - Sales landed before Inventory in
 * this implementation's actual build order, the reverse of the assumption in the state-machine
 * precondition list. PRODUCT lines post today with no physical stock effect; this must be closed
 * when the ledger lands, the same way Catalog left its own stock-tracking fields {@code null}
 * rather than faked when it shipped before Inventory too.
 */
@Service
public class PostSaleService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DISCOUNT_TIER_25 = BigDecimal.valueOf(10);
    private static final BigDecimal DISCOUNT_TIER_50 = BigDecimal.valueOf(25);

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CustomerCreditQueryPort creditQueryPort;
    private final CustomerCreditPolicy creditPolicy = new CustomerCreditPolicy();
    private final ProductRepository productRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final PermissionService permissionService;
    private final SalesApprovalRepository salesApprovalRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final CashbookEntryRepository cashbookEntryRepository;
    private final HeldSaleRepository heldSaleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public PostSaleService(final InvoiceService invoiceService, final InvoiceRepository invoiceRepository,
                           final CustomerRepository customerRepository, final CustomerCreditQueryPort creditQueryPort,
                           final ProductRepository productRepository,
                           final BusinessProfileRepository businessProfileRepository,
                           final DocumentSequenceRepository documentSequenceRepository,
                           final PermissionService permissionService,
                           final SalesApprovalRepository salesApprovalRepository,
                           final CustomerPaymentRepository customerPaymentRepository,
                           final CashbookEntryRepository cashbookEntryRepository,
                           final HeldSaleRepository heldSaleRepository, final UserRepository userRepository,
                           final AuditService auditService, final IdempotencyService idempotencyService) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditQueryPort = creditQueryPort;
        this.productRepository = productRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.permissionService = permissionService;
        this.salesApprovalRepository = salesApprovalRepository;
        this.customerPaymentRepository = customerPaymentRepository;
        this.cashbookEntryRepository = cashbookEntryRepository;
        this.heldSaleRepository = heldSaleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public IdempotencyService.IdempotentResult<PostInvoiceResponse> post(final UUID invoiceId, final UUID idempotencyKey,
                                                                          final PostInvoiceRequest request,
                                                                          final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "invoice.post", Map.of("invoiceId", invoiceId, "request", request),
                PostInvoiceResponse.class, () -> doPost(invoiceId, idempotencyKey, request, actorId));
    }

    private PostInvoiceResponse doPost(final UUID invoiceId, final UUID idempotencyKey, final PostInvoiceRequest request,
                                       final UUID actorId) {
        final Invoice invoice = invoiceService.load(invoiceId);
        assertVersion(invoice, request.version());
        assertDraft(invoice);
        if (invoice.getLines().isEmpty()) {
            throw domainRejected("Invoice must have at least one line to post");
        }

        // Credit sales row-lock the customer (CRD-CON-001) so two concurrent credit postings for
        // the same customer serialize rather than both reading the same stale outstanding
        // receivable; non-credit sales don't touch credit at all, so an unlocked read is enough.
        final Customer customer = invoice.getCustomerId() == null ? null
                : (request.creditSale() ? customerRepository.findByIdForUpdate(invoice.getCustomerId())
                        : customerRepository.findById(invoice.getCustomerId()))
                        .orElseThrow(() -> new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND,
                                HttpStatus.NOT_FOUND, "Customer was not found"));
        if (request.creditSale() && customer == null) {
            throw new IdentityException(ApiErrorCode.INVOICE_CREDIT_CUSTOMER_REQUIRED, HttpStatus.BAD_REQUEST,
                    "A customer is required for a credit sale");
        }

        // Recalculate one more time from current lines/tax config - authoritative, not trusting
        // whatever the draft happened to show last.
        invoiceService.recalculate(invoice);

        final Set<String> actorPermissions = permissionService.effectivePermissions(actorId);
        final Set<SalesApproval> validApprovals = loadValidApprovals(invoiceId, request.approvalIds());
        validateDiscountApprovals(invoice, actorPermissions, validApprovals);
        validateBelowCost(invoice, actorPermissions, validApprovals);

        final BigDecimal paymentTotal = sumPayments(request.payments());
        validatePaymentTotal(invoice, request, paymentTotal);

        final BigDecimal creditAmount = invoice.getTotalAmount().subtract(paymentTotal).max(BigDecimal.ZERO);
        if (customer != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
            validateCreditEligibility(customer, creditAmount);
        }

        final BusinessProfile businessProfile = businessProfileRepository.findById((short) 1).orElse(null);
        final String invoiceNumber = allocateInvoiceNumber(invoice);
        final Instant postedAt = Instant.now();

        invoice.post(idempotencyKey, invoiceNumber,
                businessProfile == null ? null : businessProfile.getBusinessName(),
                businessProfile == null ? null : businessAddress(businessProfile),
                businessProfile == null ? null : businessProfile.getVatRegistrationNumber(),
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getAddressLine1(),
                null,
                postedAt);

        // Known gap: no SALE stock movement is created for PRODUCT lines here - see class Javadoc.

        final List<CustomerPayment> payments = recordPayments(invoice, request.payments(), actorId);
        recordCashbookEntries(payments, actorId);
        convertHeldSaleIfLinked(invoice.getId());

        invoiceRepository.flush();

        // Detail values must be Strings, not BigDecimal/boolean: AuditLog's JSON-mapped Map field
        // round-trips through JSON for Hibernate's dirty-check snapshot, and BigDecimal#equals is
        // scale-sensitive, so a raw BigDecimal here spuriously flags the just-inserted (and
        // immutable) AuditLog row as dirty at commit. Every other call site already follows this
        // string-only convention.
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_POSTED", actorId,
                Map.of("invoiceNumber", invoiceNumber, "totalAmount", invoice.getTotalAmount().toPlainString(),
                        "creditSale", String.valueOf(request.creditSale())));

        return toResponse(invoice, paymentTotal);
    }

    private void assertVersion(final Invoice invoice, final long expectedVersion) {
        if (invoice.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Invoice was modified by another user");
        }
    }

    /** Translates the domain's defensive {@code assertDraft()} guard into the API's 409 contract
     *  (mirrors {@code InvoiceService.assertDraft} - see that Javadoc). */
    private void assertDraft(final Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_DRAFT, HttpStatus.CONFLICT,
                    "Invoice is not DRAFT and cannot be modified");
        }
    }

    private Set<SalesApproval> loadValidApprovals(final UUID invoiceId, final List<UUID> approvalIds) {
        if (approvalIds == null || approvalIds.isEmpty()) {
            return Set.of();
        }
        final Set<SalesApproval> valid = new HashSet<>();
        for (final UUID approvalId : approvalIds) {
            salesApprovalRepository.findById(approvalId)
                    .filter(approval -> approval.getInvoiceId().equals(invoiceId))
                    .ifPresent(valid::add);
        }
        return valid;
    }

    /** SALE-DISC-001/002: MVP.md 5.6 discount tiers, checked at posting per StateMachines.md 4.4. */
    private void validateDiscountApprovals(final Invoice invoice, final Set<String> actorPermissions,
                                           final Set<SalesApproval> validApprovals) {
        requireDiscountApproval(discountPercent(invoice.getDiscountType(), invoice.getDiscountValue(),
                invoice.getSubtotal()), actorPermissions, validApprovals);
        for (final InvoiceLine line : invoice.getLines()) {
            final BigDecimal gross = line.getQuantity().multiply(line.getUnitPrice());
            requireDiscountApproval(discountPercent(line.getDiscountType(), line.getDiscountValue(), gross),
                    actorPermissions, validApprovals);
        }
    }

    private BigDecimal discountPercent(final DiscountType type, final BigDecimal value, final BigDecimal base) {
        if (type == DiscountType.PERCENTAGE) {
            return value == null ? BigDecimal.ZERO : value;
        }
        if (type == DiscountType.FIXED && base != null && base.compareTo(BigDecimal.ZERO) > 0) {
            return value.multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private void requireDiscountApproval(final BigDecimal percent, final Set<String> actorPermissions,
                                         final Set<SalesApproval> validApprovals) {
        if (percent.compareTo(DISCOUNT_TIER_50) > 0) {
            requireApproval(actorPermissions, validApprovals, "invoice.discount.approve_50",
                    SalesApprovalType.DISCOUNT_OVER_25);
        } else if (percent.compareTo(DISCOUNT_TIER_25) > 0) {
            requireApproval(actorPermissions, validApprovals, "invoice.discount.approve_25",
                    SalesApprovalType.DISCOUNT_10_25);
        }
    }

    /** SALE-DISC-004: a PRODUCT line's unit price below the product's current cost needs sign-off. */
    private void validateBelowCost(final Invoice invoice, final Set<String> actorPermissions,
                                   final Set<SalesApproval> validApprovals) {
        for (final InvoiceLine line : invoice.getLines()) {
            if (line.getLineType() != LineType.PRODUCT || line.getProductId() == null) {
                continue;
            }
            final Product product = productRepository.findById(line.getProductId()).orElse(null);
            if (product == null || product.getCostPrice() == null) {
                continue;
            }
            if (line.getUnitPrice().compareTo(product.getCostPrice()) < 0) {
                if (actorPermissions.contains("invoice.sell_below_cost")) {
                    continue;
                }
                final boolean approved = validApprovals.stream()
                        .anyMatch(approval -> approval.getApprovalType() == SalesApprovalType.BELOW_COST);
                if (!approved) {
                    throw new IdentityException(ApiErrorCode.INVOICE_BELOW_COST_APPROVAL_REQUIRED,
                            HttpStatus.CONFLICT, "Selling below cost requires permission or manager approval");
                }
            }
        }
    }

    private void requireApproval(final Set<String> actorPermissions, final Set<SalesApproval> validApprovals,
                                 final String permission, final SalesApprovalType approvalType) {
        if (actorPermissions.contains(permission)) {
            return;
        }
        final boolean approved = validApprovals.stream().anyMatch(approval -> approval.getApprovalType() == approvalType);
        if (!approved) {
            throw new IdentityException(ApiErrorCode.INVOICE_DISCOUNT_APPROVAL_REQUIRED, HttpStatus.CONFLICT,
                    "This discount requires manager approval");
        }
    }

    private BigDecimal sumPayments(final List<PaymentLineRequest> payments) {
        if (payments == null) {
            return BigDecimal.ZERO;
        }
        return payments.stream().map(p -> p.amount() == null ? BigDecimal.ZERO : p.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** MVP.md 5.7: an immediate sale must reach zero balance; a credit sale may leave a receivable. */
    private void validatePaymentTotal(final Invoice invoice, final PostInvoiceRequest request, final BigDecimal paymentTotal) {
        if (request.payments() != null) {
            for (final PaymentLineRequest payment : request.payments()) {
                if (payment.amount() == null || payment.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw domainRejected("Each payment amount must be greater than zero");
                }
                paymentMethod(payment.paymentMethod());
            }
        }
        if (paymentTotal.compareTo(invoice.getTotalAmount()) > 0) {
            throw domainRejected("Payment total cannot exceed the invoice total");
        }
        if (!request.creditSale() && paymentTotal.compareTo(invoice.getTotalAmount()) != 0) {
            throw new IdentityException(ApiErrorCode.INVOICE_PAYMENT_REQUIRED, HttpStatus.BAD_REQUEST,
                    "An immediate-payment sale must be paid in full");
        }
    }

    private void validateCreditEligibility(final Customer customer, final BigDecimal creditAmount) {
        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_SALES_BLOCKED, HttpStatus.CONFLICT,
                    "Customer account is blocked");
        }
        final CustomerReceivableSnapshot snapshot = creditQueryPort.snapshotFor(customer.getId());
        final CreditEligibility eligibility = creditPolicy.evaluate(customer, snapshot.outstandingReceivable(),
                snapshot.oldestOutstandingDays(), creditAmount);
        if (eligibility == CreditEligibility.LIMIT_EXCEEDED) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_CREDIT_LIMIT_EXCEEDED, HttpStatus.CONFLICT,
                    "Credit limit would be exceeded");
        }
        if (eligibility == CreditEligibility.BLOCK_ALL || eligibility == CreditEligibility.CASH_ONLY) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_CREDIT_BLOCKED_BY_AGING, HttpStatus.CONFLICT,
                    "Customer's overdue balance blocks further credit sales");
        }
    }

    private String allocateInvoiceNumber(final Invoice invoice) {
        final long next = documentSequenceRepository.nextDailyValue("INV", invoice.getInvoiceDate(), "INV", 4);
        return documentSequenceRepository.formatDaily("INV", invoice.getInvoiceDate(), next, 4);
    }

    /**
     * StateMachines.md &sect;7.6: a held sale only reaches CONVERTED as part of successful invoice
     * posting - done here, in the same transaction as the rest of the posting effects, so there is
     * no window where the invoice is POSTED but the held sale is still HELD/RESUMED.
     */
    private void convertHeldSaleIfLinked(final UUID invoiceId) {
        heldSaleRepository.findByConvertedInvoiceId(invoiceId).ifPresent(HeldSale::markConverted);
    }

    private List<CustomerPayment> recordPayments(final Invoice invoice, final List<PaymentLineRequest> payments,
                                                 final UUID actorId) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        return payments.stream().map(line -> {
            final CustomerPayment payment = new CustomerPayment(UUID.randomUUID(), invoice.getCustomerId(),
                    Instant.now(), paymentMethod(line.paymentMethod()), line.amount(), line.referenceNumber(),
                    actorId, null);
            payment.allocateTo(invoice.getId(), line.amount());
            return customerPaymentRepository.save(payment);
        }).toList();
    }

    private void recordCashbookEntries(final List<CustomerPayment> payments, final UUID actorId) {
        for (final CustomerPayment payment : payments) {
            cashbookEntryRepository.save(new CashbookEntry(payment.getRequestId(), payment.getPaymentDate(),
                    CashDirection.IN, CashSourceType.CUSTOMER_PAYMENT, payment.getAmount(), payment.getPaymentMethod(),
                    null, payment.getId(), null, actorId));
        }
    }

    private PaymentMethod paymentMethod(final String value) {
        if (value == null || value.isBlank()) {
            throw domainRejected("paymentMethod is required for every payment line");
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException exception) {
            throw domainRejected("Unsupported payment method: " + value);
        }
    }

    private String businessAddress(final BusinessProfile profile) {
        final String line1 = profile.getAddressLine1();
        final String city = profile.getCity();
        if (line1 == null && city == null) {
            return null;
        }
        if (line1 == null) {
            return city;
        }
        return city == null ? line1 : line1 + ", " + city;
    }

    private PostInvoiceResponse toResponse(final Invoice invoice, final BigDecimal amountPaid) {
        final BigDecimal balanceDue = invoice.getTotalAmount().subtract(amountPaid).max(BigDecimal.ZERO);
        final String paymentStatus = balanceDue.compareTo(BigDecimal.ZERO) <= 0 ? "PAID"
                : amountPaid.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "UNPAID";
        return new PostInvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus().name(),
                paymentStatus, invoice.getSubtotal(), invoice.getVatAmount(), invoice.getTotalAmount(), amountPaid,
                balanceDue, invoice.getPostedAt(), invoice.getVersion());
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

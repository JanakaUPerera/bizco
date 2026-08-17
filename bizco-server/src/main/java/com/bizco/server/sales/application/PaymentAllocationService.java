package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentSearchResponse;
import com.bizco.common.dto.finance.PaymentDtos.PaymentAllocationRequest;
import com.bizco.common.dto.finance.PaymentDtos.PaymentAllocationResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordCustomerPaymentRequest;
import com.bizco.common.dto.finance.PaymentDtos.RecordInvoicePaymentRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.finance.domain.CashDirection;
import com.bizco.server.finance.domain.CashSourceType;
import com.bizco.server.finance.domain.CashbookEntry;
import com.bizco.server.finance.domain.CustomerPayment;
import com.bizco.server.finance.domain.CustomerPaymentAllocation;
import com.bizco.server.finance.domain.PaymentMethod;
import com.bizco.server.finance.infrastructure.CashbookEntryRepository;
import com.bizco.server.finance.infrastructure.CustomerPaymentRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.infrastructure.InvoiceBalanceRepository;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a payment against one or more already-POSTED invoices after the fact
 * (ApiContracts.md &sect;17, FIN-AR-PAY-001..004). Distinct from the payment recording inside
 * {@code PostSaleService}, which only ever pays the single invoice being posted in that same
 * transaction; this is the "customer pays down their running balance later" path, and can span
 * several invoices in one payment (FIN-AR-PAY-003).
 *
 * <p>Locks the customer row for the duration (the same {@code findByIdForUpdate} pattern
 * {@code PostSaleService} uses for CRD-CON-001) so two concurrent payment requests against the
 * same customer's invoices serialize - FIN-AR-CON-001 requires that two payments which together
 * would exceed an invoice's balance cannot both commit.
 */
@Service
public class PaymentAllocationService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceBalanceRepository invoiceBalanceRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final CashbookEntryRepository cashbookEntryRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public PaymentAllocationService(final CustomerRepository customerRepository, final InvoiceRepository invoiceRepository,
                                    final InvoiceBalanceRepository invoiceBalanceRepository,
                                    final CustomerPaymentRepository customerPaymentRepository,
                                    final CashbookEntryRepository cashbookEntryRepository,
                                    final UserRepository userRepository, final AuditService auditService,
                                    final IdempotencyService idempotencyService) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceBalanceRepository = invoiceBalanceRepository;
        this.customerPaymentRepository = customerPaymentRepository;
        this.cashbookEntryRepository = cashbookEntryRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public IdempotencyService.IdempotentResult<CustomerPaymentResponse> recordForInvoice(final UUID invoiceId,
                                                                                          final UUID idempotencyKey,
                                                                                          final RecordInvoicePaymentRequest request,
                                                                                          final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "payment.record_for_invoice",
                Map.of("invoiceId", invoiceId, "request", request), CustomerPaymentResponse.class, () -> {
                    final Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                            ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
                    if (invoice.getCustomerId() == null) {
                        throw domainRejected("Cannot record a later payment against an invoice with no customer");
                    }
                    final RecordCustomerPaymentRequest wrapped = new RecordCustomerPaymentRequest(invoice.getCustomerId(),
                            request.paymentDate(), request.paymentMethod(), request.amount(), request.referenceNumber(),
                            request.notes(), List.of(new PaymentAllocationRequest(invoiceId, request.amount())));
                    return doRecord(idempotencyKey, wrapped, actorId);
                });
    }

    @Transactional
    public IdempotencyService.IdempotentResult<CustomerPaymentResponse> recordForCustomer(final UUID idempotencyKey,
                                                                                           final RecordCustomerPaymentRequest request,
                                                                                           final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "payment.record", request, CustomerPaymentResponse.class,
                () -> doRecord(idempotencyKey, request, actorId));
    }

    private CustomerPaymentResponse doRecord(final UUID idempotencyKey, final RecordCustomerPaymentRequest request,
                                             final UUID actorId) {
        if (request.customerId() == null) {
            throw domainRejected("customerId is required");
        }
        // Row-locked for FIN-AR-CON-001 - see class Javadoc.
        final Customer customer = customerRepository.findByIdForUpdate(request.customerId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer was not found"));
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw domainRejected("At least one allocation is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw domainRejected("amount must be greater than zero");
        }
        final BigDecimal allocatedTotal = request.allocations().stream()
                .map(allocation -> allocation.amount() == null ? BigDecimal.ZERO : allocation.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(request.amount()) != 0) {
            throw new IdentityException(ApiErrorCode.PAYMENT_ALLOCATION_MISMATCH, HttpStatus.BAD_REQUEST,
                    "Allocations must sum to exactly the payment amount");
        }
        for (final PaymentAllocationRequest allocation : request.allocations()) {
            validateAllocation(customer, allocation);
        }

        final PaymentMethod method = paymentMethod(request.paymentMethod());
        final Instant paymentDate = request.paymentDate() == null ? Instant.now() : request.paymentDate();
        final CustomerPayment payment = new CustomerPayment(idempotencyKey, customer.getId(), paymentDate, method,
                request.amount(), request.referenceNumber(), actorId, request.notes());
        request.allocations().forEach(allocation -> payment.allocateTo(allocation.invoiceId(), allocation.amount()));
        final CustomerPayment saved = customerPaymentRepository.save(payment);
        cashbookEntryRepository.save(new CashbookEntry(idempotencyKey, paymentDate, CashDirection.IN,
                CashSourceType.CUSTOMER_PAYMENT, saved.getAmount(), method, null, saved.getId(), null, actorId));
        auditService.record("CUSTOMER_PAYMENT", saved.getId().toString(), "PAYMENT_RECORDED", actorId,
                Map.of("customerId", customer.getId().toString(), "amount", saved.getAmount().toPlainString(),
                        "invoiceCount", String.valueOf(request.allocations().size())));
        return toResponse(saved);
    }

    private void validateAllocation(final Customer customer, final PaymentAllocationRequest allocation) {
        if (allocation.invoiceId() == null || allocation.amount() == null
                || allocation.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw domainRejected("Each allocation needs a positive amount and an invoice");
        }
        final Invoice invoice = invoiceRepository.findById(allocation.invoiceId()).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
        if (!customer.getId().equals(invoice.getCustomerId())) {
            throw domainRejected("Invoice does not belong to this customer");
        }
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_POSTED, HttpStatus.CONFLICT,
                    "Only a POSTED invoice can receive a payment");
        }
        final BigDecimal balance = invoiceBalanceRepository.balanceDue(invoice.getId());
        if (allocation.amount().compareTo(balance) > 0) {
            throw new IdentityException(ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE, HttpStatus.CONFLICT,
                    "Allocation exceeds invoice " + invoice.getInvoiceNumber() + "'s remaining balance");
        }
    }

    @Transactional(readOnly = true)
    public CustomerPaymentSearchResponse invoicePayments(final UUID invoiceId) {
        return new CustomerPaymentSearchResponse(customerPaymentRepository.findByInvoiceId(invoiceId).stream()
                .map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public CustomerPaymentSearchResponse customerPayments(final UUID customerId) {
        return new CustomerPaymentSearchResponse(customerPaymentRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse).toList());
    }

    private CustomerPaymentResponse toResponse(final CustomerPayment payment) {
        final List<PaymentAllocationResponse> allocations = payment.getAllocations().stream()
                .map(this::toAllocationResponse).toList();
        return new CustomerPaymentResponse(payment.getId(), payment.getCustomerId(), payment.getPaymentDate(),
                payment.getPaymentMethod().name(), payment.getAmount(), payment.getReferenceNumber(),
                payment.getReceivedBy(), allocations);
    }

    private PaymentAllocationResponse toAllocationResponse(final CustomerPaymentAllocation allocation) {
        return new PaymentAllocationResponse(allocation.getInvoiceId(), allocation.getAllocatedAmount(),
                invoiceBalanceRepository.balanceDue(allocation.getInvoiceId()));
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

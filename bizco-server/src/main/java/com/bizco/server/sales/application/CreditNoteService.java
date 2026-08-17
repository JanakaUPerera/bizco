package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteSearchResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityLineResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.SettlementRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.finance.domain.CashDirection;
import com.bizco.server.finance.domain.CashSourceType;
import com.bizco.server.finance.domain.CashbookEntry;
import com.bizco.server.finance.domain.CreditNoteApplication;
import com.bizco.server.finance.domain.CustomerRefund;
import com.bizco.server.finance.domain.PaymentMethod;
import com.bizco.server.finance.infrastructure.CashbookEntryRepository;
import com.bizco.server.finance.infrastructure.CreditNoteApplicationRepository;
import com.bizco.server.finance.infrastructure.CustomerRefundRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.sales.domain.CreditNote;
import com.bizco.server.sales.domain.CreditNoteLine;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceLine;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.domain.LineType;
import com.bizco.server.sales.infrastructure.CreditNoteRepository;
import com.bizco.server.sales.infrastructure.InvoiceBalanceRepository;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credit note / return service (DevelopmentPlan.md Week 8 task 8.3-8.4, StateMachines.md
 * &sect;6, ApiContracts.md &sect;18). See {@link CreditNote}'s Javadoc for the one-shot
 * settlement scope decision and the documented stock-restock gap.
 *
 * <p>Return window: MVP.md &sect;5.8.1 specifies a per-business-type window (7 days general
 * retail, 14 days electronics), but nothing in the current schema classifies a product by
 * business type - only {@code ProductType} (INVENTORY/SERVICE), which is a different axis. This
 * uses one uniform configurable window (default 7 days, the general-retail figure) for every
 * return; closing the electronics-specific 14-day override needs that classification added first.
 */
@Service
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceBalanceRepository invoiceBalanceRepository;
    private final CreditNoteApplicationRepository creditNoteApplicationRepository;
    private final CustomerRefundRepository customerRefundRepository;
    private final CashbookEntryRepository cashbookEntryRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final long returnWindowDays;

    public CreditNoteService(final CreditNoteRepository creditNoteRepository, final InvoiceRepository invoiceRepository,
                             final InvoiceBalanceRepository invoiceBalanceRepository,
                             final CreditNoteApplicationRepository creditNoteApplicationRepository,
                             final CustomerRefundRepository customerRefundRepository,
                             final CashbookEntryRepository cashbookEntryRepository,
                             final DocumentSequenceRepository documentSequenceRepository,
                             final UserRepository userRepository, final AuditService auditService,
                             final IdempotencyService idempotencyService,
                             @Value("${bizco.sales.return-window-days:7}") final long returnWindowDays) {
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceBalanceRepository = invoiceBalanceRepository;
        this.creditNoteApplicationRepository = creditNoteApplicationRepository;
        this.customerRefundRepository = customerRefundRepository;
        this.cashbookEntryRepository = cashbookEntryRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.returnWindowDays = returnWindowDays;
    }

    @Transactional
    public IdempotencyService.IdempotentResult<CreditNoteResponse> issue(final UUID idempotencyKey,
                                                                          final CreateCreditNoteRequest request,
                                                                          final Authentication authentication) {
        final UUID actorId = actor(authentication);
        return idempotencyService.execute(idempotencyKey, "credit_note.issue", request, CreditNoteResponse.class,
                () -> doIssue(idempotencyKey, request, actorId));
    }

    private CreditNoteResponse doIssue(final UUID idempotencyKey, final CreateCreditNoteRequest request, final UUID actorId) {
        if (request.originalInvoiceId() == null) {
            throw domainRejected("originalInvoiceId is required");
        }
        // Row-locked (DatabaseDesign.md 14.2): a concurrent credit note against the same invoice
        // must serialize behind this one so cumulative-return-quantity validation below is safe.
        final Invoice invoice = invoiceRepository.findByIdForUpdate(request.originalInvoiceId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Invoice was not found"));
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_POSTED, HttpStatus.CONFLICT,
                    "Only a POSTED invoice can be returned against");
        }
        if (invoice.getCustomerId() == null) {
            // credit_notes.customer_id is NOT NULL (DatabaseDesign.md 14.1) - a walk-in sale with
            // no customer record can't be represented here. See class Javadoc.
            throw new IdentityException(ApiErrorCode.INVOICE_CREDIT_CUSTOMER_REQUIRED, HttpStatus.BAD_REQUEST,
                    "A credit note requires the original sale to have a customer");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw domainRejected("A credit note must return at least one line");
        }
        final LocalDate deadline = invoice.getInvoiceDate().plusDays(returnWindowDays);
        if (LocalDate.now().isAfter(deadline)) {
            throw new IdentityException(ApiErrorCode.RETURN_WINDOW_EXPIRED, HttpStatus.CONFLICT,
                    "The return window for this invoice has expired");
        }

        final long next = documentSequenceRepository.nextDailyValue("CN", LocalDate.now(), "CN", 4);
        final String number = documentSequenceRepository.formatDaily("CN", LocalDate.now(), next, 4);
        final CreditNote creditNote = new CreditNote(idempotencyKey, number, invoice.getId(), invoice.getCustomerId(),
                blankToDefault(request.reason(), "Customer return"), actorId);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (final CreditNoteLineRequest lineRequest : request.lines()) {
            final InvoiceLine original = requireLine(invoice, lineRequest.invoiceLineId());
            final BigDecimal quantityReturned = lineRequest.quantityReturned();
            if (quantityReturned == null || quantityReturned.compareTo(BigDecimal.ZERO) <= 0) {
                throw domainRejected("quantityReturned must be greater than zero");
            }
            final BigDecimal alreadyReturned = creditNoteRepository.sumReturnedQuantity(original.getId());
            if (alreadyReturned.add(quantityReturned).compareTo(original.getQuantity()) > 0) {
                throw new IdentityException(ApiErrorCode.RETURN_QUANTITY_EXCEEDED, HttpStatus.CONFLICT,
                        "Requested return quantity exceeds the remaining eligible quantity");
            }
            final boolean restock = lineRequest.restock() && original.getLineType() == LineType.PRODUCT;
            final BigDecimal lineTaxable = proportional(original.getTaxableAmount(), quantityReturned, original.getQuantity());
            final BigDecimal lineVat = proportional(original.getVatAmount(), quantityReturned, original.getQuantity());
            final BigDecimal lineTotal = lineTaxable.add(lineVat);
            creditNote.addLine(new CreditNoteLine(original.getId(), quantityReturned, original.getUnitPrice(),
                    lineTaxable, original.getVatRateSnapshot(), lineVat, lineTotal, restock));
            subtotal = subtotal.add(lineTaxable);
            vatTotal = vatTotal.add(lineVat);
            grandTotal = grandTotal.add(lineTotal);
        }
        creditNote.applyTotals(subtotal, vatTotal, grandTotal);
        final CreditNote saved = creditNoteRepository.save(creditNote);

        settle(saved, request.settlement(), actorId);

        // Known gap (see class Javadoc): no CUSTOMER_RETURN stock movement is created for
        // restockable lines - the stock ledger (Week 12) does not exist yet.

        auditService.record("CREDIT_NOTE", saved.getId().toString(), "CREDIT_NOTE_POSTED", actorId,
                Map.of("creditNoteNumber", number, "originalInvoiceId", invoice.getId().toString(),
                        "totalAmount", grandTotal.toPlainString()));
        return toResponse(saved);
    }

    private void settle(final CreditNote creditNote, final SettlementRequest settlement, final UUID actorId) {
        final String type = settlement == null || settlement.type() == null || settlement.type().isBlank()
                ? "CUSTOMER_CREDIT" : settlement.type().trim().toUpperCase();
        switch (type) {
            case "APPLY_TO_BALANCE" -> applyToOriginalInvoice(creditNote);
            case "REFUND" -> refund(creditNote, settlement, actorId);
            case "CUSTOMER_CREDIT" -> { /* leave ISSUED - see class Javadoc */ }
            default -> throw domainRejected("Unknown settlement type: " + type);
        }
    }

    /** SALE-CN-005: reduces the original invoice's balance; no cashbook effect. */
    private void applyToOriginalInvoice(final CreditNote creditNote) {
        final BigDecimal balance = invoiceBalanceRepository.balanceDue(creditNote.getOriginalInvoiceId());
        if (creditNote.getTotalAmount().compareTo(balance) > 0) {
            throw domainRejected("Credit note total exceeds the invoice's remaining balance");
        }
        creditNoteApplicationRepository.save(new CreditNoteApplication(creditNote.getId(),
                creditNote.getOriginalInvoiceId(), creditNote.getTotalAmount()));
        creditNote.markSettled(Instant.now());
    }

    /** SALE-CN-004: a straight cash refund; cashbook OUT, no invoice-balance effect. */
    private void refund(final CreditNote creditNote, final SettlementRequest settlement, final UUID actorId) {
        if (settlement == null || settlement.paymentMethod() == null) {
            throw domainRejected("paymentMethod is required for a REFUND settlement");
        }
        final PaymentMethod method = paymentMethod(settlement.paymentMethod());
        final UUID requestId = UUID.randomUUID();
        final Instant now = Instant.now();
        final CustomerRefund refund = new CustomerRefund(requestId, creditNote.getId(),
                settlement.originalCustomerPaymentId(), now, method, creditNote.getTotalAmount(), null, actorId,
                creditNote.getReason());
        final CustomerRefund savedRefund = customerRefundRepository.save(refund);
        cashbookEntryRepository.save(new CashbookEntry(requestId, now, CashDirection.OUT, CashSourceType.CUSTOMER_REFUND,
                savedRefund.getAmount(), method, null, savedRefund.getId(), creditNote.getReason(), actorId));
        creditNote.markSettled(now);
    }

    @Transactional(readOnly = true)
    public ReturnEligibilityResponse returnEligibility(final UUID invoiceId) {
        final Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
        final LocalDate deadline = invoice.getInvoiceDate().plusDays(returnWindowDays);
        final List<ReturnEligibilityLineResponse> lines = invoice.getLines().stream().map(line -> {
            final BigDecimal alreadyReturned = creditNoteRepository.sumReturnedQuantity(line.getId());
            return new ReturnEligibilityLineResponse(line.getId(), line.getDescriptionSnapshot(), line.getQuantity(),
                    alreadyReturned, line.getQuantity().subtract(alreadyReturned).max(BigDecimal.ZERO),
                    line.getTaxCategorySnapshot().name(), line.getVatRateSnapshot(), line.getLineType() == LineType.PRODUCT);
        }).toList();
        return new ReturnEligibilityResponse(invoiceId, invoice.getInvoiceDate(), deadline,
                !LocalDate.now().isAfter(deadline), lines);
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse get(final UUID creditNoteId) {
        return toResponse(load(creditNoteId));
    }

    @Transactional(readOnly = true)
    public CreditNoteSearchResponse search(final UUID customerId, final UUID invoiceId, final String number,
                                           final LocalDate fromDate, final LocalDate toDate) {
        return new CreditNoteSearchResponse(creditNoteRepository.search(customerId, invoiceId,
                number == null || number.isBlank() ? null : number, fromDate, toDate).stream()
                .map(this::toResponse).toList());
    }

    private InvoiceLine requireLine(final Invoice invoice, final UUID lineId) {
        try {
            return invoice.line(lineId);
        } catch (final IllegalArgumentException exception) {
            throw new IdentityException(ApiErrorCode.INVOICE_LINE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Invoice line was not found on this invoice");
        }
    }

    private BigDecimal proportional(final BigDecimal amount, final BigDecimal quantity, final BigDecimal totalQuantity) {
        return amount.multiply(quantity).divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }

    private PaymentMethod paymentMethod(final String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException | NullPointerException exception) {
            throw domainRejected("Unsupported payment method: " + value);
        }
    }

    private String blankToDefault(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private CreditNote load(final UUID creditNoteId) {
        return creditNoteRepository.findById(creditNoteId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.CREDIT_NOTE_NOT_FOUND, HttpStatus.NOT_FOUND, "Credit note was not found"));
    }

    private CreditNoteResponse toResponse(final CreditNote creditNote) {
        final List<CreditNoteLineResponse> lines = creditNote.getLines().stream()
                .map(line -> new CreditNoteLineResponse(line.getId(), line.getOriginalInvoiceLineId(),
                        line.getQuantityReturned(), line.getUnitPriceSnapshot(), line.getTaxableAmount(),
                        line.getVatRateSnapshot(), line.getVatAmount(), line.getLineTotal(), line.isRestock()))
                .toList();
        return new CreditNoteResponse(creditNote.getId(), creditNote.getCreditNoteNumber(),
                creditNote.getOriginalInvoiceId(), creditNote.getCustomerId(), creditNote.getStatus().name(),
                creditNote.getReason(), creditNote.getSubtotal(), creditNote.getVatAmount(), creditNote.getTotalAmount(),
                creditNote.getIssuedAt(), creditNote.getIssuedBy(), creditNote.getAppliedAt(), lines);
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

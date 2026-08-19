package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceLine;
import com.bizco.server.sales.domain.LineType;
import com.bizco.server.sales.infrastructure.CreditNoteRepository;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POSTED -&gt; VOIDED (StateMachines.md &sect;4.5, SALE-VOID-001..004). Historical totals/snapshots
 * are left exactly as posted (SALE-VOID-001 "original historical values remain") - voiding never
 * edits them, and {@code v_customer_receivables} already excludes VOIDED invoices, so this alone
 * removes the invoice from the customer's outstanding balance without needing an explicit
 * reversal entry.
 *
 * <p><b>Deliberate MVP simplification:</b> this does not reverse existing payments or cashbook
 * entries against the invoice being voided - real money already collected stays recorded as
 * collected; recovering it is a separate, manual refund action (the same
 * {@code CreditNoteService.refund} flow), not something void does automatically. What void does
 * enforce is StateMachines.md &sect;4.5's "avoid duplicate economic reversal" precondition: an
 * invoice that already has a credit note issued against it cannot be voided (the credit note is
 * the correction instrument at that point, not a full void).
 *
 * <p>Physical stock is reversed, though: each PRODUCT line gets a {@code SALE_VOID} movement via
 * {@link StockPostingService} so a void does restore the stock its SALE movement removed.
 */
@Service
public class InvoiceVoidService {

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StockPostingService stockPostingService;

    public InvoiceVoidService(final InvoiceRepository invoiceRepository, final CreditNoteRepository creditNoteRepository,
                              final InvoiceService invoiceService, final UserRepository userRepository,
                              final AuditService auditService, final StockPostingService stockPostingService) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.stockPostingService = stockPostingService;
    }

    @Transactional
    public InvoiceDetailResponse voidInvoice(final UUID invoiceId, final VoidInvoiceRequest request,
                                             final Authentication authentication) {
        final UUID actorId = actor(authentication);
        final Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
        if (invoice.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Invoice was modified by another user");
        }
        if (creditNoteRepository.existsByOriginalInvoiceId(invoiceId)) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.CONFLICT,
                    "Invoice has credit notes issued against it and cannot be voided directly");
        }
        try {
            invoice.voidInvoice(actorId, request.reason(), Instant.now());
        } catch (final IllegalStateException exception) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_POSTED, HttpStatus.CONFLICT, exception.getMessage());
        } catch (final IllegalArgumentException exception) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        // "reversal/corrective postings as required" (StateMachines.md 4.5): a SALE_VOID movement
        // reverses each PRODUCT line's earlier SALE, keyed by the same invoice_line_id under the
        // distinct SALE_VOID type. Lines sourced from a JobPart never had a SALE movement (their
        // stock left as JOB_PART instead), so they are excluded here too.
        final List<InvoiceLine> productLines = invoice.getLines().stream()
                .filter(line -> line.getLineType() == LineType.PRODUCT && line.getSourceJobPartId() == null).toList();
        if (!productLines.isEmpty()) {
            stockPostingService.lockProducts(productLines.stream().map(InvoiceLine::getProductId)
                    .collect(Collectors.toSet()));
            for (final InvoiceLine line : productLines) {
                stockPostingService.postSaleVoid(line.getProductId(), invoice.getId(), line.getId(),
                        line.getQuantity(), actorId);
            }
        }

        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_VOIDED", actorId,
                Map.of("reason", request.reason()));
        return invoiceService.get(invoiceId);
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

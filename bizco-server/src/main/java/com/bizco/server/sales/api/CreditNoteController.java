package com.bizco.server.sales.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteSearchResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.CreditNoteService;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ApiContracts.md &sect;18. */
@RestController
@RequestMapping("/api/v1")
public class CreditNoteController {

    private final CreditNoteService service;

    public CreditNoteController(final CreditNoteService service) {
        this.service = service;
    }

    @GetMapping("/invoices/{invoiceId}/return-eligibility")
    @PreAuthorize("hasAuthority('invoice.credit_note.create')")
    ReturnEligibilityResponse returnEligibility(@PathVariable final UUID invoiceId) {
        return service.returnEligibility(invoiceId);
    }

    @PostMapping("/credit-notes")
    @PreAuthorize("hasAuthority('invoice.credit_note.create')")
    ResponseEntity<CreditNoteResponse> issue(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                             @RequestBody final CreateCreditNoteRequest request,
                                             final Authentication authentication) {
        final IdempotentResult<CreditNoteResponse> result = service.issue(idempotencyKey, request, authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/credit-notes/{creditNoteId}")
    @PreAuthorize("hasAuthority('invoice.read')")
    CreditNoteResponse get(@PathVariable final UUID creditNoteId) {
        return service.get(creditNoteId);
    }

    @GetMapping("/credit-notes")
    @PreAuthorize("hasAuthority('invoice.read')")
    CreditNoteSearchResponse search(@RequestParam(required = false) final UUID customerId,
                                    @RequestParam(required = false) final UUID invoiceId,
                                    @RequestParam(required = false) final String number,
                                    @RequestParam(required = false) final LocalDate fromDate,
                                    @RequestParam(required = false) final LocalDate toDate) {
        return service.search(customerId, invoiceId, number, fromDate, toDate);
    }
}

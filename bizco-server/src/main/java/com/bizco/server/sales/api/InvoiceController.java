package com.bizco.server.sales.api;

import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSearchResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import com.bizco.server.sales.application.InvoiceService;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(final InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('invoice.create')")
    ResponseEntity<InvoiceSummaryResponse> createDraft(@RequestBody final CreateDraftInvoiceRequest request,
                                                       final Authentication authentication) {
        final InvoiceSummaryResponse created = invoiceService.createDraft(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + created.invoiceId())).body(created);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('invoice.read')")
    InvoiceDetailResponse get(@PathVariable final UUID invoiceId) {
        return invoiceService.get(invoiceId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('invoice.read')")
    InvoiceSearchResponse search(@RequestParam(required = false) final String number,
                                 @RequestParam(required = false) final UUID customerId,
                                 @RequestParam(required = false) final String status,
                                 @RequestParam(required = false) final String type,
                                 @RequestParam(required = false) final LocalDate fromDate,
                                 @RequestParam(required = false) final LocalDate toDate,
                                 @RequestParam(defaultValue = "0") final int page,
                                 @RequestParam(defaultValue = "20") final int size) {
        return invoiceService.search(number, customerId, status, type, fromDate, toDate, page, size);
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse updateHeader(@PathVariable final UUID invoiceId,
                                       @RequestBody final UpdateInvoiceHeaderRequest request) {
        return invoiceService.updateHeader(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/lines")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse addLine(@PathVariable final UUID invoiceId, @RequestBody final AddInvoiceLineRequest request) {
        return invoiceService.addLine(invoiceId, request);
    }

    @PutMapping("/{invoiceId}/lines/{lineId}")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse updateLine(@PathVariable final UUID invoiceId, @PathVariable final UUID lineId,
                                     @RequestBody final UpdateInvoiceLineRequest request) {
        return invoiceService.updateLine(invoiceId, lineId, request);
    }

    @DeleteMapping("/{invoiceId}/lines/{lineId}")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse deleteLine(@PathVariable final UUID invoiceId, @PathVariable final UUID lineId) {
        return invoiceService.deleteLine(invoiceId, lineId);
    }

    @PostMapping("/{invoiceId}/preview")
    @PreAuthorize("hasAuthority('invoice.create')")
    InvoiceDetailResponse preview(@PathVariable final UUID invoiceId) {
        return invoiceService.preview(invoiceId);
    }
}

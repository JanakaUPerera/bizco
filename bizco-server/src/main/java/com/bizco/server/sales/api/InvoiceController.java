package com.bizco.server.sales.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSearchResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentSearchResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordInvoicePaymentRequest;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.InvoiceVoidService;
import com.bizco.server.sales.application.PaymentAllocationService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.sales.application.ReceiptService;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PostSaleService postSaleService;
    private final InvoiceVoidService invoiceVoidService;
    private final PaymentAllocationService paymentAllocationService;
    private final ReceiptService receiptService;

    public InvoiceController(final InvoiceService invoiceService, final PostSaleService postSaleService,
                             final InvoiceVoidService invoiceVoidService,
                             final PaymentAllocationService paymentAllocationService,
                             final ReceiptService receiptService) {
        this.invoiceService = invoiceService;
        this.postSaleService = postSaleService;
        this.invoiceVoidService = invoiceVoidService;
        this.paymentAllocationService = paymentAllocationService;
        this.receiptService = receiptService;
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

    @PostMapping("/{invoiceId}/post")
    @PreAuthorize("hasAuthority('invoice.create')")
    ResponseEntity<PostInvoiceResponse> post(@PathVariable final UUID invoiceId,
                                             @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                             @RequestBody final PostInvoiceRequest request,
                                             final Authentication authentication) {
        final IdempotentResult<PostInvoiceResponse> result = postSaleService.post(invoiceId, idempotencyKey, request,
                authentication);
        return ResponseEntity.ok()
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasAuthority('invoice.void')")
    InvoiceDetailResponse voidInvoice(@PathVariable final UUID invoiceId, @RequestBody final VoidInvoiceRequest request,
                                      final Authentication authentication) {
        return invoiceVoidService.voidInvoice(invoiceId, request, authentication);
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAuthority('invoice.payment.create')")
    ResponseEntity<CustomerPaymentResponse> recordPayment(@PathVariable final UUID invoiceId,
                                                           @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                           @RequestBody final RecordInvoicePaymentRequest request,
                                                           final Authentication authentication) {
        final IdempotentResult<CustomerPaymentResponse> result = paymentAllocationService.recordForInvoice(invoiceId,
                idempotencyKey, request, authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAuthority('invoice.read')")
    CustomerPaymentSearchResponse payments(@PathVariable final UUID invoiceId) {
        return paymentAllocationService.invoicePayments(invoiceId);
    }

    @GetMapping("/{invoiceId}/receipt")
    @PreAuthorize("hasAuthority('invoice.read')")
    ResponseEntity<byte[]> receipt(@PathVariable final UUID invoiceId) {
        return pdfResponse(receiptService.receipt(invoiceId), invoiceId);
    }

    /** Distinct from the plain receipt fetch above: audited, and requires the dedicated reprint permission (SALE task 8.9). */
    @PostMapping("/{invoiceId}/receipt/reprint")
    @PreAuthorize("hasAuthority('invoice.reprint')")
    ResponseEntity<byte[]> reprintReceipt(@PathVariable final UUID invoiceId, final Authentication authentication) {
        return pdfResponse(receiptService.reprint(invoiceId, authentication), invoiceId);
    }

    private ResponseEntity<byte[]> pdfResponse(final byte[] pdf, final UUID invoiceId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"invoice-" + invoiceId + ".pdf\"")
                .body(pdf);
    }
}

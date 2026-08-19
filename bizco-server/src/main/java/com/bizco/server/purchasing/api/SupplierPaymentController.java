package com.bizco.server.purchasing.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentSearchResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.application.SupplierPaymentService;
import java.net.URI;
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

/** Supplier payments and multi-receipt allocations (DevelopmentPlan.md Week 15, DatabaseDesign.md
 *  &sect;17.10/17.11). One-shot create, mirroring {@code PaymentAllocationController} on the sales
 *  side. */
@RestController
@RequestMapping("/api/v1/supplier-payments")
public class SupplierPaymentController {

    private final SupplierPaymentService service;

    public SupplierPaymentController(final SupplierPaymentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchasing.read')")
    SupplierPaymentSearchResponse search(@RequestParam(required = false) final UUID supplierId,
                                         @RequestParam(defaultValue = "0") final int page,
                                         @RequestParam(defaultValue = "20") final int size) {
        return service.search(supplierId, page, size);
    }

    @GetMapping("/{supplierPaymentId}")
    @PreAuthorize("hasAuthority('purchasing.read')")
    SupplierPaymentResponse get(@PathVariable final UUID supplierPaymentId) {
        return service.get(supplierPaymentId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchasing.payment.create')")
    ResponseEntity<SupplierPaymentResponse> record(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                    @RequestBody final RecordSupplierPaymentRequest request,
                                                    final Authentication authentication) {
        final IdempotentResult<SupplierPaymentResponse> result = service.record(idempotencyKey, request, authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .location(URI.create("/api/v1/supplier-payments/" + result.response().supplierPaymentId()))
                .body(result.response());
    }
}

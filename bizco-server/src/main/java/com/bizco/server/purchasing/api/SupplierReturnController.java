package com.bizco.server.purchasing.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnSearchResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.application.SupplierReturnService;
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

/** Supplier returns against a POSTED goods receipt (DevelopmentPlan.md Week 15, DatabaseDesign.md
 *  &sect;17.8/17.9). One-shot create - no draft/post split, see {@code SupplierReturn}'s Javadoc. */
@RestController
@RequestMapping("/api/v1/supplier-returns")
public class SupplierReturnController {

    private final SupplierReturnService service;

    public SupplierReturnController(final SupplierReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchasing.read')")
    SupplierReturnSearchResponse search(@RequestParam(required = false) final UUID supplierId,
                                        @RequestParam(required = false) final UUID goodsReceiptId,
                                        @RequestParam(defaultValue = "0") final int page,
                                        @RequestParam(defaultValue = "20") final int size) {
        return service.search(supplierId, goodsReceiptId, page, size);
    }

    @GetMapping("/{supplierReturnId}")
    @PreAuthorize("hasAuthority('purchasing.read')")
    SupplierReturnResponse get(@PathVariable final UUID supplierReturnId) {
        return service.get(supplierReturnId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchasing.return.create')")
    ResponseEntity<SupplierReturnResponse> create(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                   @RequestBody final CreateSupplierReturnRequest request,
                                                   final Authentication authentication) {
        final IdempotentResult<SupplierReturnResponse> result = service.create(idempotencyKey, request, authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .location(URI.create("/api/v1/supplier-returns/" + result.response().supplierReturnId()))
                .body(result.response());
    }
}

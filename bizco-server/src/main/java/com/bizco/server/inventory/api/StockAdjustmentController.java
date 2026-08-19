package com.bizco.server.inventory.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentResponse;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentSearchResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockAdjustmentService;
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

/** Stock adjustment PENDING -&gt; APPROVED/REJECTED workflow (12.6/12.7, StateMachines.md &sect;17). */
@RestController
@RequestMapping("/api/v1/stock-adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentService service;

    public StockAdjustmentController(final StockAdjustmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory.read')")
    StockAdjustmentSearchResponse search(@RequestParam(required = false) final UUID productId,
                                         @RequestParam(required = false) final String status,
                                         @RequestParam(defaultValue = "0") final int page,
                                         @RequestParam(defaultValue = "20") final int size) {
        return service.search(productId, status, page, size);
    }

    @GetMapping("/{adjustmentId}")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockAdjustmentResponse get(@PathVariable final UUID adjustmentId) {
        return service.get(adjustmentId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inventory.adjustment.create')")
    ResponseEntity<StockAdjustmentResponse> create(@RequestBody final CreateStockAdjustmentRequest request,
                                                   final Authentication authentication) {
        final StockAdjustmentResponse created = service.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/stock-adjustments/" + created.stockAdjustmentId()))
                .body(created);
    }

    @PostMapping("/{adjustmentId}/approve")
    @PreAuthorize("hasAuthority('inventory.adjustment.approve')")
    ResponseEntity<StockAdjustmentResponse> approve(@PathVariable final UUID adjustmentId,
                                                    @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                    @RequestBody final DecideStockAdjustmentRequest request,
                                                    final Authentication authentication) {
        final IdempotentResult<StockAdjustmentResponse> result = service.approve(idempotencyKey, adjustmentId, request,
                authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{adjustmentId}/reject")
    @PreAuthorize("hasAuthority('inventory.adjustment.approve')")
    ResponseEntity<StockAdjustmentResponse> reject(@PathVariable final UUID adjustmentId,
                                                   @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                   @RequestBody final DecideStockAdjustmentRequest request,
                                                   final Authentication authentication) {
        final IdempotentResult<StockAdjustmentResponse> result = service.reject(idempotencyKey, adjustmentId, request,
                authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }
}

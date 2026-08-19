package com.bizco.server.purchasing.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CancelPurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderSearchResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.UpdatePurchaseOrderHeaderRequest;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.application.PurchaseOrderService;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Purchase Order DRAFT/APPROVED/SENT workflow (DevelopmentPlan.md Week 13, DatabaseDesign.md
 *  &sect;17.3). */
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(final PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchasing.read')")
    PurchaseOrderSearchResponse search(@RequestParam(required = false) final UUID supplierId,
                                       @RequestParam(required = false) final String status,
                                       @RequestParam(defaultValue = "0") final int page,
                                       @RequestParam(defaultValue = "20") final int size) {
        return service.search(supplierId, status, page, size);
    }

    @GetMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAuthority('purchasing.read')")
    PurchaseOrderDetailResponse get(@PathVariable final UUID purchaseOrderId) {
        return service.get(purchaseOrderId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    ResponseEntity<PurchaseOrderDetailResponse> create(@RequestBody final CreatePurchaseOrderRequest request,
                                                        final Authentication authentication) {
        final PurchaseOrderDetailResponse created = service.createDraft(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + created.purchaseOrderId()))
                .body(created);
    }

    @PutMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    PurchaseOrderDetailResponse updateHeader(@PathVariable final UUID purchaseOrderId,
                                             @RequestBody final UpdatePurchaseOrderHeaderRequest request) {
        return service.updateHeader(purchaseOrderId, request);
    }

    @PostMapping("/{purchaseOrderId}/items")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    PurchaseOrderDetailResponse addItem(@PathVariable final UUID purchaseOrderId,
                                        @RequestBody final AddPurchaseOrderItemRequest request) {
        return service.addItem(purchaseOrderId, request);
    }

    @DeleteMapping("/{purchaseOrderId}/items/{itemId}")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    PurchaseOrderDetailResponse removeItem(@PathVariable final UUID purchaseOrderId, @PathVariable final UUID itemId) {
        return service.removeItem(purchaseOrderId, itemId);
    }

    @PostMapping("/{purchaseOrderId}/approve")
    @PreAuthorize("hasAuthority('purchasing.po.approve')")
    ResponseEntity<PurchaseOrderDetailResponse> approve(@PathVariable final UUID purchaseOrderId,
                                                         @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                         @RequestBody final DecidePurchaseOrderRequest request,
                                                         final Authentication authentication) {
        final IdempotentResult<PurchaseOrderDetailResponse> result = service.approve(idempotencyKey, purchaseOrderId,
                request, authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{purchaseOrderId}/send")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    ResponseEntity<PurchaseOrderDetailResponse> send(@PathVariable final UUID purchaseOrderId,
                                                      @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                      @RequestBody final DecidePurchaseOrderRequest request,
                                                      final Authentication authentication) {
        final IdempotentResult<PurchaseOrderDetailResponse> result = service.send(idempotencyKey, purchaseOrderId,
                request, authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{purchaseOrderId}/cancel")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    PurchaseOrderDetailResponse cancel(@PathVariable final UUID purchaseOrderId,
                                       @RequestBody final CancelPurchaseOrderRequest request,
                                       final Authentication authentication) {
        return service.cancel(purchaseOrderId, request, authentication);
    }

    @PostMapping("/{purchaseOrderId}/close-remaining-balance")
    @PreAuthorize("hasAuthority('purchasing.po.create')")
    PurchaseOrderDetailResponse closeRemainingBalance(@PathVariable final UUID purchaseOrderId,
                                                       @RequestBody final CancelPurchaseOrderRequest request,
                                                       final Authentication authentication) {
        return service.closeRemainingBalance(purchaseOrderId, request, authentication);
    }
}

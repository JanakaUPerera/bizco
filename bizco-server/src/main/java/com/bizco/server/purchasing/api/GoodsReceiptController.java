package com.bizco.server.purchasing.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptOutstandingSearchResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSearchResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.ProductCostHistorySearchResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.purchasing.application.GoodsReceiptService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Goods Receipt DRAFT/POSTED workflow (DevelopmentPlan.md Week 14, DatabaseDesign.md &sect;17.5). */
@RestController
@RequestMapping("/api/v1/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(final GoodsReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchasing.read')")
    GoodsReceiptSearchResponse search(@RequestParam(required = false) final UUID supplierId,
                                      @RequestParam(required = false) final String status,
                                      @RequestParam(defaultValue = "0") final int page,
                                      @RequestParam(defaultValue = "20") final int size) {
        return service.search(supplierId, status, page, size);
    }

    @GetMapping("/{goodsReceiptId}")
    @PreAuthorize("hasAuthority('purchasing.read')")
    GoodsReceiptDetailResponse get(@PathVariable final UUID goodsReceiptId) {
        return service.get(goodsReceiptId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchasing.grn.create')")
    ResponseEntity<GoodsReceiptDetailResponse> create(@RequestBody final CreateGoodsReceiptRequest request,
                                                       final Authentication authentication) {
        final GoodsReceiptDetailResponse created = service.createDraft(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/goods-receipts/" + created.goodsReceiptId())).body(created);
    }

    @PostMapping("/{goodsReceiptId}/items")
    @PreAuthorize("hasAuthority('purchasing.grn.create')")
    GoodsReceiptDetailResponse addItem(@PathVariable final UUID goodsReceiptId,
                                       @RequestBody final AddGoodsReceiptItemRequest request) {
        return service.addItem(goodsReceiptId, request);
    }

    @DeleteMapping("/{goodsReceiptId}/items/{itemId}")
    @PreAuthorize("hasAuthority('purchasing.grn.create')")
    GoodsReceiptDetailResponse removeItem(@PathVariable final UUID goodsReceiptId, @PathVariable final UUID itemId) {
        return service.removeItem(goodsReceiptId, itemId);
    }

    @PostMapping("/{goodsReceiptId}/post")
    @PreAuthorize("hasAuthority('purchasing.grn.create')")
    ResponseEntity<GoodsReceiptDetailResponse> post(@PathVariable final UUID goodsReceiptId,
                                                     @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                     @RequestBody final PostGoodsReceiptRequest request,
                                                     final Authentication authentication) {
        final IdempotentResult<GoodsReceiptDetailResponse> result = service.post(idempotencyKey, goodsReceiptId, request,
                authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    /** Supplier statement / outstanding-balance view (DevelopmentPlan.md Week 15 task 15.5,
     *  DatabaseDesign.md &sect;18). */
    @GetMapping("/outstanding")
    @PreAuthorize("hasAuthority('purchasing.read')")
    GoodsReceiptOutstandingSearchResponse outstanding(@RequestParam(required = false) final UUID supplierId,
                                                        @RequestParam(defaultValue = "false") final boolean outstandingOnly,
                                                        @RequestParam(defaultValue = "0") final int page,
                                                        @RequestParam(defaultValue = "20") final int size) {
        return service.outstanding(supplierId, outstandingOnly, page, size);
    }

    @GetMapping("/cost-history")
    @PreAuthorize("hasAuthority('purchasing.cost_history.read')")
    ProductCostHistorySearchResponse costHistory(@RequestParam final UUID productId,
                                                  @RequestParam(defaultValue = "0") final int page,
                                                  @RequestParam(defaultValue = "20") final int size) {
        return service.costHistory(productId, page, size);
    }
}

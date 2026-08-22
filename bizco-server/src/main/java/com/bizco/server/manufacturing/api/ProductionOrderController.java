package com.bizco.server.manufacturing.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSearchResponse;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.manufacturing.application.ProductionService;
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

/** The Produce transaction's REST surface (DevelopmentPlan.md Week 21). */
@RestController
@RequestMapping("/api/v1/production-orders")
public class ProductionOrderController {

    private final ProductionService service;

    public ProductionOrderController(final ProductionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manufacturing.read')")
    ProductionOrderSearchResponse search(@RequestParam(required = false) final UUID bomId,
                                         @RequestParam(required = false) final UUID finishedVariantId,
                                         @RequestParam(defaultValue = "0") final int page,
                                         @RequestParam(defaultValue = "20") final int size) {
        return service.search(bomId, finishedVariantId, page, size);
    }

    @GetMapping("/{productionOrderId}")
    @PreAuthorize("hasAuthority('manufacturing.read')")
    ProductionOrderResponse get(@PathVariable final UUID productionOrderId) {
        return service.get(productionOrderId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manufacturing.produce')")
    ResponseEntity<ProductionOrderResponse> produce(
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
            @RequestBody final ProduceRequest request, final Authentication authentication) {
        final IdempotentResult<ProductionOrderResponse> result = service.produce(idempotencyKey, request, authentication);
        return ResponseEntity.ok().header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }
}

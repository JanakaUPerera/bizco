package com.bizco.server.inventory.api;

import com.bizco.common.dto.inventory.StockDtos.LowStockSummaryResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelSearchResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementSearchResponse;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.inventory.domain.StockReferenceType;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Inventory read side (DevelopmentPlan.md Week 12): stock levels, movement history, low stock. */
@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final StockQueryService service;

    public StockController(final StockQueryService service) {
        this.service = service;
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockLevelSearchResponse levels(@RequestParam(required = false) final String q,
                                    @RequestParam(defaultValue = "0") final int page,
                                    @RequestParam(defaultValue = "20") final int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/levels/{productId}")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockLevelResponse level(@PathVariable final UUID productId) {
        return service.levelFor(productId);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockLevelSearchResponse lowStock(@RequestParam(defaultValue = "0") final int page,
                                      @RequestParam(defaultValue = "20") final int size) {
        return service.lowStock(page, size);
    }

    @GetMapping("/low-stock/summary")
    @PreAuthorize("hasAuthority('inventory.read')")
    LowStockSummaryResponse lowStockSummary() {
        return service.lowStockSummary();
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockMovementSearchResponse movements(@RequestParam final UUID productId,
                                          @RequestParam(defaultValue = "0") final int page,
                                          @RequestParam(defaultValue = "20") final int size) {
        return service.movementHistory(productId, page, size);
    }

    @GetMapping("/movements/by-reference")
    @PreAuthorize("hasAuthority('inventory.read')")
    StockMovementSearchResponse movementsByReference(@RequestParam final String referenceType,
                                                      @RequestParam final UUID referenceId,
                                                      @RequestParam(defaultValue = "0") final int page,
                                                      @RequestParam(defaultValue = "20") final int size) {
        return service.movementsFor(StockReferenceType.valueOf(referenceType.trim().toUpperCase()), referenceId, page,
                size);
    }
}

package com.bizco.server.sales.api;

import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSearchResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.UpdateHeldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.server.sales.application.HeldSaleService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ApiContracts.md &sect;16. */
@RestController
@RequestMapping("/api/v1/held-sales")
public class HeldSaleController {

    private final HeldSaleService service;

    public HeldSaleController(final HeldSaleService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    ResponseEntity<HeldSaleDetailResponse> hold(@RequestBody final HoldSaleRequest request,
                                                final Authentication authentication) {
        final HeldSaleDetailResponse created = service.hold(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/held-sales/" + created.heldSaleId())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    HeldSaleSearchResponse search(@RequestParam(required = false) final String status,
                                  @RequestParam(required = false) final UUID cashierId) {
        return service.search(status, cashierId);
    }

    @GetMapping("/{heldSaleId}")
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    HeldSaleDetailResponse get(@PathVariable final UUID heldSaleId) {
        return service.get(heldSaleId);
    }

    @PostMapping("/{heldSaleId}/resume")
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    HeldSaleDetailResponse resume(@PathVariable final UUID heldSaleId, final Authentication authentication) {
        return service.resume(heldSaleId, authentication);
    }

    @PutMapping("/{heldSaleId}")
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    HeldSaleDetailResponse update(@PathVariable final UUID heldSaleId, @RequestBody final UpdateHeldSaleRequest request,
                                  final Authentication authentication) {
        return service.update(heldSaleId, request, authentication);
    }

    @PostMapping("/{heldSaleId}/cancel")
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    HeldSaleDetailResponse cancel(@PathVariable final UUID heldSaleId, final Authentication authentication) {
        return service.cancel(heldSaleId, authentication);
    }

    @PostMapping("/{heldSaleId}/convert")
    @PreAuthorize("hasAuthority('invoice.hold_bill')")
    InvoiceDetailResponse convert(@PathVariable final UUID heldSaleId, final Authentication authentication) {
        return service.convert(heldSaleId, authentication);
    }
}

package com.bizco.server.purchasing.api;

import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSearchResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierUpdateRequest;
import com.bizco.server.purchasing.application.SupplierService;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(final SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier.read')")
    SupplierSearchResponse search(@RequestParam(required = false) final String q,
                                  @RequestParam(required = false) final String status,
                                  @RequestParam(defaultValue = "0") final int page,
                                  @RequestParam(defaultValue = "20") final int size) {
        final Page<SupplierSummaryResponse> result = service.search(q, status, page, size);
        return new SupplierSearchResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('supplier.create')")
    ResponseEntity<SupplierDetailResponse> create(@RequestBody final SupplierCreateRequest request,
                                                  final Authentication authentication) {
        final SupplierDetailResponse created = service.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + created.supplierId())).body(created);
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('supplier.read')")
    SupplierDetailResponse get(@PathVariable final UUID supplierId) {
        return service.get(supplierId);
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('supplier.update')")
    SupplierDetailResponse update(@PathVariable final UUID supplierId,
                                  @RequestBody final SupplierUpdateRequest request,
                                  final Authentication authentication) {
        return service.update(supplierId, request, authentication);
    }

    @PostMapping("/{supplierId}/activate")
    @PreAuthorize("hasAuthority('supplier.update')")
    SupplierDetailResponse activate(@PathVariable final UUID supplierId, final Authentication authentication) {
        return service.activate(supplierId, authentication);
    }

    @PostMapping("/{supplierId}/deactivate")
    @PreAuthorize("hasAuthority('supplier.deactivate')")
    SupplierDetailResponse deactivate(@PathVariable final UUID supplierId, final Authentication authentication) {
        return service.deactivate(supplierId, authentication);
    }
}

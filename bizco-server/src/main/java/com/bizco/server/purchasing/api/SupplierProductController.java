package com.bizco.server.purchasing.api;

import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductSearchResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductUpdateRequest;
import com.bizco.server.purchasing.application.SupplierProductService;
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

/** Per-supplier product catalog (DevelopmentPlan.md Week 13). */
@RestController
@RequestMapping("/api/v1/supplier-products")
public class SupplierProductController {

    private final SupplierProductService service;

    public SupplierProductController(final SupplierProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchasing.read')")
    SupplierProductSearchResponse search(@RequestParam(required = false) final UUID supplierId,
                                         @RequestParam(required = false) final UUID productId,
                                         @RequestParam(defaultValue = "0") final int page,
                                         @RequestParam(defaultValue = "20") final int size) {
        return service.search(supplierId, productId, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchasing.supplier_product.create')")
    ResponseEntity<SupplierProductResponse> create(@RequestBody final SupplierProductCreateRequest request,
                                                    final Authentication authentication) {
        final SupplierProductResponse created = service.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/supplier-products/" + created.supplierProductId()))
                .body(created);
    }

    @PutMapping("/{supplierProductId}")
    @PreAuthorize("hasAuthority('purchasing.supplier_product.update')")
    SupplierProductResponse update(@PathVariable final UUID supplierProductId,
                                   @RequestBody final SupplierProductUpdateRequest request,
                                   final Authentication authentication) {
        return service.update(supplierProductId, request, authentication);
    }
}

package com.bizco.server.catalog.api;

import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantGenerateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import com.bizco.server.catalog.application.VariantService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Phase 6 Week 17 (DevelopmentPlan.md Week 17). Separate from {@link CatalogController} —
 *  matches {@link com.bizco.server.catalog.application.VariantService}'s own service-boundary
 *  split. */
@RestController
public class VariantController {

    private final VariantService service;

    public VariantController(final VariantService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products/{productId}/variants")
    @PreAuthorize("hasAuthority('product.variant.read')")
    List<VariantResponse> variants(@PathVariable final UUID productId) {
        return service.listVariants(productId);
    }

    @PostMapping("/api/v1/products/{productId}/variants")
    @PreAuthorize("hasAuthority('product.variant.create')")
    ResponseEntity<VariantResponse> createVariant(@PathVariable final UUID productId,
                                                  @RequestBody final VariantCreateRequest request,
                                                  final Authentication authentication) {
        final VariantResponse created = service.createVariant(productId, request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/products/" + productId + "/variants/"
                + created.productVariantId())).body(created);
    }

    @PutMapping("/api/v1/products/{productId}/variants/{variantId}")
    @PreAuthorize("hasAuthority('product.variant.update')")
    VariantResponse updateVariant(@PathVariable final UUID productId, @PathVariable final UUID variantId,
                                  @RequestBody final VariantUpdateRequest request,
                                  final Authentication authentication) {
        return service.updateVariant(variantId, request, authentication);
    }

    @PostMapping("/api/v1/products/{productId}/variants/generate")
    @PreAuthorize("hasAuthority('product.variant.create')")
    List<VariantResponse> generateVariants(@PathVariable final UUID productId,
                                           @RequestBody final VariantGenerateRequest request,
                                           final Authentication authentication) {
        return service.generateVariants(productId, request, authentication);
    }
}

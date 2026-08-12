package com.bizco.server.catalog.api;

import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductPriceResolutionResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSearchResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceSearchResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.UomResponse;
import com.bizco.server.catalog.application.BarcodeImageService;
import com.bizco.server.catalog.application.CatalogService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
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
public class CatalogController {

    private final CatalogService service;
    private final BarcodeImageService barcodeImageService;

    public CatalogController(final CatalogService service, final BarcodeImageService barcodeImageService) {
        this.service = service;
        this.barcodeImageService = barcodeImageService;
    }

    @GetMapping("/api/v1/product-categories")
    @PreAuthorize("hasAuthority('product.category.read')")
    List<CategoryResponse> categories() {
        return service.categories();
    }

    @PostMapping("/api/v1/product-categories")
    @PreAuthorize("hasAuthority('product.category.create')")
    ResponseEntity<CategoryResponse> createCategory(@RequestBody final CategoryCreateRequest request,
                                                    final Authentication authentication) {
        final CategoryResponse created = service.createCategory(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/product-categories/" + created.categoryId()))
                .body(created);
    }

    @PutMapping("/api/v1/product-categories/{categoryId}")
    @PreAuthorize("hasAuthority('product.category.update')")
    CategoryResponse updateCategory(@PathVariable final Long categoryId,
                                    @RequestBody final CategoryUpdateRequest request,
                                    final Authentication authentication) {
        return service.updateCategory(categoryId, request, authentication);
    }

    @GetMapping("/api/v1/uom")
    @PreAuthorize("hasAuthority('product.read')")
    List<UomResponse> uom() {
        return service.uom();
    }

    @GetMapping("/api/v1/products")
    @PreAuthorize("hasAuthority('product.read')")
    ProductSearchResponse products(@RequestParam(required = false) final String q,
                                   @RequestParam(required = false) final Long categoryId,
                                   @RequestParam(required = false) final String type,
                                   @RequestParam(required = false) final Boolean active,
                                   @RequestParam(defaultValue = "0") final int page,
                                   @RequestParam(defaultValue = "20") final int size,
                                   final Authentication authentication) {
        final Page<ProductSummaryResponse> result = service.products(q, categoryId, type, active, page, size,
                hasPermission(authentication, "product.view_cost"));
        return new ProductSearchResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/api/v1/products")
    @PreAuthorize("hasAuthority('product.create')")
    ResponseEntity<ProductDetailResponse> createProduct(@RequestBody final ProductCreateRequest request,
                                                        final Authentication authentication) {
        final ProductDetailResponse created = service.createProduct(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.productId())).body(created);
    }

    @GetMapping("/api/v1/products/barcode/{barcode}")
    @PreAuthorize("hasAuthority('product.read')")
    ProductSummaryResponse barcode(@PathVariable final String barcode, final Authentication authentication) {
        return service.barcode(barcode, hasPermission(authentication, "product.view_cost"));
    }

    @GetMapping("/api/v1/products/{productId}")
    @PreAuthorize("hasAuthority('product.read')")
    ProductDetailResponse getProduct(@PathVariable final UUID productId, final Authentication authentication) {
        return service.getProduct(productId, hasPermission(authentication, "product.view_cost"));
    }

    @PutMapping("/api/v1/products/{productId}")
    @PreAuthorize("hasAuthority('product.update')")
    ProductDetailResponse updateProduct(@PathVariable final UUID productId,
                                        @RequestBody final ProductUpdateRequest request,
                                        final Authentication authentication) {
        return service.updateProduct(productId, request, authentication);
    }

    @PostMapping("/api/v1/products/{productId}/activate")
    @PreAuthorize("hasAuthority('product.update')")
    ProductDetailResponse activateProduct(@PathVariable final UUID productId, final Authentication authentication) {
        return service.activateProduct(productId, authentication);
    }

    @PostMapping("/api/v1/products/{productId}/deactivate")
    @PreAuthorize("hasAuthority('product.delete')")
    ProductDetailResponse deactivateProduct(@PathVariable final UUID productId, final Authentication authentication) {
        return service.deactivateProduct(productId, authentication);
    }

    @GetMapping("/api/v1/products/{productId}/price")
    @PreAuthorize("hasAuthority('product.read')")
    ProductPriceResolutionResponse resolvePrice(@PathVariable final UUID productId,
                                                @RequestParam(defaultValue = "RETAIL") final String tier) {
        return service.resolvePrice(productId, tier);
    }

    @GetMapping(value = "/api/v1/barcodes/code128/{value}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('product.read')")
    byte[] code128(@PathVariable final String value,
                   @RequestParam(defaultValue = "320") final int width,
                   @RequestParam(defaultValue = "100") final int height) {
        return barcodeImageService.code128Png(value, width, height);
    }

    @GetMapping("/api/v1/services")
    @PreAuthorize("hasAuthority('service.read')")
    ServiceSearchResponse services(@RequestParam(required = false) final String q,
                                   @RequestParam(required = false) final Boolean active,
                                   @RequestParam(defaultValue = "0") final int page,
                                   @RequestParam(defaultValue = "20") final int size) {
        final Page<ServiceResponse> result = service.services(q, active, page, size);
        return new ServiceSearchResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/api/v1/services")
    @PreAuthorize("hasAuthority('service.create')")
    ResponseEntity<ServiceResponse> createService(@RequestBody final ServiceCreateRequest request,
                                                  final Authentication authentication) {
        final ServiceResponse created = service.createService(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/services/" + created.serviceId())).body(created);
    }

    @GetMapping("/api/v1/services/{serviceId}")
    @PreAuthorize("hasAuthority('service.read')")
    ServiceResponse getService(@PathVariable final UUID serviceId) {
        return service.getService(serviceId);
    }

    @PutMapping("/api/v1/services/{serviceId}")
    @PreAuthorize("hasAuthority('service.update')")
    ServiceResponse updateService(@PathVariable final UUID serviceId,
                                  @RequestBody final ServiceUpdateRequest request,
                                  final Authentication authentication) {
        return service.updateService(serviceId, request, authentication);
    }

    @PostMapping("/api/v1/services/{serviceId}/activate")
    @PreAuthorize("hasAuthority('service.update')")
    ServiceResponse activateService(@PathVariable final UUID serviceId, final Authentication authentication) {
        return service.activateService(serviceId, authentication);
    }

    @PostMapping("/api/v1/services/{serviceId}/deactivate")
    @PreAuthorize("hasAuthority('service.update')")
    ServiceResponse deactivateService(@PathVariable final UUID serviceId, final Authentication authentication) {
        return service.deactivateService(serviceId, authentication);
    }

    private boolean hasPermission(final Authentication authentication, final String permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}

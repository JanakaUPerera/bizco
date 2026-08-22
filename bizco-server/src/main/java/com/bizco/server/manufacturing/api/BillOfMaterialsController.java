package com.bizco.server.manufacturing.api;

import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSearchResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.UpdateBomRequest;
import com.bizco.server.manufacturing.application.BillOfMaterialsService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bill of Materials CRUD (DevelopmentPlan.md Week 20, DatabaseDesign.md &sect;57). */
@RestController
@RequestMapping("/api/v1/boms")
public class BillOfMaterialsController {

    private final BillOfMaterialsService service;

    public BillOfMaterialsController(final BillOfMaterialsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manufacturing.read')")
    BomSearchResponse search(@RequestParam(defaultValue = "false") final boolean activeOnly,
                             @RequestParam(defaultValue = "0") final int page,
                             @RequestParam(defaultValue = "20") final int size) {
        return service.search(activeOnly, page, size);
    }

    @GetMapping("/{bomId}")
    @PreAuthorize("hasAuthority('manufacturing.read')")
    BomDetailResponse get(@PathVariable final UUID bomId) {
        return service.get(bomId);
    }

    @GetMapping("/by-variant/{finishedVariantId}")
    @PreAuthorize("hasAuthority('manufacturing.read')")
    BomDetailResponse byVariant(@PathVariable final UUID finishedVariantId) {
        return service.byFinishedVariant(finishedVariantId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manufacturing.bom.manage')")
    ResponseEntity<BomDetailResponse> create(@RequestBody final CreateBomRequest request,
                                             final Authentication authentication) {
        final BomDetailResponse created = service.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/boms/" + created.bomId())).body(created);
    }

    @PutMapping("/{bomId}")
    @PreAuthorize("hasAuthority('manufacturing.bom.manage')")
    BomDetailResponse update(@PathVariable final UUID bomId, @RequestBody final UpdateBomRequest request,
                             final Authentication authentication) {
        return service.update(bomId, request, authentication);
    }

    @PostMapping("/{bomId}/items")
    @PreAuthorize("hasAuthority('manufacturing.bom.manage')")
    BomDetailResponse addItem(@PathVariable final UUID bomId, @RequestBody final BomItemRequest request,
                              final Authentication authentication) {
        return service.addItem(bomId, request, authentication);
    }

    @PutMapping("/{bomId}/items/{bomItemId}")
    @PreAuthorize("hasAuthority('manufacturing.bom.manage')")
    BomDetailResponse updateItem(@PathVariable final UUID bomId, @PathVariable final UUID bomItemId,
                                 @RequestBody final BomItemRequest request) {
        return service.updateItem(bomId, bomItemId, request);
    }

    @DeleteMapping("/{bomId}/items/{bomItemId}")
    @PreAuthorize("hasAuthority('manufacturing.bom.manage')")
    BomDetailResponse removeItem(@PathVariable final UUID bomId, @PathVariable final UUID bomItemId) {
        return service.removeItem(bomId, bomItemId);
    }
}

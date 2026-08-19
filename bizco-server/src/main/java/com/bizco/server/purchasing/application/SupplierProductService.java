package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductSearchResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductUpdateRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierProduct;
import com.bizco.server.purchasing.infrastructure.SupplierProductRepository;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-supplier product catalog (DatabaseDesign.md &sect;17.2, SRS.md &sect;6.9.4, DevelopmentPlan.md
 *  Week 13). Lets a Purchase Order line pre-fill from a supplier's known price/lead-time. */
@Service
public class SupplierProductService {

    private final SupplierProductRepository repository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public SupplierProductService(final SupplierProductRepository repository, final SupplierRepository supplierRepository,
                                  final ProductRepository productRepository, final AuditService auditService,
                                  final UserRepository userRepository) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SupplierProductSearchResponse search(final UUID supplierId, final UUID productId, final int page,
                                                final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<SupplierProduct> result = repository.search(supplierId, productId, pageable);
        return new SupplierProductSearchResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SupplierProductResponse create(final SupplierProductCreateRequest request, final Authentication authentication) {
        requireSupplier(request.supplierId());
        requireProduct(request.productId());
        if (request.preferred()) {
            demoteExistingPreferred(request.productId(), null);
        }
        try {
            final SupplierProduct saved = repository.saveAndFlush(new SupplierProduct(request.supplierId(),
                    request.productId(), request.supplierSku(), money(request.purchasePrice()),
                    quantity(request.minOrderQty()), request.leadTimeDays(), request.preferred()));
            auditService.record("SUPPLIER_PRODUCT", saved.getId().toString(), "SUPPLIER_PRODUCT_CREATED",
                    actor(authentication), java.util.Map.of("supplierId", request.supplierId().toString(),
                            "productId", request.productId().toString()));
            return toResponse(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_PRODUCT_DUPLICATE, HttpStatus.CONFLICT,
                    "This supplier already has a catalog entry for this product");
        }
    }

    @Transactional
    public SupplierProductResponse update(final UUID id, final SupplierProductUpdateRequest request,
                                          final Authentication authentication) {
        final SupplierProduct entry = load(id);
        if (entry.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "The supplier product entry was modified by another user");
        }
        if (request.preferred()) {
            demoteExistingPreferred(entry.getProductId(), entry.getId());
        }
        entry.update(request.supplierSku(), money(request.purchasePrice()), quantity(request.minOrderQty()),
                request.leadTimeDays(), request.preferred());
        repository.flush();
        auditService.record("SUPPLIER_PRODUCT", id.toString(), "SUPPLIER_PRODUCT_UPDATED", actor(authentication),
                java.util.Map.of("productId", entry.getProductId().toString()));
        return toResponse(entry);
    }

    /**
     * At most one preferred supplier per product ({@code uq_supplier_products_preferred}) is a
     * business rule this method enforces up front - marking a new entry preferred demotes whichever
     * one previously held it, rather than making the caller un-check the old one first. The DB
     * unique index stays as a race-condition backstop for two concurrent requests, not the primary
     * mechanism (a caught {@link DataIntegrityViolationException} from that index would otherwise be
     * indistinguishable from the unrelated supplier+product uniqueness violation caught below).
     */
    private void demoteExistingPreferred(final UUID productId, final UUID excludeId) {
        for (final SupplierProduct existing : repository.findByProductId(productId)) {
            if (existing.isPreferred() && !existing.getId().equals(excludeId)) {
                existing.update(existing.getSupplierSku(), existing.getPurchasePrice(), existing.getMinOrderQty(),
                        existing.getLeadTimeDays(), false);
            }
        }
        repository.flush();
    }

    private void requireSupplier(final UUID supplierId) {
        if (supplierId == null || !supplierRepository.existsById(supplierId)) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_NOT_FOUND, HttpStatus.NOT_FOUND, "Supplier was not found");
        }
    }

    private void requireProduct(final UUID productId) {
        if (productId == null || !productRepository.existsById(productId)) {
            throw new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product was not found");
        }
    }

    private SupplierProduct load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.SUPPLIER_PRODUCT_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Supplier product entry was not found"));
    }

    private SupplierProductResponse toResponse(final SupplierProduct sp) {
        final Supplier supplier = supplierRepository.findById(sp.getSupplierId()).orElse(null);
        final Product product = productRepository.findById(sp.getProductId()).orElse(null);
        return new SupplierProductResponse(sp.getId(), sp.getSupplierId(), supplier == null ? null : supplier.getName(),
                sp.getProductId(), product == null ? null : product.getSku(), product == null ? null : product.getName(),
                sp.getSupplierSku(), sp.getPurchasePrice(), sp.getMinOrderQty(), sp.getLeadTimeDays(),
                sp.getLastPurchasePrice(), sp.isPreferred(), sp.getCreatedAt(), sp.getUpdatedAt(), sp.getVersion());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private BigDecimal money(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal quantity(final BigDecimal value) { return value == null ? BigDecimal.ONE : value; }
}

package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSearchResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSummaryResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.UpdateHeldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.sales.domain.DiscountType;
import com.bizco.server.sales.domain.HeldSale;
import com.bizco.server.sales.domain.HeldSaleItem;
import com.bizco.server.sales.domain.HeldSaleStatus;
import com.bizco.server.sales.infrastructure.HeldSaleRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Held-bill cart snapshot service (DevelopmentPlan.md Week 8 task 8.1, StateMachines.md
 * &sect;7, ApiContracts.md &sect;16). See {@link HeldSale}'s Javadoc for the two-stage
 * hold/convert/post contract.
 *
 * <p>A hold never posts a stock movement (StateMachines.md &sect;7.3 "no SALE stock movement") -
 * it only has to prove the reservation is coverable right now, by checking demand against
 * {@link StockPostingService#requireAvailable} under the same product lock every stock-affecting
 * path takes. That lock is what makes STK-CON-002 hold-vs-sale safe: a concurrent
 * {@code PostSaleService.doPost} for the same product either committed its SALE movement before
 * this hold locks the product (so {@code v_available_stock} already reflects it) or is blocked
 * behind this hold's lock and will see this hold's now-committed {@code held_sale_items} row
 * (via {@code v_reserved_stock}) once it proceeds.
 */
@Service
public class HeldSaleService {

    private final HeldSaleRepository heldSaleRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StockPostingService stockPostingService;
    private final long expiryMinutes;

    public HeldSaleService(final HeldSaleRepository heldSaleRepository, final ProductRepository productRepository,
                           final ProductVariantRepository variantRepository,
                           final CustomerRepository customerRepository,
                           final DocumentSequenceRepository documentSequenceRepository,
                           final InvoiceService invoiceService, final UserRepository userRepository,
                           final AuditService auditService, final StockPostingService stockPostingService,
                           @Value("${bizco.sales.held-sale-expiry-minutes:120}") final long expiryMinutes) {
        this.heldSaleRepository = heldSaleRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.customerRepository = customerRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.stockPostingService = stockPostingService;
        this.expiryMinutes = expiryMinutes;
    }

    @Transactional
    public HeldSaleDetailResponse hold(final HoldSaleRequest request, final Authentication authentication) {
        final UUID cashierId = actor(authentication);
        if (request.customerId() != null) {
            requireCustomer(request.customerId());
        }
        final List<HeldSaleItem> items = buildItems(request.items());
        requireReservationAvailable(demandByVariant(items), Map.of());
        final long next = documentSequenceRepository.nextDailyValue("HLD", LocalDate.now(), "HLD", 4);
        final String heldNumber = documentSequenceRepository.formatDaily("HLD", LocalDate.now(), next, 4);
        final HeldSale heldSale = new HeldSale(heldNumber, request.customerId(), cashierId, request.notes(),
                expiresAt());
        items.forEach(heldSale::addItem);
        final HeldSale saved = heldSaleRepository.save(heldSale);
        auditService.record("HELD_SALE", saved.getId().toString(), "HELD_SALE_CREATED", cashierId,
                Map.of("heldNumber", heldNumber, "itemCount", String.valueOf(items.size())));
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public HeldSaleSearchResponse search(final String status, final UUID cashierId) {
        final HeldSaleStatus statusFilter = status == null || status.isBlank() ? null
                : HeldSaleStatus.valueOf(status.trim().toUpperCase());
        return new HeldSaleSearchResponse(heldSaleRepository.search(statusFilter, cashierId).stream()
                .map(this::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public HeldSaleDetailResponse get(final UUID heldSaleId) {
        return toDetail(load(heldSaleId));
    }

    @Transactional
    public HeldSaleDetailResponse resume(final UUID heldSaleId, final Authentication authentication) {
        final HeldSale heldSale = load(heldSaleId);
        assertActive(heldSale);
        heldSale.resume();
        auditService.record("HELD_SALE", heldSale.getId().toString(), "HELD_SALE_RESUMED", actor(authentication),
                Map.of("heldNumber", heldSale.getHeldNumber()));
        return toDetail(heldSale);
    }

    @Transactional
    public HeldSaleDetailResponse update(final UUID heldSaleId, final UpdateHeldSaleRequest request,
                                         final Authentication authentication) {
        final HeldSale heldSale = load(heldSaleId);
        assertVersion(heldSale, request.version());
        assertActive(heldSale);
        if (request.customerId() != null) {
            requireCustomer(request.customerId());
        }
        final List<HeldSaleItem> newItems = buildItems(request.items());
        // The new demand replaces this held sale's own existing reservation, so that existing
        // reservation (not anyone else's) is what gets credited back before checking availability.
        requireReservationAvailable(demandByVariant(newItems), demandByVariant(heldSale.getItems()));
        heldSale.replaceItems(newItems, request.customerId(), request.notes(), expiresAt());
        auditService.record("HELD_SALE", heldSale.getId().toString(), "HELD_SALE_UPDATED", actor(authentication),
                Map.of("heldNumber", heldSale.getHeldNumber()));
        return toDetail(heldSale);
    }

    @Transactional
    public HeldSaleDetailResponse cancel(final UUID heldSaleId, final Authentication authentication) {
        final HeldSale heldSale = load(heldSaleId);
        assertActive(heldSale);
        heldSale.cancel();
        auditService.record("HELD_SALE", heldSale.getId().toString(), "HELD_SALE_RELEASED", actor(authentication),
                Map.of("heldNumber", heldSale.getHeldNumber(), "reason", "CANCELLED"));
        return toDetail(heldSale);
    }

    /**
     * ApiContracts.md &sect;16.6 stage 1: builds a real DRAFT invoice from the held cart and links
     * it. The held sale stays HELD/RESUMED until that invoice's own POST commits - see
     * {@code PostSaleService}, which looks the held sale up by {@code convertedInvoiceId} and
     * calls {@link HeldSale#markConverted()} atomically in the same posting transaction.
     */
    @Transactional
    public InvoiceDetailResponse convert(final UUID heldSaleId, final Authentication authentication) {
        final HeldSale heldSale = load(heldSaleId);
        assertActive(heldSale);
        // Already converted to a draft in an earlier call (e.g. client retry) - reuse it instead of
        // creating a second, now-orphaned draft invoice for the same held sale.
        if (heldSale.getConvertedInvoiceId() != null) {
            return invoiceService.get(heldSale.getConvertedInvoiceId());
        }
        if (heldSale.getItems().isEmpty()) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "Held sale has no items to convert");
        }
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                heldSale.getCustomerId(), heldSale.getNotes()), authentication);
        for (final HeldSaleItem item : heldSale.getItems()) {
            invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("PRODUCT", item.getProductId(),
                    item.getProductVariantId(), null, null, item.getQuantity(), item.getUnitPriceSnapshot(), null,
                    new DiscountRequest(item.getDiscountType().name(), item.getDiscountValue())));
        }
        heldSale.linkConvertedInvoice(draft.invoiceId());
        auditService.record("HELD_SALE", heldSale.getId().toString(), "HELD_SALE_CONVERTED", actor(authentication),
                Map.of("heldNumber", heldSale.getHeldNumber(), "invoiceId", draft.invoiceId().toString()));
        return invoiceService.get(draft.invoiceId());
    }

    /** Aggregates item quantities per variant, since a cart can list the same variant on more than
     *  one line (StateMachines.md 7.3 "reservation quantities are available"). */
    private Map<UUID, BigDecimal> demandByVariant(final List<HeldSaleItem> items) {
        return items.stream().collect(Collectors.groupingBy(HeldSaleItem::getProductVariantId,
                Collectors.reducing(BigDecimal.ZERO, HeldSaleItem::getQuantity, BigDecimal::add)));
    }

    /**
     * Locks every demanded variant in stable order (STK-CON-003) and checks that {@code newDemand}
     * is coverable once {@code ownExistingReservation} (this same held sale's current items, if any)
     * is credited back - so resizing an already-active hold is checked against stock actually free
     * for it, not double-counting its own prior reservation as unavailable.
     */
    private void requireReservationAvailable(final Map<UUID, BigDecimal> newDemand,
                                             final Map<UUID, BigDecimal> ownExistingReservation) {
        if (newDemand.isEmpty()) {
            return;
        }
        stockPostingService.lockVariants(newDemand.keySet());
        newDemand.forEach((productVariantId, quantity) -> {
            final BigDecimal creditBack = ownExistingReservation.getOrDefault(productVariantId, BigDecimal.ZERO);
            final BigDecimal netDemand = quantity.subtract(creditBack).max(BigDecimal.ZERO);
            stockPostingService.requireAvailable(productVariantId, netDemand);
        });
    }

    private List<HeldSaleItem> buildItems(final List<HeldSaleItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "A held sale must have at least one item");
        }
        return requests.stream().map(this::buildItem).toList();
    }

    private HeldSaleItem buildItem(final HeldSaleItemRequest request) {
        if (request.productId() == null) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "productId is required for every held sale item");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IdentityException(ApiErrorCode.DOMAIN_RULE_REJECTED, HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero");
        }
        final Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product was not found"));
        // Phase 6 Week 19: a caller (POS) may name the exact variant it resolved (e.g. via the
        // barcode/variant picker); otherwise fall back to the product's default variant.
        final ProductVariant variant = request.productVariantId() != null
                ? requireVariantOfProduct(request.productVariantId(), product.getId())
                : variantRepository.findByProductIdAndDefaultVariantTrue(product.getId())
                        .orElseThrow(() -> new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                                "Product variant was not found"));
        final BigDecimal unitPrice = request.unitPrice() != null ? request.unitPrice() : variant.getSellingPrice();
        final DiscountRequest discount = request.discount() == null ? DiscountRequest.NONE : request.discount();
        final DiscountType discountType = discount.type() == null || discount.type().isBlank()
                ? DiscountType.NONE : DiscountType.valueOf(discount.type().trim().toUpperCase());
        return new HeldSaleItem(product.getId(), variant.getId(), request.quantity(), unitPrice, discountType,
                discount.value() == null ? BigDecimal.ZERO : discount.value());
    }

    /** Phase 6 Week 19: mirrors {@code InvoiceService.requireVariantOfProduct} - rejects a
     *  client-supplied variant id that doesn't actually belong to the given product. */
    private ProductVariant requireVariantOfProduct(final UUID productVariantId, final UUID productId) {
        final ProductVariant variant = variantRepository.findById(productVariantId)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product variant was not found"));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new IdentityException(ApiErrorCode.VARIANT_PRODUCT_MISMATCH, HttpStatus.CONFLICT,
                    "Product variant does not belong to the given product");
        }
        return variant;
    }

    private Instant expiresAt() {
        return Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);
    }

    private void requireCustomer(final UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND, "Customer was not found");
        }
    }

    private void assertActive(final HeldSale heldSale) {
        if (heldSale.getStatus() != HeldSaleStatus.HELD && heldSale.getStatus() != HeldSaleStatus.RESUMED) {
            throw new IdentityException(ApiErrorCode.HELD_SALE_NOT_ACTIVE, HttpStatus.CONFLICT,
                    "Held sale is not active");
        }
    }

    private void assertVersion(final HeldSale heldSale, final long expectedVersion) {
        if (heldSale.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Held sale was modified by another user");
        }
    }

    private HeldSale load(final UUID heldSaleId) {
        return heldSaleRepository.findById(heldSaleId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.HELD_SALE_NOT_FOUND, HttpStatus.NOT_FOUND, "Held sale was not found"));
    }

    private HeldSaleDetailResponse toDetail(final HeldSale heldSale) {
        return new HeldSaleDetailResponse(heldSale.getId(), heldSale.getHeldNumber(), heldSale.getCustomerId(),
                heldSale.getCashierId(), heldSale.getStatus().name(), heldSale.getHeldAt(), heldSale.getExpiresAt(),
                heldSale.getConvertedInvoiceId(), heldSale.getNotes(), heldSale.getVersion(),
                heldSale.getItems().stream().map(this::toItemResponse).toList());
    }

    private HeldSaleItemResponse toItemResponse(final HeldSaleItem item) {
        final Product product = productRepository.findById(item.getProductId()).orElse(null);
        final BigDecimal discountAmount = item.getDiscountType() == DiscountType.PERCENTAGE
                ? item.getUnitPriceSnapshot().multiply(item.getQuantity())
                        .multiply(item.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                : item.getDiscountType() == DiscountType.FIXED ? item.getDiscountValue() : BigDecimal.ZERO;
        final BigDecimal estimatedLineTotal = item.getUnitPriceSnapshot().multiply(item.getQuantity())
                .subtract(discountAmount).max(BigDecimal.ZERO);
        return new HeldSaleItemResponse(item.getId(), item.getProductId(), item.getProductVariantId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                item.getQuantity(), item.getUnitPriceSnapshot(), item.getDiscountType().name(),
                item.getDiscountValue(), estimatedLineTotal);
    }

    private HeldSaleSummaryResponse toSummary(final HeldSale heldSale) {
        final BigDecimal estimatedTotal = heldSale.getItems().stream()
                .map(item -> item.getUnitPriceSnapshot().multiply(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HeldSaleSummaryResponse(heldSale.getId(), heldSale.getHeldNumber(), heldSale.getCustomerId(),
                heldSale.getCashierId(), heldSale.getStatus().name(), heldSale.getHeldAt(), heldSale.getExpiresAt(),
                heldSale.getItems().size(), estimatedTotal);
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

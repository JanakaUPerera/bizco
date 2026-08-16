package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceLineResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSearchResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.domain.ServiceDefinition;
import com.bizco.server.catalog.domain.TaxCategory;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.catalog.infrastructure.ServiceDefinitionRepository;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.domain.CustomerCategory;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.domain.DiscountType;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceLine;
import com.bizco.server.sales.domain.InvoicePricingCalculator;
import com.bizco.server.sales.domain.InvoicePricingCalculator.DiscountInput;
import com.bizco.server.sales.domain.InvoicePricingCalculator.InvoiceCalculation;
import com.bizco.server.sales.domain.InvoicePricingCalculator.LineInput;
import com.bizco.server.sales.domain.InvoicePricingCalculator.LineResult;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.domain.InvoiceType;
import com.bizco.server.sales.domain.LineType;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import com.bizco.server.system.repository.TaxConfigurationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft-side invoice application service (DevelopmentPlan.md Week 6): create/read/update draft
 * invoices and their lines, and recalculate the authoritative preview. Posting (DRAFT -&gt;
 * POSTED) is {@code PostSaleService}, a later Week 7 addition - this service never allocates an
 * official invoice number or produces stock/finance/audit effects, matching the DRAFT invariant
 * that drafts have no financial effect (StateMachines.md &sect;9.4).
 */
@Service
public class InvoiceService {

    private static final InvoicePricingCalculator CALCULATOR = new InvoicePricingCalculator();

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final ServiceDefinitionRepository serviceDefinitionRepository;
    private final CustomerRepository customerRepository;
    private final TaxConfigurationRepository taxConfigurationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public InvoiceService(final InvoiceRepository invoiceRepository, final ProductRepository productRepository,
                          final ServiceDefinitionRepository serviceDefinitionRepository,
                          final CustomerRepository customerRepository,
                          final TaxConfigurationRepository taxConfigurationRepository,
                          final UserRepository userRepository, final AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
        this.customerRepository = customerRepository;
        this.taxConfigurationRepository = taxConfigurationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceSummaryResponse createDraft(final CreateDraftInvoiceRequest request, final Authentication authentication) {
        final LocalDate invoiceDate = request.invoiceDate() == null ? LocalDate.now() : request.invoiceDate();
        if (request.customerId() != null) {
            requireCustomer(request.customerId());
        }
        final UUID cashierId = actor(authentication);
        final Invoice invoice = new Invoice(invoiceDate, request.dueDate(), invoiceType(request.invoiceType()),
                request.customerId(), cashierId, request.notes());
        final Invoice saved = invoiceRepository.save(invoice);
        auditService.record("INVOICE", saved.getId().toString(), "INVOICE_DRAFT_CREATED", cashierId,
                Map.of("invoiceType", saved.getInvoiceType().name()));
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse get(final UUID invoiceId) {
        return toDetail(load(invoiceId));
    }

    @Transactional(readOnly = true)
    public InvoiceSearchResponse search(final String number, final UUID customerId, final String status,
                                        final String type, final LocalDate fromDate, final LocalDate toDate,
                                        final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<Invoice> result = invoiceRepository.search(blankToNull(number), customerId,
                status == null || status.isBlank() ? null : InvoiceStatus.valueOf(status.trim().toUpperCase()),
                type == null || type.isBlank() ? null : InvoiceType.valueOf(type.trim().toUpperCase()),
                fromDate, toDate, pageable);
        return new InvoiceSearchResponse(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public InvoiceDetailResponse updateHeader(final UUID invoiceId, final UpdateInvoiceHeaderRequest request) {
        final Invoice invoice = load(invoiceId);
        assertVersion(invoice, request.version());
        if (request.customerId() != null) {
            requireCustomer(request.customerId());
        }
        assertDraft(invoice);
        final DiscountRequest discount = request.discount() == null ? DiscountRequest.NONE : request.discount();
        invoice.updateHeader(request.invoiceDate(), request.dueDate(), invoiceType(request.invoiceType()),
                request.customerId(), discountType(discount.type()), nonNegative(discount.value()), request.notes());
        recalculate(invoice);
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_DRAFT_UPDATED", null, Map.of());
        return toDetail(invoice);
    }

    @Transactional
    public InvoiceDetailResponse addLine(final UUID invoiceId, final AddInvoiceLineRequest request) {
        final Invoice invoice = load(invoiceId);
        assertDraft(invoice);
        final InvoiceLine line = buildLine(invoice, request);
        invoice.addLine(line);
        recalculate(invoice);
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_LINE_ADDED", null,
                Map.of("lineType", line.getLineType().name()));
        return toDetail(invoice);
    }

    @Transactional
    public InvoiceDetailResponse updateLine(final UUID invoiceId, final UUID lineId,
                                            final UpdateInvoiceLineRequest request) {
        final Invoice invoice = load(invoiceId);
        assertVersion(invoice, request.version());
        assertDraft(invoice);
        final InvoiceLine line = requireLine(invoice, lineId);
        if (request.quantity() != null) {
            validateQuantity(request.quantity());
            line.changeQuantity(request.quantity());
        }
        final DiscountRequest discount = request.discount() == null ? DiscountRequest.NONE : request.discount();
        line.applyDiscount(discountType(discount.type()), nonNegative(discount.value()));
        recalculate(invoice);
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_LINE_UPDATED", null,
                Map.of("invoiceLineId", lineId.toString()));
        return toDetail(invoice);
    }

    @Transactional
    public InvoiceDetailResponse deleteLine(final UUID invoiceId, final UUID lineId) {
        final Invoice invoice = load(invoiceId);
        assertDraft(invoice);
        requireLine(invoice, lineId);
        invoice.removeLine(lineId);
        recalculate(invoice);
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_LINE_REMOVED", null,
                Map.of("invoiceLineId", lineId.toString()));
        return toDetail(invoice);
    }

    @Transactional
    public InvoiceDetailResponse preview(final UUID invoiceId) {
        final Invoice invoice = load(invoiceId);
        recalculate(invoice);
        return toDetail(invoice);
    }

    private InvoiceLine buildLine(final Invoice invoice, final AddInvoiceLineRequest request) {
        final LineType lineType = lineType(request.lineType());
        final BigDecimal quantity = request.quantity();
        validateQuantity(quantity);
        return switch (lineType) {
            case PRODUCT -> buildProductLine(invoice, request, quantity);
            case SERVICE -> buildServiceLine(request, quantity);
            case CUSTOM -> buildCustomLine(request, quantity);
        };
    }

    private InvoiceLine buildProductLine(final Invoice invoice, final AddInvoiceLineRequest request, final BigDecimal quantity) {
        if (request.productId() == null) {
            throw fieldError("productId", "REQUIRED", "Product is required for a PRODUCT line.");
        }
        final Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Product was not found"));
        if (!product.isActive()) {
            throw new IdentityException(ApiErrorCode.PRODUCT_INACTIVE, HttpStatus.CONFLICT, "Product is not active");
        }
        final BigDecimal unitPrice = request.requestedUnitPrice() != null
                ? request.requestedUnitPrice() : resolvePrice(invoice.getCustomerId(), product);
        return new InvoiceLine(LineType.PRODUCT, product.getId(), null, product.getSku(), product.getName(),
                product.getUom() == null ? null : product.getUom().getCode(), quantity, unitPrice,
                product.getTaxCategory(), BigDecimal.ZERO);
    }

    private InvoiceLine buildServiceLine(final AddInvoiceLineRequest request, final BigDecimal quantity) {
        if (request.serviceId() == null) {
            throw fieldError("serviceId", "REQUIRED", "Service is required for a SERVICE line.");
        }
        final ServiceDefinition service = serviceDefinitionRepository.findById(request.serviceId())
                .orElseThrow(() -> new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Service was not found"));
        if (!service.isActive()) {
            throw new IdentityException(ApiErrorCode.SERVICE_NOT_FOUND, HttpStatus.CONFLICT, "Service is not active");
        }
        final BigDecimal unitPrice = request.requestedUnitPrice() != null ? request.requestedUnitPrice() : service.getBasePrice();
        return new InvoiceLine(LineType.SERVICE, null, service.getId(), null, service.getName(), null, quantity,
                unitPrice, TaxCategory.STANDARD, BigDecimal.ZERO);
    }

    private InvoiceLine buildCustomLine(final AddInvoiceLineRequest request, final BigDecimal quantity) {
        if (request.description() == null || request.description().isBlank()) {
            throw fieldError("description", "REQUIRED", "Description is required for a CUSTOM line.");
        }
        if (request.requestedUnitPrice() == null) {
            throw fieldError("requestedUnitPrice", "REQUIRED", "Unit price is required for a CUSTOM line.");
        }
        final TaxCategory taxCategory = request.taxCategory() == null || request.taxCategory().isBlank()
                ? TaxCategory.STANDARD : TaxCategory.valueOf(request.taxCategory().trim().toUpperCase());
        return new InvoiceLine(LineType.CUSTOM, null, null, null, request.description().trim(), null, quantity,
                request.requestedUnitPrice(), taxCategory, BigDecimal.ZERO);
    }

    /** MVP.md Section 4.3: customer category selects the default pricing tier; WHOLESALE only applies if the product has a wholesale price. */
    private BigDecimal resolvePrice(final UUID customerId, final Product product) {
        final boolean wholesaleEligible = customerId != null && product.getWholesalePrice() != null
                && customerRepository.findById(customerId).map(Customer::getCategory)
                        .map(category -> category == CustomerCategory.WHOLESALE).orElse(false);
        return wholesaleEligible ? product.getWholesalePrice() : product.getSellingPrice();
    }

    /** Recomputes every line and header total via {@link InvoicePricingCalculator} and persists the snapshot. */
    private void recalculate(final Invoice invoice) {
        final BigDecimal vatRate = effectiveVatRate();
        final List<InvoiceLine> lines = invoice.getLines();
        final List<LineInput> inputs = lines.stream()
                .map(line -> new LineInput(line.getLineNumber(), line.getQuantity(), line.getUnitPrice(),
                        new DiscountInput(line.getDiscountType(), line.getDiscountValue()),
                        line.getTaxCategorySnapshot(), vatRate))
                .toList();
        if (inputs.isEmpty()) {
            invoice.applyCalculatedTotals(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
            invoice.setVatRateSnapshot(vatRate);
            invoiceRepository.flush();
            return;
        }
        final InvoiceCalculation calculation = CALCULATOR.calculate(inputs,
                new DiscountInput(invoice.getDiscountType(), invoice.getDiscountValue()));
        for (final LineResult result : calculation.lines()) {
            final InvoiceLine line = lines.get(result.lineNumber() - 1);
            line.applyCalculation(result.lineDiscountAmount().add(result.invoiceDiscountAllocated()),
                    result.taxableAmount(), result.vatAmount(), result.lineTotalInclVat());
        }
        invoice.applyCalculatedTotals(calculation.subtotal(), calculation.discountAmount(), calculation.taxableAmount(),
                calculation.vatAmount(), calculation.totalAmount());
        invoice.setVatRateSnapshot(vatRate);
        // Forces Hibernate to issue the UPDATE (and bump @Version) now rather than at transaction
        // commit, so the version returned to the caller in this same method call is current -
        // otherwise a client's very next request would submit a version already stale.
        invoiceRepository.flush();
    }

    private BigDecimal effectiveVatRate() {
        return taxConfigurationRepository.findById((short) 1)
                .filter(config -> config.isVatEnabled())
                .map(config -> config.getVatRate())
                .orElse(BigDecimal.ZERO);
    }

    private Invoice load(final UUID invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
    }

    private void requireCustomer(final UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND, "Customer was not found");
        }
    }

    private void assertVersion(final Invoice invoice, final long expectedVersion) {
        if (invoice.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "Invoice was modified by another user");
        }
    }

    private InvoiceLine requireLine(final Invoice invoice, final UUID lineId) {
        try {
            return invoice.line(lineId);
        } catch (final IllegalArgumentException exception) {
            throw new IdentityException(ApiErrorCode.INVOICE_LINE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Invoice line was not found on this invoice");
        }
    }

    /** Translates the domain's defensive {@code assertDraft()} guard into the API's 409 contract. */
    private void assertDraft(final Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_DRAFT, HttpStatus.CONFLICT,
                    "Invoice is not DRAFT and cannot be modified");
        }
    }

    private void validateQuantity(final BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw fieldError("quantity", "INVALID", "Quantity must be greater than zero.");
        }
    }

    private ApiValidationException fieldError(final String field, final String code, final String message) {
        return new ApiValidationException(List.of(new FieldError(field, code, message)));
    }

    private InvoiceType invoiceType(final String value) {
        return value == null || value.isBlank() ? InvoiceType.SALES : InvoiceType.valueOf(value.trim().toUpperCase());
    }

    private LineType lineType(final String value) {
        if (value == null || value.isBlank()) {
            throw fieldError("lineType", "REQUIRED", "Line type is required.");
        }
        return LineType.valueOf(value.trim().toUpperCase());
    }

    private DiscountType discountType(final String value) {
        return value == null || value.isBlank() ? DiscountType.NONE : DiscountType.valueOf(value.trim().toUpperCase());
    }

    private BigDecimal nonNegative(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private InvoiceSummaryResponse toSummary(final Invoice invoice) {
        return new InvoiceSummaryResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                invoice.getStatus().name(), invoice.getCustomerId(), invoice.getTotalAmount(), invoice.getVersion());
    }

    private InvoiceDetailResponse toDetail(final Invoice invoice) {
        final List<InvoiceLineResponse> lines = new ArrayList<>();
        for (final InvoiceLine line : invoice.getLines()) {
            lines.add(new InvoiceLineResponse(line.getId(), line.getLineNumber(), line.getLineType().name(),
                    line.getProductId(), line.getServiceId(), line.getSkuSnapshot(), line.getDescriptionSnapshot(),
                    line.getUomSnapshot(), line.getQuantity(), line.getUnitPrice(), line.getDiscountType().name(),
                    line.getDiscountValue(), line.getDiscountAmount(), line.getTaxCategorySnapshot().name(),
                    line.getVatRateSnapshot(), line.getTaxableAmount(), line.getVatAmount(), line.getLineTotalInclVat()));
        }
        return new InvoiceDetailResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                invoice.getDueDate(), invoice.getInvoiceType().name(), invoice.getStatus().name(),
                invoice.getCustomerId(), invoice.getCashierId(), invoice.getSubtotal(), invoice.getDiscountType().name(),
                invoice.getDiscountValue(), invoice.getDiscountAmount(), invoice.getTaxableAmount(),
                invoice.getVatRateSnapshot(), invoice.getVatAmount(), invoice.getTotalAmount(), invoice.getNotes(),
                invoice.getCreatedAt(), invoice.getPostedAt(), invoice.getVersion(), lines);
    }
}

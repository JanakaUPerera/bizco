package com.bizco.server.purchasing.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierUpdateRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.domain.Supplier;
import com.bizco.server.purchasing.domain.SupplierStatus;
import com.bizco.server.purchasing.infrastructure.SupplierRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private static final Pattern PHONE = Pattern.compile("^(?:0\\d{9}|\\+94\\d{9})$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SupplierRepository repository;
    private final SupplierCodeGenerator codeGenerator;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public SupplierService(final SupplierRepository repository, final SupplierCodeGenerator codeGenerator,
                           final AuditService auditService, final UserRepository userRepository) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierSummaryResponse> search(final String q, final String status, final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return repository.search(blankToNull(q), nullableStatus(status), pageable).map(this::summary);
    }

    @Transactional
    public SupplierDetailResponse create(final SupplierCreateRequest request, final Authentication authentication) {
        validate(request.supplierCode(), request.name(), request.phone(), request.email(), request.openingBalance(),
                false);
        try {
            final String code = request.supplierCode() == null || request.supplierCode().isBlank()
                    ? codeGenerator.nextCode() : request.supplierCode();
            final Supplier saved = repository.saveAndFlush(new Supplier(code, request.name(), request.contactPerson(),
                    request.address(), request.phone(), request.email(), request.tinNumber(), request.paymentTerms(),
                    money(request.openingBalance())));
            auditService.record("SUPPLIER", saved.getId().toString(), "SUPPLIER_CREATED", actor(authentication),
                    Map.of("supplierCode", saved.getSupplierCode()));
            return detail(saved);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_CODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Supplier code already exists.");
        }
    }

    @Transactional(readOnly = true)
    public SupplierDetailResponse get(final UUID id) {
        return detail(load(id));
    }

    @Transactional
    public SupplierDetailResponse update(final UUID id, final SupplierUpdateRequest request,
                                         final Authentication authentication) {
        validate(request.supplierCode(), request.name(), request.phone(), request.email(), request.openingBalance(),
                true);
        final Supplier supplier = load(id);
        if (supplier.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "The supplier was modified by another user.");
        }
        try {
            supplier.update(request.supplierCode(), request.name(), request.contactPerson(), request.address(),
                    request.phone(), request.email(), request.tinNumber(), request.paymentTerms(),
                    money(request.openingBalance()), status(request.status()));
            repository.flush();
            auditService.record("SUPPLIER", id.toString(), "SUPPLIER_UPDATED", actor(authentication),
                    Map.of("supplierCode", supplier.getSupplierCode()));
            return detail(supplier);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.SUPPLIER_CODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Supplier code already exists.");
        }
    }

    @Transactional
    public SupplierDetailResponse activate(final UUID id, final Authentication authentication) {
        final Supplier supplier = load(id);
        supplier.activate();
        auditService.record("SUPPLIER", id.toString(), "SUPPLIER_ACTIVATED", actor(authentication),
                Map.of("supplierCode", supplier.getSupplierCode()));
        return detail(supplier);
    }

    @Transactional
    public SupplierDetailResponse deactivate(final UUID id, final Authentication authentication) {
        final Supplier supplier = load(id);
        supplier.deactivate();
        auditService.record("SUPPLIER", id.toString(), "SUPPLIER_DEACTIVATED", actor(authentication),
                Map.of("supplierCode", supplier.getSupplierCode()));
        return detail(supplier);
    }

    private void validate(final String code, final String name, final String phone, final String email,
                          final BigDecimal openingBalance, final boolean codeRequired) {
        final List<FieldError> errors = new ArrayList<>();
        if (codeRequired && (code == null || code.isBlank())) {
            errors.add(new FieldError("supplierCode", "REQUIRED", "Supplier code is required."));
        }
        if (name == null || name.isBlank()) {
            errors.add(new FieldError("name", "REQUIRED", "Name is required."));
        }
        if (phone != null && !phone.isBlank() && !PHONE.matcher(phone).matches()) {
            errors.add(new FieldError("phone", "INVALID_PHONE", "Phone number format is invalid."));
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            errors.add(new FieldError("email", "INVALID_EMAIL", "Email format is invalid."));
        }
        if (openingBalance != null && openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new FieldError("openingBalance", "NEGATIVE", "Opening balance must be zero or greater."));
        }
        if (!errors.isEmpty()) throw new ApiValidationException(errors);
    }

    private Supplier load(final UUID id) {
        return repository.findById(id).orElseThrow(() -> new IdentityException(ApiErrorCode.SUPPLIER_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Supplier was not found."));
    }

    private SupplierStatus status(final String value) {
        return value == null || value.isBlank() ? SupplierStatus.ACTIVE : SupplierStatus.valueOf(value.toUpperCase());
    }

    private SupplierStatus nullableStatus(final String value) {
        return value == null || value.isBlank() ? null : status(value);
    }

    private SupplierSummaryResponse summary(final Supplier s) {
        return new SupplierSummaryResponse(s.getId(), s.getSupplierCode(), s.getName(), s.getContactPerson(),
                s.getPhone(), s.getEmail(), s.getOpeningBalance(), s.getStatus().name(), s.getVersion());
    }

    private SupplierDetailResponse detail(final Supplier s) {
        return new SupplierDetailResponse(s.getId(), s.getSupplierCode(), s.getName(), s.getContactPerson(),
                s.getAddress(), s.getPhone(), s.getEmail(), s.getTinNumber(), s.getPaymentTerms(),
                s.getOpeningBalance(), s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private BigDecimal money(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String blankToNull(final String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

package com.bizco.server.customer.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.FieldError;
import com.bizco.common.dto.customer.CustomerDtos.AgingBuckets;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreditSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerUpdateRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.customer.domain.Customer;
import com.bizco.server.customer.domain.CustomerCategory;
import com.bizco.server.customer.domain.CustomerCreditPolicy;
import com.bizco.server.customer.domain.CustomerStatus;
import com.bizco.server.customer.domain.CreditEligibility;
import com.bizco.server.customer.infrastructure.CustomerCodeGenerator;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.customer.infrastructure.PiiEncryptionService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import java.math.BigDecimal;
import java.time.Instant;
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
public class CustomerService {

    private static final Pattern PHONE = Pattern.compile("^(?:0\\d{9}|\\+94\\d{9})$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CustomerRepository repository;
    private final CustomerCodeGenerator codeGenerator;
    private final PiiEncryptionService encryptionService;
    private final CustomerCreditQueryPort creditQueryPort;
    private final CustomerAnonymizationEligibilityPort anonymizationEligibilityPort;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final CustomerCreditPolicy creditPolicy = new CustomerCreditPolicy();

    public CustomerService(final CustomerRepository repository, final CustomerCodeGenerator codeGenerator,
                           final PiiEncryptionService encryptionService,
                           final CustomerCreditQueryPort creditQueryPort,
                           final CustomerAnonymizationEligibilityPort anonymizationEligibilityPort,
                           final AuditService auditService, final UserRepository userRepository) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.encryptionService = encryptionService;
        this.creditQueryPort = creditQueryPort;
        this.anonymizationEligibilityPort = anonymizationEligibilityPort;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional
    public CustomerDetailResponse create(final CustomerCreateRequest request, final Authentication authentication) {
        validate(request.name(), request.phone(), request.email(), request.category(), request.creditLimit(), false);
        try {
            final Customer customer = new Customer(codeGenerator.nextCode(), request.name(), request.phone(),
                    request.email(), request.addressLine1(), request.addressLine2(), request.city(),
                    encryptionService.encrypt(request.nicNumber()), encryptionService.encrypt(request.brNumber()),
                    category(request.category()), money(request.creditLimit()), request.consentMarketing(),
                    request.consentDataSharing());
            final Customer saved = repository.saveAndFlush(customer);
            auditService.record("CUSTOMER", saved.getId().toString(), "CUSTOMER_CREATED", actor(authentication),
                    Map.of("customerCode", saved.getCustomerCode()));
            return detail(saved, true, false, authentication);
        } catch (final DataIntegrityViolationException ex) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_CODE_DUPLICATE, HttpStatus.CONFLICT,
                    "Customer code already exists.");
        }
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummaryResponse> search(final String q, final String category, final String status,
                                                final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return repository.search(blankToNull(q), nullableCategory(category), nullableStatus(status), pageable)
                .map(this::summary);
    }

    @Transactional
    public CustomerDetailResponse get(final UUID customerId, final boolean revealPii,
                                      final Authentication authentication) {
        final Customer customer = load(customerId);
        return detail(customer, revealPii, revealPii, authentication);
    }

    @Transactional
    public CustomerDetailResponse update(final UUID customerId, final CustomerUpdateRequest request,
                                         final Authentication authentication) {
        validate(request.name(), request.phone(), request.email(), request.category(), request.creditLimit(), true);
        final Customer customer = load(customerId);
        assertVersion(customer, request.version());
        customer.updateProfile(request.name(), request.phone(), request.email(), request.addressLine1(),
                request.addressLine2(), request.city(), encryptionService.encrypt(request.nicNumber()),
                encryptionService.encrypt(request.brNumber()), category(request.category()), money(request.creditLimit()),
                request.consentMarketing(), request.consentDataSharing());
        final Customer saved = repository.save(customer);
        auditService.record("CUSTOMER", saved.getId().toString(), "CUSTOMER_UPDATED", actor(authentication),
                Map.of("customerCode", saved.getCustomerCode()));
        return detail(saved, true, false, authentication);
    }

    @Transactional
    public CustomerDetailResponse block(final UUID customerId, final Authentication authentication) {
        final Customer customer = load(customerId);
        customer.block();
        auditService.record("CUSTOMER", customer.getId().toString(), "CUSTOMER_BLOCKED", actor(authentication),
                Map.of("customerCode", customer.getCustomerCode()));
        return detail(customer, true, false, authentication);
    }

    @Transactional
    public CustomerDetailResponse activate(final UUID customerId, final Authentication authentication) {
        final Customer customer = load(customerId);
        customer.activate();
        auditService.record("CUSTOMER", customer.getId().toString(), "CUSTOMER_ACTIVATED", actor(authentication),
                Map.of("customerCode", customer.getCustomerCode()));
        return detail(customer, true, false, authentication);
    }

    @Transactional(readOnly = true)
    public CustomerCreditSummaryResponse creditSummary(final UUID customerId) {
        final Customer customer = load(customerId);
        final CustomerReceivableSnapshot snapshot = creditQueryPort.snapshotFor(customerId);
        final CreditEligibility eligibility = creditPolicy.evaluate(customer, snapshot.outstandingReceivable(),
                snapshot.oldestOutstandingDays(), BigDecimal.ZERO);
        return new CustomerCreditSummaryResponse(customerId, customer.getCreditLimit(), snapshot.outstandingReceivable(),
                customer.getCreditLimit().subtract(snapshot.outstandingReceivable()), snapshot.oldestOutstandingDays(),
                eligibility.name(), new AgingBuckets(snapshot.days0To30(), snapshot.days31To60(),
                snapshot.days61To90(), snapshot.days91Plus()));
    }

    @Transactional
    public CustomerDetailResponse anonymize(final UUID customerId, final Authentication authentication) {
        final Customer customer = load(customerId);
        final List<String> blockers = anonymizationEligibilityPort.blockersFor(customerId);
        if (!blockers.isEmpty()) {
            throw new IdentityException(ApiErrorCode.CUSTOMER_ANONYMIZATION_BLOCKED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Customer cannot be anonymized while dependent records block anonymization.");
        }
        customer.anonymize(Instant.now());
        auditService.record("CUSTOMER", customer.getId().toString(), "CUSTOMER_ANONYMIZED", actor(authentication),
                Map.of("customerCode", customer.getCustomerCode()));
        return detail(customer, true, false, authentication);
    }

    private Customer load(final UUID customerId) {
        return repository.findById(customerId)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer was not found."));
    }

    private void validate(final String name, final String phone, final String email, final String category,
                          final BigDecimal creditLimit, final boolean allowAnonymizedPhone) {
        final List<FieldError> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(new FieldError("name", "REQUIRED", "Name is required."));
        }
        if (phone == null || phone.isBlank()) {
            errors.add(new FieldError("phone", "REQUIRED", "Phone is required."));
        } else if (!allowAnonymizedPhone && !PHONE.matcher(phone).matches()) {
            errors.add(new FieldError("phone", "INVALID_PHONE", "Phone number format is invalid."));
        } else if (allowAnonymizedPhone && !"0000000000".equals(phone) && !PHONE.matcher(phone).matches()) {
            errors.add(new FieldError("phone", "INVALID_PHONE", "Phone number format is invalid."));
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            errors.add(new FieldError("email", "INVALID_EMAIL", "Email format is invalid."));
        }
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new FieldError("creditLimit", "NEGATIVE", "Credit limit must be zero or greater."));
        }
        try {
            category(category);
        } catch (final IllegalArgumentException ex) {
            errors.add(new FieldError("category", "INVALID", "Customer category is invalid."));
        }
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
    }

    private void assertVersion(final Customer customer, final long expectedVersion) {
        if (customer.getVersion() != expectedVersion) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "The customer was modified by another user.");
        }
    }

    private CustomerSummaryResponse summary(final Customer customer) {
        return new CustomerSummaryResponse(customer.getId(), customer.getCustomerCode(), customer.getName(),
                customer.getPhone(), customer.getCategory().name(), customer.getStatus().name(),
                customer.getCreditLimit(), customer.isAnonymized(), customer.getVersion());
    }

    private CustomerDetailResponse detail(final Customer customer, final boolean includePii,
                                          final boolean auditPiiReveal, final Authentication authentication) {
        String nic = null;
        String br = null;
        if (includePii) {
            nic = encryptionService.decrypt(customer.getNicCiphertext());
            br = encryptionService.decrypt(customer.getBrCiphertext());
            if (auditPiiReveal && (nic != null || br != null)) {
                auditService.record("CUSTOMER", customer.getId().toString(), "CUSTOMER_PII_REVEALED",
                        actor(authentication), Map.of("customerCode", customer.getCustomerCode()));
            }
        } else {
            nic = mask(customer.getNicCiphertext());
            br = mask(customer.getBrCiphertext());
        }
        return new CustomerDetailResponse(customer.getId(), customer.getCustomerCode(), customer.getName(),
                customer.getPhone(), customer.getEmail(), customer.getAddressLine1(), customer.getAddressLine2(),
                customer.getCity(), nic, br, includePii, customer.getCategory().name(), customer.getStatus().name(),
                customer.getCreditLimit(), customer.isConsentMarketing(), customer.isConsentDataSharing(),
                customer.getConsentDate(), customer.isAnonymized(), customer.getAnonymizedAt(),
                customer.getCreatedAt(), customer.getUpdatedAt(), customer.getVersion());
    }

    private String mask(final byte[] ciphertext) {
        return ciphertext == null ? null : "******";
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private CustomerCategory category(final String category) {
        if (category == null || category.isBlank()) {
            return CustomerCategory.RETAIL;
        }
        return CustomerCategory.valueOf(category.trim().toUpperCase());
    }

    private CustomerCategory nullableCategory(final String category) {
        return category == null || category.isBlank() ? null : category(category);
    }

    private CustomerStatus nullableStatus(final String status) {
        return status == null || status.isBlank() ? null : CustomerStatus.valueOf(status.trim().toUpperCase());
    }

    private BigDecimal money(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

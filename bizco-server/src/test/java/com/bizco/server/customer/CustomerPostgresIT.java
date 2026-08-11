package com.bizco.server.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerUpdateRequest;
import com.bizco.server.audit.repository.AuditLogRepository;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.customer.infrastructure.CustomerRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CustomerPostgresIT extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cusCrudSearchPiiAndCreditSummaryWorkAgainstPostgres() {
        final CustomerDetailResponse created = customerService.create(request("Nimal Traders", "0771234567"),
                auth("manager", "customer.create", "customer.read", "customer.view_pii"));

        assertThat(created.customerCode()).startsWith("CUS-");
        assertThat(customerService.search("Nimal", null, "ACTIVE", 0, 10).getContent())
                .extracting("customerCode").contains(created.customerCode());
        assertThat(customerService.search("077123", null, null, 0, 10).getContent()).isNotEmpty();
        assertThat(customerService.search(created.customerCode(), null, null, 0, 10).getContent()).isNotEmpty();
        assertThat(created.creditLimit()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        final String rawStorage = jdbc.queryForObject("""
                select encode(nic_ciphertext, 'escape') || ':' || encode(br_ciphertext, 'escape')
                from customers
                where customer_id = ?
                """, String.class, created.customerId());
        assertThat(rawStorage).doesNotContain("951234567V").doesNotContain("PV12345");

        final CustomerDetailResponse masked = customerService.get(created.customerId(), false, auth("cashier", "customer.read"));
        assertEquals("******", masked.nicNumber());
        assertEquals("******", masked.brNumber());

        final CustomerDetailResponse full = customerService.get(created.customerId(), true,
                auth("manager", "customer.read", "customer.view_pii"));
        assertEquals("951234567V", full.nicNumber());
        assertEquals("PV12345", full.brNumber());
        assertThat(auditLogRepository.findAll()).anyMatch(audit -> "CUSTOMER_PII_REVEALED".equals(audit.getActionCode())
                && !audit.getDetails().toString().contains("951234567V"));
        assertEquals("NORMAL", customerService.creditSummary(created.customerId()).eligibility());
    }

    @Test
    void validationOptimisticLockAndAnonymizationAreEnforced() {
        assertThrows(ApiValidationException.class, () -> customerService.create(
                new CustomerCreateRequest("Bad Credit", "0771234567", null, null, null, null,
                        null, null, "RETAIL", BigDecimal.valueOf(-1), false, false),
                auth("manager", "customer.create")));

        final CustomerDetailResponse created = customerService.create(request("Stale Customer", "0777654321"),
                auth("manager", "customer.create"));
        final CustomerUpdateRequest stale = new CustomerUpdateRequest("Stale Customer", "0777654321", null,
                null, null, null, null, null, "RETAIL", BigDecimal.ZERO, false, false, created.version() + 1);

        final IdentityException conflict = assertThrows(IdentityException.class,
                () -> customerService.update(created.customerId(), stale, auth("manager", "customer.update")));
        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, conflict.getCode());

        final CustomerDetailResponse anonymized = customerService.anonymize(created.customerId(),
                auth("admin", "customer.anonymize"));
        assertThat(anonymized.name()).startsWith("Deleted Customer CUS-");
        assertThat(anonymized.nicNumber()).isNull();
        assertThat(customerRepository.findById(created.customerId()).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void customerCodesAreGeneratedThroughSequenceTable() {
        final CustomerDetailResponse first = customerService.create(request("Sequence One " + UUID.randomUUID(), "0711111111"),
                auth("manager", "customer.create"));
        final CustomerDetailResponse second = customerService.create(request("Sequence Two " + UUID.randomUUID(), "0722222222"),
                auth("manager", "customer.create"));

        assertThat(first.customerCode()).isNotEqualTo(second.customerCode());
        assertThat(jdbc.queryForObject("select next_value from document_sequences where sequence_key = 'CUSTOMER'",
                Long.class)).isGreaterThan(2);
    }

    private CustomerCreateRequest request(final String name, final String phone) {
        return new CustomerCreateRequest(name, phone, "customer@example.lk", "12 Main Street", null, "Colombo",
                "951234567V", "PV12345", "RETAIL", BigDecimal.valueOf(5000), false, false);
    }

    private UsernamePasswordAuthenticationToken auth(final String username, final String... permissions) {
        return new UsernamePasswordAuthenticationToken(username, null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierUpdateRequest;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.SupplierService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SupplierWeek5PostgresIT extends PostgresIntegrationTest {

    @Autowired
    private SupplierService supplierService;

    @Test
    void supplierCreationUniquenessValidationStatusAndOptimisticLockingWork() {
        final String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        final var supplier = supplierService.create(new SupplierCreateRequest("SUP-T-" + suffix, "Acme Supplies",
                "Nimal", "Colombo", "0771234567", "acme@example.com", "TIN1", "Net 30",
                BigDecimal.ZERO), auth("supplier.create"));

        assertThat(supplier.status()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> supplierService.create(new SupplierCreateRequest("SUP-T-" + suffix, "Duplicate",
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.SUPPLIER_CODE_DUPLICATE);

        assertThatThrownBy(() -> supplierService.create(new SupplierCreateRequest("SUP-BAD-" + suffix, "Bad",
                null, null, null, null, null, null, new BigDecimal("-1.00")), auth("supplier.create")))
                .isInstanceOf(ApiValidationException.class);

        assertThat(supplierService.deactivate(supplier.supplierId(), auth("supplier.deactivate")).status())
                .isEqualTo("INACTIVE");
        assertThat(supplierService.activate(supplier.supplierId(), auth("supplier.update")).status())
                .isEqualTo("ACTIVE");

        assertThatThrownBy(() -> supplierService.update(supplier.supplierId(), new SupplierUpdateRequest(
                supplier.supplierCode(), supplier.name(), supplier.contactPerson(), supplier.address(), supplier.phone(),
                supplier.email(), supplier.tinNumber(), supplier.paymentTerms(), supplier.openingBalance(), "ACTIVE",
                supplier.version() + 100), auth("supplier.update")))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void generatedSupplierCodesUseSequence() {
        final var first = supplierService.create(new SupplierCreateRequest(null, "Generated A", null, null,
                null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
        final var second = supplierService.create(new SupplierCreateRequest(null, "Generated B", null, null,
                null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));

        assertThat(first.supplierCode()).startsWith("SUP-");
        assertThat(second.supplierCode()).startsWith("SUP-");
        assertThat(second.supplierCode()).isNotEqualTo(first.supplierCode());
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

package com.bizco.server.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.SalesApprovalService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class SalesApprovalServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private SalesApprovalService salesApprovalService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void salesApprovalRequiresValidApproverCredentialsAndPermission() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final User manager = createUser("manager_" + token(), "MANAGER");
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));

        final SalesApprovalResponse approval = salesApprovalService.requestApproval(draft.invoiceId(),
                new SalesApprovalRequest("DISCOUNT_10_25", null, new BigDecimal("15"), "Regular customer",
                        manager.getUsername(), "Correct1!"), auth(cashier));

        assertThat(approval.approvalType()).isEqualTo("DISCOUNT_10_25");
        assertThat(approval.approvedBy().userId()).isEqualTo(manager.getId());
        assertThat(jdbc.queryForObject("select reason from sales_approvals where sales_approval_id = ?",
                String.class, approval.approvalId())).isEqualTo("Regular customer");

        assertThatThrownBy(() -> salesApprovalService.requestApproval(draft.invoiceId(),
                new SalesApprovalRequest("DISCOUNT_10_25", null, new BigDecimal("15"), "wrong password",
                        manager.getUsername(), "WrongPassword1!"), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);

        assertThatThrownBy(() -> salesApprovalService.requestApproval(draft.invoiceId(),
                new SalesApprovalRequest("DISCOUNT_10_25", null, new BigDecimal("15"), "cashier cannot approve",
                        cashier.getUsername(), "Correct1!"), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.AUTH_PERMISSION_DENIED);
    }

    @Test
    void approvalForUnknownInvoiceLineIsRejected() {
        final User cashier = createUser("cashier_" + token(), "CASHIER");
        final User manager = createUser("manager_" + token(), "MANAGER");
        final InvoiceSummaryResponse draft = invoiceService.createDraft(
                new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES", null, null), auth(cashier));

        assertThatThrownBy(() -> salesApprovalService.requestApproval(draft.invoiceId(),
                new SalesApprovalRequest("PRICE_OVERRIDE", UUID.randomUUID(), new BigDecimal("100"), "reason",
                        manager.getUsername(), "Correct1!"), auth(cashier)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.INVOICE_LINE_NOT_FOUND);
    }

    private User createUser(final String username, final String roleCode) {
        final Role role = roleRepository.findByCode(roleCode).orElseThrow();
        final User user = new User(username, username, passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("invoice.create")));
    }
}

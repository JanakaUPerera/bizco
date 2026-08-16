package com.bizco.server.sales.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.server.config.CorrelationIdFilter;
import com.bizco.server.config.SecurityConfig;
import com.bizco.server.identity.controller.ApiExceptionHandler;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.sales.application.InvoiceService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, ApiExceptionHandler.class})
class InvoiceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InvoiceService invoiceService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserSessionRepository sessionRepository;
    @MockitoBean
    private PermissionService permissionService;

    @Test
    void createDraftAllowedForUsersWithInvoiceCreatePermission() throws Exception {
        authenticate(Set.of("invoice.create"));
        final UUID invoiceId = UUID.randomUUID();
        when(invoiceService.createDraft(any(), any())).thenReturn(
                new InvoiceSummaryResponse(invoiceId, null, LocalDate.now(), "DRAFT", null, java.math.BigDecimal.ZERO, 0));

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer good-token")
                        .contentType("application/json")
                        .content("{\"invoiceType\":\"SALES\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createDraftDeniedWithoutPermission() throws Exception {
        authenticate(Set.of("invoice.read"));

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer good-token")
                        .contentType("application/json")
                        .content("{\"invoiceType\":\"SALES\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void getInvoiceRequiresReadPermission() throws Exception {
        authenticate(Set.of("invoice.create"));

        mockMvc.perform(get("/api/v1/invoices/" + UUID.randomUUID()).header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    private void authenticate(final Set<String> permissions) throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = new Role("CASHIER", "Cashier", Map.of(), true);
        final User user = new User("cashier", "Cashier", "hash", role);
        setId(user, userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1", Instant.now().plusSeconds(60));
        when(tokenService.hash("good-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(session));
        when(permissionService.effectivePermissions(userId)).thenReturn(permissions);
    }

    private void setId(final User user, final UUID id) throws Exception {
        final Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}

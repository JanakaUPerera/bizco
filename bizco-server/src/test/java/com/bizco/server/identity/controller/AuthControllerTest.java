package com.bizco.server.identity.controller;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.identity.AuthResponses.AuthenticatedUser;
import com.bizco.common.dto.identity.AuthResponses.CurrentSessionResponse;
import com.bizco.common.dto.identity.AuthResponses.LoginData;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.server.config.CorrelationIdFilter;
import com.bizco.server.identity.service.AuthService;
import com.bizco.server.identity.service.IdentityException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @Test
    void loginReturnsSessionTokenWithoutLeakingPassword() throws Exception {
        when(authService.login(any(), eq("127.0.0.1"))).thenReturn(new LoginResponse(
                new LoginData("session-token", Instant.now().plusSeconds(900),
                        new AuthenticatedUser(UUID.randomUUID(), "admin", "Administrator",
                                "SUPER_ADMIN", Set.of("user.read"), false))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .header(ApiHeaders.CORRELATION_ID, "test-correlation-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"secret","clientId":"test-client"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiHeaders.CORRELATION_ID, "test-correlation-id"))
                .andExpect(jsonPath("$.data.sessionToken").value("session-token"))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginFailureReturnsApiError() throws Exception {
        when(authService.login(any(), eq("127.0.0.1"))).thenThrow(new IdentityException(
                ApiErrorCode.AUTH_INVALID_CREDENTIALS, org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong","clientId":"test-client"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.message").value(not("wrong")));
    }

    @Test
    void api004BlankLoginRequestReturnsValidationContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"","clientId":"test-client"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void currentSessionReturnsEffectivePermissions() throws Exception {
        final UUID userId = UUID.randomUUID();
        when(authService.currentSession("session-token")).thenReturn(new CurrentSessionResponse(
                userId, "admin", "Administrator", "SUPER_ADMIN", Instant.now().plusSeconds(900), Set.of("user.read"), false));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer session-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.effectivePermissions[0]").value("user.read"));
    }
}

package com.bizco.server.identity.controller;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
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
            .build();

    @Test
    void loginReturnsSessionTokenWithoutLeakingPassword() throws Exception {
        when(authService.login(any(), eq("127.0.0.1"))).thenReturn(new LoginResponse(
                "session-token",
                Instant.now().plusSeconds(900),
                UUID.randomUUID(),
                "admin",
                "Administrator",
                Set.of("identity.user.read")));

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"secret","clientId":"test-client"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("session-token"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginFailureReturnsApiError() throws Exception {
        when(authService.login(any(), eq("127.0.0.1"))).thenThrow(new IdentityException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong","clientId":"test-client"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.message").value(not("wrong")));
    }
}

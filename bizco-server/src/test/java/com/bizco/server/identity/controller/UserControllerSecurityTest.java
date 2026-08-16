package com.bizco.server.identity.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.identity.UserResponses.ResetPasswordResponse;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.server.config.CorrelationIdFilter;
import com.bizco.server.config.SecurityConfig;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import com.bizco.server.identity.service.AuthService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.identity.service.UserService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, ApiExceptionHandler.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserSessionRepository sessionRepository;

    @Test
    void userReadEndpointAllowsUsersWithPermission() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Long primaryRoleId = 1L;
        authenticate(userId, Set.of("user.read"));
        when(userService.listUsers()).thenReturn(List.of(new UserResponse(
                userId, "admin", "Administrator", primaryRoleId, "ACTIVE", true, null, 0)));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void secRbac002ProtectedEndpointRejectsUsersWithoutPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("role.read"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void api002ProtectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    @Test
    void api005MissingUserReturnsStableNotFoundError() throws Exception {
        final UUID userId = UUID.randomUUID();
        final UUID missingId = UUID.randomUUID();
        authenticate(userId, Set.of("user.read"));
        when(userService.getUser(missingId)).thenThrow(new IdentityException(
                ApiErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found"));

        mockMvc.perform(get("/api/v1/users/" + missingId)
                        .header("Authorization", "Bearer good-token")
                        .header(ApiHeaders.CORRELATION_ID, "api-005"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(ApiHeaders.CORRELATION_ID, "api-005"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/" + missingId))
                .andExpect(jsonPath("$.correlationId").value("api-005"));
    }

    @Test
    void resetPasswordEndpointAllowsUsersWithPermission() throws Exception {
        final UUID adminId = UUID.randomUUID();
        final UUID targetUserId = UUID.randomUUID();
        authenticate(adminId, Set.of("user.reset_password"));
        when(userService.resetPassword(org.mockito.ArgumentMatchers.eq(targetUserId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ResetPasswordResponse(targetUserId, "Temp!23456A"));

        mockMvc.perform(post("/api/v1/users/" + targetUserId + "/reset-password")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp!23456A"));
    }

    @Test
    void resetPasswordEndpointRejectsUsersWithoutPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("user.read"));

        mockMvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/reset-password")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    private void authenticate(final UUID userId, final Set<String> permissions) throws Exception {
        final Role role = new Role("ADMIN", "Administrator", Map.of(), true);
        final User user = new User("admin", "Administrator", "hash", role);
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

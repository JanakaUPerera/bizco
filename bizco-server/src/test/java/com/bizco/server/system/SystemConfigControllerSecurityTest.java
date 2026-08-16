package com.bizco.server.system;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.dto.system.SystemResponses.SystemConfigListResponse;
import com.bizco.server.config.CorrelationIdFilter;
import com.bizco.server.config.SecurityConfig;
import com.bizco.server.identity.controller.ApiExceptionHandler;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.system.service.SystemConfigService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemConfigController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, ApiExceptionHandler.class})
class SystemConfigControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemConfigService systemConfigService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserSessionRepository sessionRepository;

    @Test
    void configReadAllowedForUsersWithPermission() throws Exception {
        authenticate(Set.of("system.config.read"));
        when(systemConfigService.list()).thenReturn(new SystemConfigListResponse(List.of()));

        mockMvc.perform(get("/api/v1/system/config").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void configReadDeniedWithoutPermission() throws Exception {
        authenticate(Set.of("user.read"));

        mockMvc.perform(get("/api/v1/system/config").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    private void authenticate(final Set<String> permissions) throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = new Role("OWNER", "Owner", Map.of(), true);
        final User user = new User("owner", "Owner", "hash", role);
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

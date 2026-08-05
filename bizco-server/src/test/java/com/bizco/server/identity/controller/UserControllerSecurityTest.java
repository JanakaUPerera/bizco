package com.bizco.server.identity.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.dto.identity.UserResponses.UserResponse;
import com.bizco.server.config.SecurityConfig;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserSessionRepository sessionRepository;

    @Test
    void userReadEndpointAllowsUsersWithPermission() throws Exception {
        final UUID userId = UUID.randomUUID();
        final UUID primaryRoleId = UUID.randomUUID();
        authenticate(userId, Set.of("identity.user.read"));
        when(userService.listUsers()).thenReturn(List.of(new UserResponse(
                userId, "admin", "Administrator", primaryRoleId, "ACTIVE", true, null)));

        mockMvc.perform(get("/api/identity/users").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void userReadEndpointRejectsUsersWithoutPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("identity.role.read"));

        mockMvc.perform(get("/api/identity/users").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden());
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

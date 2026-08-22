package com.bizco.server.catalog.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.server.catalog.application.BarcodeImageService;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.config.CorrelationIdFilter;
import com.bizco.server.config.SecurityConfig;
import com.bizco.server.identity.controller.ApiExceptionHandler;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import com.bizco.server.identity.service.PermissionService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** There was previously no security test for {@link CatalogController} at all; this closes that
 *  gap (existing category/product/service endpoints) as well as covering the Phase 6 Week 16
 *  brand/attribute/category-attribute endpoints. */
@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, ApiExceptionHandler.class})
class CatalogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogService catalogService;
    @MockitoBean
    private BarcodeImageService barcodeImageService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserSessionRepository sessionRepository;
    @MockitoBean
    private PermissionService permissionService;

    @Test
    void brandReadRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.read"));

        mockMvc.perform(get("/api/v1/brands").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void brandCreateRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.brand.read"));

        mockMvc.perform(post("/api/v1/brands").header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void attributeReadRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.read"));

        mockMvc.perform(get("/api/v1/attributes").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void attributeCreateRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.attribute.read"));

        mockMvc.perform(post("/api/v1/attributes").header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void categoryAttributeAssignRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.attribute.read"));

        mockMvc.perform(post("/api/v1/product-categories/1/attributes").header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void categoryAttributeUnassignRequiresPermission() throws Exception {
        authenticate(UUID.randomUUID(), Set.of("product.attribute.read"));

        mockMvc.perform(delete("/api/v1/product-categories/1/attributes/1")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    private void authenticate(final UUID userId, final Set<String> permissions) throws Exception {
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

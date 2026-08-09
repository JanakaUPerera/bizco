package com.bizco.server.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.service.PermissionService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final SessionAuthenticationFilter filter = new SessionAuthenticationFilter(
            tokenService, sessionRepository, permissionService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingBearerTokenLeavesRequestUnauthenticated() throws ServletException, IOException {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidBearerTokenLeavesRequestUnauthenticated() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        when(tokenService.hash("bad-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validBearerTokenAuthenticatesUserWithEffectivePermissions() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = new Role("ADMIN", "Administrator", Map.of(), true);
        final User user = new User("admin", "Administrator", "hash", role);
        setId(user, userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1", Instant.now().plusSeconds(60));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        when(tokenService.hash("good-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(session));
        when(permissionService.effectivePermissions(userId)).thenReturn(Set.of("user.read"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("admin", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("user.read")));
        verify(sessionRepository).save(session);
        assertTrue(session.getExpiresAt().isAfter(Instant.now().plusSeconds(800)));
    }

    @Test
    void secSession001ExpiredIdleSessionReturnsUnauthorized() throws Exception {
        final UUID userId = UUID.randomUUID();
        final Role role = new Role("CASHIER", "Cashier", Map.of(), true);
        final User user = new User("cashier", "Cashier", "hash", role);
        setId(user, userId);
        final UserSession session = new UserSession(user, "hashed-token", "client-1", Instant.now().plusSeconds(60));
        setField(session, "lastActivityAt", Instant.now().minusSeconds(901));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/api/v1/users");
        request.addHeader("Authorization", "Bearer idle-token");
        when(tokenService.hash("idle-token")).thenReturn("hashed-token");
        when(sessionRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(session));

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_SESSION_EXPIRED"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void setId(final User user, final UUID id) throws Exception {
        setField(user, "id", id);
    }

    private void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

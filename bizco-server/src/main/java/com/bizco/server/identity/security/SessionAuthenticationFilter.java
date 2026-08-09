package com.bizco.server.identity.security;

import com.bizco.common.api.ApiError;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.ApiHeaders;
import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.service.AuthService;
import com.bizco.server.identity.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserSessionRepository sessionRepository;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public SessionAuthenticationFilter(final TokenService tokenService, final UserSessionRepository sessionRepository,
                                       final PermissionService permissionService) {
        this.tokenService = tokenService;
        this.sessionRepository = sessionRepository;
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            if (!authenticate(header.substring(7), request, response)) {
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticate(final String token, final HttpServletRequest request,
                                 final HttpServletResponse response) throws IOException {
        final String tokenHash = tokenService.hash(token);
        final Instant now = Instant.now();
        final var optionalSession = sessionRepository.findByTokenHash(tokenHash);
        if (optionalSession.isEmpty()) {
            return true;
        }
        final UserSession session = optionalSession.get();
        if (!session.activeAt(now)) {
            writeUnauthorized(response, request, ApiErrorCode.AUTH_SESSION_EXPIRED, "Session has expired");
            return false;
        }
        session.touch(now, AuthService.IDLE_TIMEOUT);
        sessionRepository.save(session);
        final var user = session.getUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        null,
                        authorities(user.getId())));
        return true;
    }

    private List<SimpleGrantedAuthority> authorities(final UUID userId) {
        final List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
        permissionService.effectivePermissions(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    private void writeUnauthorized(final HttpServletResponse response, final HttpServletRequest request,
                                   final ApiErrorCode code, final String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ApiError.of(code.code(), message,
                request.getRequestURI(), correlationId(request)));
    }

    private String correlationId(final HttpServletRequest request) {
        final Object attribute = request.getAttribute(ApiHeaders.CORRELATION_ID);
        return attribute instanceof String value ? value : null;
    }
}

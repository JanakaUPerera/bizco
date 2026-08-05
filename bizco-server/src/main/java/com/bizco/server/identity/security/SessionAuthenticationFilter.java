package com.bizco.server.identity.security;

import com.bizco.server.identity.entity.UserSession;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.service.PermissionService;
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
            authenticate(header.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(final String token) {
        final String tokenHash = tokenService.hash(token);
        final Instant now = Instant.now();
        sessionRepository.findByTokenHash(tokenHash)
                .filter(session -> session.activeAt(now))
                .map(UserSession::getUser)
                .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                null,
                                authorities(user.getId()))));
    }

    private List<SimpleGrantedAuthority> authorities(final UUID userId) {
        final List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
        permissionService.effectivePermissions(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }
}

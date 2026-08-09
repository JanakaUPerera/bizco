package com.bizco.server.config;

import com.bizco.common.api.ApiHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String correlationId = resolveCorrelationId(request);
        request.setAttribute(ApiHeaders.CORRELATION_ID, correlationId);
        response.setHeader(ApiHeaders.CORRELATION_ID, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(final HttpServletRequest request) {
        final String requested = request.getHeader(ApiHeaders.CORRELATION_ID);
        if (requested == null || requested.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requested.trim();
    }
}

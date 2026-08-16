package com.bizco.server.config;

import com.bizco.common.api.ApiError;
import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.api.ApiHeaders;
import com.bizco.server.identity.security.SessionAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                            final SessionAuthenticationFilter sessionAuthenticationFilter) throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/v1/health", "/actuator/health",
                                "/api/auth/login", "/api/v1/auth/login",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(objectMapper, response, request, HttpServletResponse.SC_UNAUTHORIZED,
                                        ApiErrorCode.AUTH_SESSION_INVALID, "Session is invalid"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(objectMapper, response, request, HttpServletResponse.SC_FORBIDDEN,
                                        ApiErrorCode.AUTH_PERMISSION_DENIED, "Permission denied")))
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    private void writeError(final ObjectMapper objectMapper, final HttpServletResponse response,
                            final HttpServletRequest request, final int status,
                            final ApiErrorCode code, final String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ApiError.of(code.code(), message,
                request.getRequestURI(), correlationId(request)));
    }

    private String correlationId(final HttpServletRequest request) {
        final Object attribute = request.getAttribute(ApiHeaders.CORRELATION_ID);
        return attribute instanceof String value ? value : null;
    }
}



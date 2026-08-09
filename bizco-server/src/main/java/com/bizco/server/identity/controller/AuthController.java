package com.bizco.server.identity.controller;

import com.bizco.common.api.FieldError;
import com.bizco.common.dto.identity.AuthResponses.ChangePasswordRequest;
import com.bizco.common.dto.identity.AuthResponses.CurrentSessionResponse;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody final LoginRequest request, final HttpServletRequest httpRequest) {
        validateLogin(request);
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") final String authorization) {
        authService.logout(token(authorization));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentSessionResponse me(@RequestHeader("Authorization") final String authorization) {
        return authService.currentSession(token(authorization));
    }

    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(@RequestBody final ChangePasswordRequest request, final Principal principal) {
        authService.changePassword(principal.getName(), request);
        return ResponseEntity.noContent().build();
    }

    private String token(final String authorization) {
        return authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
    }

    private void validateLogin(final LoginRequest request) {
        final List<FieldError> fieldErrors = new ArrayList<>();
        if (request == null || request.username() == null || request.username().isBlank()) {
            fieldErrors.add(new FieldError("username", "REQUIRED", "Username is required."));
        }
        if (request == null || request.password() == null || request.password().isBlank()) {
            fieldErrors.add(new FieldError("password", "REQUIRED", "Password is required."));
        }
        if (!fieldErrors.isEmpty()) {
            throw new ApiValidationException(fieldErrors);
        }
    }
}


package com.bizco.server.identity.controller;

import com.bizco.common.dto.identity.UserResponses.LoginHistorySearchResponse;
import com.bizco.server.identity.service.LoginHistoryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/login-history")
public class LoginHistoryController {

    private final LoginHistoryService loginHistoryService;

    public LoginHistoryController(final LoginHistoryService loginHistoryService) {
        this.loginHistoryService = loginHistoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.login_history.read')")
    LoginHistorySearchResponse search(@RequestParam(required = false) final UUID userId,
                                      @RequestParam(defaultValue = "0") final int page,
                                      @RequestParam(defaultValue = "20") final int size) {
        return loginHistoryService.search(userId, page, size);
    }
}

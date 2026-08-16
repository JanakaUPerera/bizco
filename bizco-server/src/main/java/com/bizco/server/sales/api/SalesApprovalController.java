package com.bizco.server.sales.api;

import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.bizco.server.sales.application.SalesApprovalService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/approvals")
public class SalesApprovalController {

    private final SalesApprovalService service;

    public SalesApprovalController(final SalesApprovalService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('invoice.create')")
    SalesApprovalResponse requestApproval(@PathVariable final UUID invoiceId,
                                          @RequestBody final SalesApprovalRequest request,
                                          final Authentication authentication) {
        return service.requestApproval(invoiceId, request, authentication);
    }
}

package com.bizco.server.audit.api;

import com.bizco.common.dto.audit.AuditDtos.AuditLogSearchResponse;
import com.bizco.server.audit.service.AuditService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(final AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit.read')")
    AuditLogSearchResponse search(@RequestParam(required = false) final String entityType,
                                  @RequestParam(required = false) final String actionCode,
                                  @RequestParam(required = false) final UUID actorUserId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant to,
                                  @RequestParam(defaultValue = "0") final int page,
                                  @RequestParam(defaultValue = "20") final int size) {
        return auditService.search(entityType, actionCode, actorUserId, from, to, page, size);
    }
}

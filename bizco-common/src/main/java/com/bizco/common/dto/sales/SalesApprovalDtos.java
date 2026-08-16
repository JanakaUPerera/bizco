package com.bizco.common.dto.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SalesApprovalDtos {

    private SalesApprovalDtos() {
    }

    public record SalesApprovalRequest(
            String approvalType,
            UUID invoiceLineId,
            BigDecimal requestedValue,
            String reason,
            String approverUsername,
            String approverSecret
    ) {
    }

    public record ApprovedBy(UUID userId, String displayName) {
    }

    public record SalesApprovalResponse(
            UUID approvalId,
            String approvalType,
            ApprovedBy approvedBy,
            Instant approvedAt
    ) {
    }
}

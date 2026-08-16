package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.sales.SalesApprovalDtos.ApprovedBy;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.SalesApproval;
import com.bizco.server.sales.domain.SalesApprovalType;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import com.bizco.server.sales.infrastructure.SalesApprovalRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesApprovalService {

    private final SalesApprovalRepository repository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SalesApprovalService(final SalesApprovalRepository repository, final InvoiceRepository invoiceRepository,
                                final UserRepository userRepository, final PermissionService permissionService,
                                final PasswordEncoder passwordEncoder, final AuditService auditService) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Independently authenticates the approver (never trusting the requester's own session for
     * this) and checks the approver holds the permission required for the approval type
     * (ApiContracts.md &sect;14.2). The approver's password/PIN is verified but never stored.
     */
    @Transactional
    public SalesApprovalResponse requestApproval(final UUID invoiceId, final SalesApprovalRequest request,
                                                  final Authentication authentication) {
        final Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
        if (request.invoiceLineId() != null) {
            requireLine(invoice, request.invoiceLineId());
        }
        final SalesApprovalType approvalType = SalesApprovalType.valueOf(request.approvalType().trim().toUpperCase());
        final User approver = authenticateApprover(request.approverUsername(), request.approverSecret());
        requirePermission(approver, requiredPermission(approvalType));

        final UUID requestedBy = actor(authentication);
        final SalesApproval saved = repository.save(new SalesApproval(invoiceId, request.invoiceLineId(), approvalType,
                requestedBy, approver.getId(), request.reason()));
        auditService.record("SALES_APPROVAL", saved.getId().toString(), "SALES_APPROVAL_GRANTED", requestedBy,
                Map.of("approvalType", approvalType.name(), "invoiceId", invoiceId.toString(),
                        "approvedByUsername", approver.getUsername()));
        return new SalesApprovalResponse(saved.getId(), approvalType.name(),
                new ApprovedBy(approver.getId(), approver.getDisplayName()), saved.getApprovedAt());
    }

    private void requireLine(final Invoice invoice, final UUID lineId) {
        try {
            invoice.line(lineId);
        } catch (final IllegalArgumentException exception) {
            throw new IdentityException(ApiErrorCode.INVOICE_LINE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Invoice line was not found on this invoice");
        }
    }

    private User authenticateApprover(final String username, final String secret) {
        final User approver = userRepository.findByUsernameIgnoreCase(username == null ? "" : username)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                        HttpStatus.UNAUTHORIZED, "Approver credentials are invalid"));
        if (!approver.canAuthenticate(Instant.now())) {
            throw new IdentityException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE, HttpStatus.UNAUTHORIZED,
                    "Approver account is not active");
        }
        if (secret == null || !passwordEncoder.matches(secret, approver.getPasswordHash())) {
            throw new IdentityException(ApiErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Approver credentials are invalid");
        }
        return approver;
    }

    private void requirePermission(final User approver, final String requiredPermission) {
        if (!permissionService.effectivePermissions(approver.getId()).contains(requiredPermission)) {
            throw new IdentityException(ApiErrorCode.AUTH_PERMISSION_DENIED, HttpStatus.FORBIDDEN,
                    "Approver does not have permission to authorize this action");
        }
    }

    private String requiredPermission(final SalesApprovalType approvalType) {
        return switch (approvalType) {
            case DISCOUNT_10_25 -> "invoice.discount.approve_25";
            case DISCOUNT_OVER_25 -> "invoice.discount.approve_50";
            case PRICE_OVERRIDE -> "invoice.override_price";
            case BELOW_COST -> "invoice.sell_below_cost";
        };
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}

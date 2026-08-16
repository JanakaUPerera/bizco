package com.bizco.server.identity.service;

import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.entity.UserRole;
import com.bizco.server.identity.repository.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically revokes secondary role assignments whose {@code expiresAt} has passed.
 *
 * <p>Effective-permission calculation ({@link PermissionService}) already excludes expired
 * assignments at request time, so this scheduler does not change runtime authorization; it
 * closes the audit gap by recording a {@code SECONDARY_ROLE_EXPIRED} event and marking the
 * assignment inactive so it stops showing up as "active" in role-management screens.
 */
@Component
public class SecondaryRoleExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecondaryRoleExpiryScheduler.class);

    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public SecondaryRoleExpiryScheduler(final UserRoleRepository userRoleRepository, final AuditService auditService) {
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${bizco.security.secondary-role-expiry-check-interval-ms:300000}")
    @Transactional
    public void expireSecondaryRoles() {
        final List<UserRole> expired = userRoleRepository.findExpiredActive(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (final UserRole assignment : expired) {
            assignment.revoke(null, "EXPIRED");
            auditService.record("USER_ROLE_ASSIGNMENT", assignment.getId().toString(), "SECONDARY_ROLE_EXPIRED", null,
                    Map.of("userId", assignment.getUser().getId().toString(), "roleCode", assignment.getRole().getCode()));
        }
        log.info("Expired {} secondary role assignment(s)", expired.size());
    }
}

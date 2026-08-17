package com.bizco.server.sales.application;

import com.bizco.server.audit.service.AuditService;
import com.bizco.server.sales.domain.HeldSale;
import com.bizco.server.sales.infrastructure.HeldSaleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * StateMachines.md &sect;7.8: periodically expires held sales past their reservation window,
 * mirroring {@code SecondaryRoleExpiryScheduler}'s pattern. Since the stock ledger (Week 12)
 * doesn't exist yet, there is no reservation to actually release - this only advances the held
 * sale's own status and records the release event (HELD_SALE_RELEASED, StateMachines.md
 * &sect;table of audit actions).
 */
@Component
public class HeldSaleExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeldSaleExpiryScheduler.class);

    private final HeldSaleRepository heldSaleRepository;
    private final AuditService auditService;

    public HeldSaleExpiryScheduler(final HeldSaleRepository heldSaleRepository, final AuditService auditService) {
        this.heldSaleRepository = heldSaleRepository;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${bizco.sales.held-sale-expiry-check-interval-ms:300000}")
    @Transactional
    public void expireHeldSales() {
        final List<HeldSale> expirable = heldSaleRepository.findExpirable(Instant.now());
        if (expirable.isEmpty()) {
            return;
        }
        for (final HeldSale heldSale : expirable) {
            heldSale.expire();
            auditService.record("HELD_SALE", heldSale.getId().toString(), "HELD_SALE_RELEASED", null,
                    Map.of("heldNumber", heldSale.getHeldNumber(), "reason", "EXPIRED"));
        }
        log.info("Expired {} held sale(s)", expirable.size());
    }
}

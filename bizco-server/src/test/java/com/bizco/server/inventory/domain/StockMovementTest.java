package com.bizco.server.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** STK-LEDGER-002: a movement's shape enforces immutability by only ever offering a constructor. */
class StockMovementTest {

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> new StockMovement(UUID.randomUUID(), MovementType.SALE, BigDecimal.ZERO,
                StockReferenceType.INVOICE, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesSignedQuantityAsGiven() {
        final UUID productId = UUID.randomUUID();
        final UUID referenceId = UUID.randomUUID();
        final UUID sourceLineId = UUID.randomUUID();
        final UUID actorId = UUID.randomUUID();

        final StockMovement movement = new StockMovement(productId, MovementType.SALE, new BigDecimal("-2.000"),
                StockReferenceType.INVOICE, referenceId, sourceLineId, "note", actorId);

        assertThat(movement.getProductId()).isEqualTo(productId);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("-2.000");
        assertThat(movement.getReferenceType()).isEqualTo(StockReferenceType.INVOICE);
        assertThat(movement.getReferenceId()).isEqualTo(referenceId);
        assertThat(movement.getSourceLineId()).isEqualTo(sourceLineId);
        assertThat(movement.getCreatedBy()).isEqualTo(actorId);
    }
}

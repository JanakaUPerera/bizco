package com.bizco.server.manufacturing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.domain.ProductVariant;
import com.bizco.server.catalog.infrastructure.ProductVariantRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.inventory.application.StockPostingService;
import com.bizco.server.manufacturing.domain.BillOfMaterials;
import com.bizco.server.manufacturing.domain.BomItem;
import com.bizco.server.manufacturing.domain.ProductionOrder;
import com.bizco.server.manufacturing.infrastructure.BillOfMaterialsRepository;
import com.bizco.server.manufacturing.infrastructure.ProductionOrderRepository;
import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Task 8 code-review fix (STK-CON-003): every variant a Produce transaction touches - every
 * distinct component AND the finished variant - must be locked by exactly one
 * {@code StockPostingService.lockVariants} call, never by a separate {@code lockVariant} call for
 * the finished variant. Only variants locked together in one {@code lockVariants} call share its
 * stable-ascending-order deadlock-avoidance guarantee (see {@code StockPostingService}'s class
 * Javadoc); a finished variant of one BOM can also be a component of another
 * (multi-level/sub-assembly manufacturing), so two separate lock calls would not share that
 * guarantee across two concurrently produced, cross-referencing BOMs.
 *
 * <p>This is a plain Mockito unit test, not a {@code PostgresIT}, because what needs proving here
 * is a call-site/argument property of {@code doProduce} itself - which variant ids reach which
 * {@code StockPostingService} method, and in how many calls - not persistence or real-lock
 * behaviour (already covered end-to-end by {@code ProductionServicePostgresIT}). A genuine
 * concurrent-deadlock repro is left to a dedicated concurrency test task later in the plan, per
 * the review's own guidance.
 */
class ProductionServiceLockingOrderTest {

    private final BillOfMaterialsRepository bomRepository = mock(BillOfMaterialsRepository.class);
    private final ProductionOrderRepository productionOrderRepository = mock(ProductionOrderRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final StockPostingService stockPostingService = mock(StockPostingService.class);
    private final DocumentSequenceRepository documentSequenceRepository = mock(DocumentSequenceRepository.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ProductionService productionService = new ProductionService(bomRepository, productionOrderRepository,
            variantRepository, stockPostingService, documentSequenceRepository, idempotencyService, userRepository,
            auditService);

    @Test
    @SuppressWarnings("unchecked")
    void produceLocksEveryComponentAndTheFinishedVariantInOneOrderedCall() throws Exception {
        final UUID bomId = UUID.randomUUID();
        final UUID finishedVariantId = UUID.randomUUID();
        final UUID componentA = UUID.randomUUID();
        final UUID componentB = UUID.randomUUID();

        final BillOfMaterials bom = new BillOfMaterials(finishedVariantId, "Framed Photo");
        bom.addItem(new BomItem(componentA, new BigDecimal("1.000"), null));
        bom.addItem(new BomItem(componentB, new BigDecimal("2.000"), null));
        assignGeneratedId(bom, "id");
        when(bomRepository.findById(bomId)).thenReturn(Optional.of(bom));

        final ProductVariant componentAVariant = mock(ProductVariant.class);
        when(componentAVariant.getCostPrice()).thenReturn(new BigDecimal("5.00"));
        final ProductVariant componentBVariant = mock(ProductVariant.class);
        when(componentBVariant.getCostPrice()).thenReturn(new BigDecimal("3.00"));
        final ProductVariant finishedVariant = mock(ProductVariant.class);
        when(stockPostingService.lockVariants(anyCollection())).thenReturn(Map.of(componentA, componentAVariant,
                componentB, componentBVariant, finishedVariantId, finishedVariant));

        when(documentSequenceRepository.nextDailyValue("PRODUCTION_ORDER", LocalDate.now(), "MO", 4)).thenReturn(1L);
        when(documentSequenceRepository.formatDaily("MO", LocalDate.now(), 1L, 4)).thenReturn("MO-20260823-0001");
        when(productionOrderRepository.saveAndFlush(any(ProductionOrder.class))).thenAnswer(invocation -> {
            final ProductionOrder order = invocation.getArgument(0);
            assignGeneratedId(order, "id");
            return order;
        });
        when(variantRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(idempotencyService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            final Supplier<Object> action = invocation.getArgument(4);
            return new IdempotentResult<>(action.get(), false);
        });

        final ProduceRequest request = new ProduceRequest(bomId, new BigDecimal("1.000"), "MADE_TO_ORDER", null);
        final var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a",
                List.of(new SimpleGrantedAuthority("manufacturing.produce")));

        productionService.produce(UUID.randomUUID(), request, authentication);

        final ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(stockPostingService, times(1)).lockVariants(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(componentA, componentB, finishedVariantId);

        // The whole point of this test: no separate, un-ordered lock call for the finished
        // variant - every touched variant must go through the single lockVariants call asserted
        // above, or the STK-CON-003 ascending-order guarantee does not hold across BOMs whose
        // finished/component roles cross (BOM_A: finished=X, components={Y}; BOM_B: finished=Y,
        // components={X}).
        verify(stockPostingService, never()).lockVariant(any(UUID.class));
    }

    /** Simulates what Hibernate does at flush/load time for a {@code @GeneratedValue} id - there
     *  is no public setter, and this test never touches a real persistence context, so the id has
     *  to be assigned this way for the response/audit-building code that runs after
     *  {@code saveAndFlush} (and, for the BOM, after {@code findById}) to run without a
     *  NullPointerException. */
    private void assignGeneratedId(final Object entity, final String fieldName) throws Exception {
        final Field idField = entity.getClass().getDeclaredField(fieldName);
        idField.setAccessible(true);
        idField.set(entity, UUID.randomUUID());
    }
}

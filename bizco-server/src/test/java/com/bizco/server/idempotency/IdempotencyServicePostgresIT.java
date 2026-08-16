package com.bizco.server.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.server.idempotency.service.IdempotencyService;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class IdempotencyServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactRetryReplaysWithoutRerunningTheAction() {
        final UUID requestId = UUID.randomUUID();
        final AtomicInteger actionRuns = new AtomicInteger();

        final IdempotentResult<String> first = inTransaction(() -> idempotencyService.execute(requestId,
                "invoice.post", Map.of("customerId", "c-1", "total", 100), String.class,
                () -> {
                    actionRuns.incrementAndGet();
                    return "INV-20260401-0001";
                }));
        final IdempotentResult<String> second = inTransaction(() -> idempotencyService.execute(requestId,
                "invoice.post", Map.of("customerId", "c-1", "total", 100), String.class,
                () -> {
                    actionRuns.incrementAndGet();
                    return "INV-20260401-9999";
                }));

        assertThat(actionRuns.get()).isEqualTo(1);
        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response()).isEqualTo("INV-20260401-0001");
    }

    @Test
    void sameKeyDifferentPayloadIsRejectedAsConflict() {
        final UUID requestId = UUID.randomUUID();

        inTransaction(() -> idempotencyService.execute(requestId, "invoice.post",
                Map.of("customerId", "c-1", "total", 100), String.class, () -> "INV-20260401-0001"));

        assertThatThrownBy(() -> inTransaction(() -> idempotencyService.execute(requestId, "invoice.post",
                Map.of("customerId", "c-1", "total", 999), String.class, () -> "INV-20260401-0002")))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> assertThat(((IdentityException) ex).getCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void rolledBackAttemptCanBeRetriedWithTheSameKey() {
        final UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> inTransaction(() -> idempotencyService.execute(requestId, "invoice.post",
                Map.of("customerId", "c-1"), String.class, () -> {
                    throw new IllegalStateException("simulated posting failure");
                })));

        final IdempotentResult<String> retried = inTransaction(() -> idempotencyService.execute(requestId,
                "invoice.post", Map.of("customerId", "c-1"), String.class, () -> "INV-20260401-0001"));

        assertThat(retried.replayed()).isFalse();
        assertThat(retried.response()).isEqualTo("INV-20260401-0001");
    }

    /**
     * MVP's stated Critical risk "Duplicate retries on LAN" (DevelopmentPlan.md 12): fires 10
     * concurrent identical requests at the same Idempotency-Key and asserts the underlying
     * business action ran exactly once, and every caller observed the same result.
     */
    @Test
    void concurrentIdenticalRetriesRunTheActionExactlyOnce() throws Exception {
        final UUID requestId = UUID.randomUUID();
        final AtomicInteger actionRuns = new AtomicInteger();
        final int attempts = 10;
        final ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            final List<Callable<IdempotentResult<String>>> tasks = IntStream.range(0, attempts)
                    .<Callable<IdempotentResult<String>>>mapToObj(i -> () -> inTransaction(() ->
                            idempotencyService.execute(requestId, "invoice.post",
                                    Map.of("customerId", "c-1", "total", 100), String.class, () -> {
                                        actionRuns.incrementAndGet();
                                        return "INV-20260401-0001";
                                    })))
                    .toList();
            final List<Future<IdempotentResult<String>>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
            final List<IdempotentResult<String>> results = futures.stream().map(this::get).toList();

            assertThat(actionRuns.get()).isEqualTo(1);
            assertThat(results).allSatisfy(result -> assertThat(result.response()).isEqualTo("INV-20260401-0001"));
            assertThat(results.stream().filter(IdempotentResult::replayed).count()).isEqualTo(attempts - 1L);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private <T> T get(final Future<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private <T> T inTransaction(final java.util.function.Supplier<T> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }
}

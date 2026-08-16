package com.bizco.server.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.server.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DocumentSequenceRepositoryPostgresIT extends PostgresIntegrationTest {

    @Autowired
    private DocumentSequenceRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void dailySequenceStartsAtOneAndIncrementsWithinTheSameDay() {
        final LocalDate day = LocalDate.of(2026, 4, 1);

        final long first = inTransaction(() -> repository.nextDailyValue("INV_TEST_A", day, "INV", 4));
        final long second = inTransaction(() -> repository.nextDailyValue("INV_TEST_A", day, "INV", 4));
        final long third = inTransaction(() -> repository.nextDailyValue("INV_TEST_A", day, "INV", 4));

        assertThat(List.of(first, second, third)).containsExactly(first, first + 1, first + 2);
    }

    @Test
    void dailySequenceResetsOnANewBusinessDate() {
        final long dayOneFirst = inTransaction(() -> repository.nextDailyValue("INV_TEST_B", LocalDate.of(2026, 5, 1), "INV", 4));
        inTransaction(() -> repository.nextDailyValue("INV_TEST_B", LocalDate.of(2026, 5, 1), "INV", 4));
        final long dayTwoFirst = inTransaction(() -> repository.nextDailyValue("INV_TEST_B", LocalDate.of(2026, 5, 2), "INV", 4));

        assertThat(dayOneFirst).isEqualTo(1);
        assertThat(dayTwoFirst).isEqualTo(1);
    }

    @Test
    void formatDailyMatchesMvpInvoiceNumberFormat() {
        assertThat(repository.formatDaily("INV", LocalDate.of(2026, 4, 1), 42, 4)).isEqualTo("INV-20260401-0042");
    }

    /**
     * MVP.md 5.5 requires gap-free, collision-free numbering under concurrency; DatabaseDesign.md
     * 10.2 prohibits MAX(number)+1 for exactly this reason. Fires 20 concurrent allocations at the
     * same (documentType, businessDate) and asserts every returned value is unique.
     */
    @Test
    void concurrentAllocationsOnTheSameDayNeverCollide() throws Exception {
        final LocalDate day = LocalDate.of(2026, 6, 1);
        final int attempts = 20;
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Callable<Long>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Long>>mapToObj(i -> () -> inTransaction(() ->
                            repository.nextDailyValue("INV_TEST_C", day, "INV", 4)))
                    .toList();
            final List<Future<Long>> futures = pool.invokeAll(tasks);
            final Set<Long> values = futures.stream().map(this::get).collect(Collectors.toSet());

            assertThat(values).hasSize(attempts);
            assertThat(values).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, attempts).boxed().toList());
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private <T> T get(final Future<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private long inTransaction(final java.util.function.Supplier<Long> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }
}

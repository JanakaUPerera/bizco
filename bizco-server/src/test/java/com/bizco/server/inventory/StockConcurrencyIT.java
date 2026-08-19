package com.bizco.server.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleDetailResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.sales.application.HeldSaleService;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * STK-CON-001/002 (AcceptanceTests.md &sect;22, P0): the product row lock
 * ({@code StockPostingService.lockProduct}/{@code lockProducts}), not any application-level
 * pre-check, is what a concurrent oversell attempt must be unable to get past. Mirrors
 * {@code AppointmentConcurrencyIT}: each attempt runs in its own thread and its own transaction.
 */
class StockConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private HeldSaleService heldSaleService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private StockQueryService stockQueryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void stkCon001ExactlyOneConcurrentSaleOfTheLastUnitCommits() throws Exception {
        final int attempts = 8;
        final User cashier = inTransaction(this::createUser);
        final ProductDetailResponse product = inTransaction(this::createProduct);
        inTransaction(() -> {
            seedStock(product.productId(), "1.000");
            return null;
        });
        final List<UUID> draftInvoiceIds = IntStream.range(0, attempts)
                .mapToObj(i -> inTransaction(() -> prepareDraft(cashier, product))).toList();

        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Callable<Object>> tasks = draftInvoiceIds.stream()
                    .<Callable<Object>>map(invoiceId -> () -> attemptPost(cashier, invoiceId)).toList();
            final List<Object> results = pool.invokeAll(tasks).stream().map(this::get).toList();

            final long succeeded = results.stream().filter(r -> r instanceof UUID).count();
            final long insufficientStock = results.stream().filter(IdentityException.class::isInstance)
                    .map(IdentityException.class::cast)
                    .filter(ex -> ex.getCode() == ApiErrorCode.STOCK_INSUFFICIENT).count();

            assertThat(succeeded).isEqualTo(1);
            assertThat(insufficientStock).isEqualTo(attempts - 1);
            assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("0.000");
        } finally {
            pool.shutdown();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void stkCon002HoldVsSaleOnTheLastUnitNeverOversells() throws Exception {
        final User cashier = inTransaction(this::createUser);
        final ProductDetailResponse product = inTransaction(this::createProduct);
        inTransaction(() -> {
            seedStock(product.productId(), "1.000");
            return null;
        });
        final UUID draftInvoiceId = inTransaction(() -> prepareDraft(cashier, product));

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Future<Object> holdResult = pool.submit(() -> attemptHold(cashier, product));
            final Future<Object> saleResult = pool.submit(() -> attemptPost(cashier, draftInvoiceId));
            final Object hold = get(holdResult);
            final Object sale = get(saleResult);

            final boolean holdSucceeded = hold instanceof HeldSaleDetailResponse;
            final boolean saleSucceeded = sale instanceof UUID;
            assertThat(holdSucceeded ^ saleSucceeded).as("exactly one of hold/sale should win the last unit").isTrue();

            final var level = stockQueryService.levelFor(product.productId());
            assertThat(level.availableStock()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            if (saleSucceeded) {
                assertThat(level.physicalStock()).isEqualByComparingTo("0.000");
            } else {
                assertThat(level.reservedStock()).isEqualByComparingTo("1.000");
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    private UUID prepareDraft(final User cashier, final ProductDetailResponse product) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, "concurrency test"), auth(cashier, "invoice.create"));
        invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("PRODUCT", product.productId(), null,
                null, BigDecimal.ONE, new BigDecimal("100.00"), null, DiscountRequest.NONE));
        return draft.invoiceId();
    }

    private Object attemptPost(final User cashier, final UUID invoiceId) {
        try {
            return inTransaction(() -> {
                final var detail = invoiceService.get(invoiceId);
                final var result = postSaleService.post(invoiceId, UUID.randomUUID(),
                        new PostInvoiceRequest(detail.version(),
                                List.of(new PaymentLineRequest("CASH", detail.totalAmount(), null)), false, List.of()),
                        auth(cashier, "invoice.create"));
                return result.response().invoiceId();
            });
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private Object attemptHold(final User cashier, final ProductDetailResponse product) {
        try {
            return inTransaction(() -> heldSaleService.hold(new HoldSaleRequest(null,
                    List.of(new HeldSaleItemRequest(product.productId(), BigDecimal.ONE, new BigDecimal("100.00"),
                            DiscountRequest.NONE)), "concurrency hold"), auth(cashier, "invoice.hold_bill")));
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private void seedStock(final UUID productId, final String quantity) {
        final UUID actorId = jdbc.queryForObject("select user_id from users limit 1", UUID.class);
        jdbc.update("""
                insert into stock_movements (product_id, movement_type, quantity, reference_type, reference_id, created_by)
                values (?, 'ADJUSTMENT', ?, 'STOCK_ADJUSTMENT', ?, ?)
                """, productId, new BigDecimal(quantity), UUID.randomUUID(), actorId);
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Concurrency " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("CON-" + suffix, null, "Concurrency Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("100.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("conc_" + token(), "Concurrency Cashier", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user, final String permission) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority(permission)));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", List.of(new SimpleGrantedAuthority(permission)));
    }

    private <T> T inTransaction(final Supplier<T> action) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private <T> T get(final Future<T> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

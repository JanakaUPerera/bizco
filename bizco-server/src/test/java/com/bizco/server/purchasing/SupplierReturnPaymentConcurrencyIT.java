package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentAllocationRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.purchasing.application.GoodsReceiptService;
import com.bizco.server.purchasing.application.SupplierPaymentService;
import com.bizco.server.purchasing.application.SupplierReturnService;
import com.bizco.server.purchasing.application.SupplierService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PUR-RET-CON-001/PUR-PAY-CON-001 (AcceptanceTests.md, DevelopmentPlan.md Week 15 task 15.7): the
 * goods receipt row lock ({@code GoodsReceiptRepository.findByIdForUpdate}), not any
 * application-level pre-check, is what two concurrent overlapping return/payment requests against
 * the same goods receipt must be unable to get past. Mirrors {@code StockConcurrencyIT}: each
 * attempt runs in its own thread and its own transaction.
 */
class SupplierReturnPaymentConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private SupplierReturnService supplierReturnService;
    @Autowired
    private SupplierPaymentService supplierPaymentService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private CatalogService catalogService;
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
    void purRetCon001OnlyOneOfTwoOverlappingReturnsCommits() throws Exception {
        final User user = inTransaction(this::createUser);
        final SupplierDetailResponse supplier = inTransaction(this::createSupplier);
        final ProductDetailResponse product = inTransaction(this::createProduct);
        final GoodsReceiptDetailResponse receipt = inTransaction(
                () -> postedReceipt(user, supplier, product, new BigDecimal("10.000"), new BigDecimal("10.00")));
        final UUID goodsReceiptItemId = receipt.items().get(0).goodsReceiptItemId();

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final List<Callable<Object>> tasks = List.of(
                    () -> attemptReturn(user, supplier, receipt.goodsReceiptId(), goodsReceiptItemId),
                    () -> attemptReturn(user, supplier, receipt.goodsReceiptId(), goodsReceiptItemId));
            final List<Object> results = pool.invokeAll(tasks).stream().map(this::get).toList();

            final long succeeded = results.stream().filter(SupplierReturnResponse.class::isInstance).count();
            final long rejected = results.stream().filter(IdentityException.class::isInstance)
                    .map(IdentityException.class::cast)
                    .filter(ex -> ex.getCode() == ApiErrorCode.RETURN_QUANTITY_EXCEEDED).count();

            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdown();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void purPayCon001OnlyOneOfTwoOverlappingAllocationsCommits() throws Exception {
        final User user = inTransaction(this::createUser);
        final SupplierDetailResponse supplier = inTransaction(this::createSupplier);
        final ProductDetailResponse product = inTransaction(this::createProduct);
        final GoodsReceiptDetailResponse receipt = inTransaction(
                () -> postedReceipt(user, supplier, product, new BigDecimal("10.000"), new BigDecimal("10.00"))); // 100.00

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final List<Callable<Object>> tasks = List.of(
                    () -> attemptPayment(user, supplier, receipt.goodsReceiptId()),
                    () -> attemptPayment(user, supplier, receipt.goodsReceiptId()));
            final List<Object> results = pool.invokeAll(tasks).stream().map(this::get).toList();

            final long succeeded = results.stream().filter(SupplierPaymentResponse.class::isInstance).count();
            final long rejected = results.stream().filter(IdentityException.class::isInstance)
                    .map(IdentityException.class::cast)
                    .filter(ex -> ex.getCode() == ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE).count();

            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdown();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    private Object attemptReturn(final User user, final SupplierDetailResponse supplier, final UUID goodsReceiptId,
                                 final UUID goodsReceiptItemId) {
        try {
            return inTransaction(() -> supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                    supplier.supplierId(), goodsReceiptId, "Concurrency test",
                    List.of(new CreateSupplierReturnItemRequest(goodsReceiptItemId, new BigDecimal("7.000")))),
                    auth(user)).response());
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private Object attemptPayment(final User user, final SupplierDetailResponse supplier, final UUID goodsReceiptId) {
        try {
            return inTransaction(() -> supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(
                    supplier.supplierId(), null, "CASH", new BigDecimal("70.00"), null, "Concurrency test",
                    List.of(new SupplierPaymentAllocationRequest(goodsReceiptId, new BigDecimal("70.00")))),
                    auth(user)).response());
        } catch (final IdentityException ex) {
            return ex;
        }
    }

    private GoodsReceiptDetailResponse postedReceipt(final User user, final SupplierDetailResponse supplier,
                                                      final ProductDetailResponse product, final BigDecimal quantity,
                                                      final BigDecimal unitCost) {
        final GoodsReceiptDetailResponse draft = goodsReceiptService.createDraft(
                new CreateGoodsReceiptRequest(null, supplier.supplierId(), null, LocalDate.now(), null), auth(user));
        final GoodsReceiptDetailResponse withItem = goodsReceiptService.addItem(draft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), quantity, null, null, unitCost));
        return goodsReceiptService.post(UUID.randomUUID(), withItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(withItem.version()), auth(user)).response();
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-CON-" + suffix, "Concurrency Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Con Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("PCON-" + suffix, null, "Con Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("1.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("pcon_" + token(), "Concurrency Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                List.of(new SimpleGrantedAuthority("purchasing.grn.create"),
                        new SimpleGrantedAuthority("purchasing.return.create"),
                        new SimpleGrantedAuthority("purchasing.payment.create")));
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

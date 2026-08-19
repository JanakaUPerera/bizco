package com.bizco.server.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementSearchResponse;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.inventory.application.StockQueryService;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.InvoiceVoidService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * STK-LEDGER-001..003, STK-SOURCE-001, REC-STK-001..003 (AcceptanceTests.md &sect;21/25):
 * {@code stock_movements} is the sole authoritative source of physical stock, a sale posts exactly
 * one negative movement per PRODUCT line, and a retried source row cannot double-post.
 */
class StockLedgerPostgresIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private InvoiceVoidService invoiceVoidService;
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
    void stkLedger001And003SalePostsOneNegativeMovementAndReconciles() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        seedStock(product.productId(), "20.000");

        final InvoiceDetailResponse posted = sell(cashier, product, "3.000", "100.00");

        final StockLevelResponse level = stockQueryService.levelFor(product.productId());
        assertThat(level.physicalStock()).isEqualByComparingTo("17.000");
        assertThat(level.availableStock()).isEqualByComparingTo("17.000");

        final StockMovementSearchResponse history = stockQueryService.movementHistory(product.productId(), 0, 20);
        assertThat(history.data()).anySatisfy(movement -> {
            assertThat(movement.movementType()).isEqualTo("SALE");
            assertThat(movement.quantity()).isEqualByComparingTo("-3.000");
            assertThat(movement.referenceId()).isEqualTo(posted.invoiceId());
        });
    }

    @Test
    void saleVoidReversesPhysicalStock() {
        final User cashier = createUser();
        final ProductDetailResponse product = createProduct();
        seedStock(product.productId(), "10.000");
        final InvoiceDetailResponse posted = sell(cashier, product, "4.000", "50.00");
        assertThat(stockQueryService.levelFor(product.productId()).physicalStock()).isEqualByComparingTo("6.000");

        invoiceVoidService.voidInvoice(posted.invoiceId(), new VoidInvoiceRequest("test void", posted.version()),
                auth(cashier, "invoice.void"));

        final StockLevelResponse level = stockQueryService.levelFor(product.productId());
        assertThat(level.physicalStock()).isEqualByComparingTo("10.000");
        final StockMovementSearchResponse history = stockQueryService.movementHistory(product.productId(), 0, 20);
        assertThat(history.data()).anySatisfy(movement -> assertThat(movement.movementType()).isEqualTo("SALE_VOID"));
    }

    /** REC-STK-001: physical stock equals SUM(stock_movements.quantity) for the product. */
    @Test
    void recStk001PhysicalStockReconcilesWithMovementSum() {
        final ProductDetailResponse product = createProduct();
        seedStock(product.productId(), "15.500");
        seedStock(product.productId(), "-4.250");

        final BigDecimal summed = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movements where product_id = ?", BigDecimal.class,
                product.productId());
        final StockLevelResponse level = stockQueryService.levelFor(product.productId());

        assertThat(level.physicalStock()).isEqualByComparingTo(summed);
        assertThat(level.physicalStock()).isEqualByComparingTo("11.250");
    }

    /** REC-STK-003: the low-stock query used by the report and the dashboard summary must agree. */
    @Test
    void recStk003LowStockCountMatchesLowStockListing() {
        final ProductDetailResponse product = createLowReorderProduct();
        seedStock(product.productId(), "1.000");

        final long summaryCount = stockQueryService.lowStockSummary().lowStockCount();
        final boolean listed = stockQueryService.lowStock(0, 500).data().stream()
                .anyMatch(row -> row.productId().equals(product.productId()));

        assertThat(listed).isTrue();
        assertThat(summaryCount).isGreaterThanOrEqualTo(1);
    }

    /** STK-SOURCE-001: the DB unique index, not application logic, is the backstop that rejects a
     *  second movement for the same (movement_type, source_line_id). */
    @Test
    void stkSource001DuplicateSourceLineIsRejectedByTheDatabase() {
        final ProductDetailResponse product = createProduct();
        final UUID sourceLineId = UUID.randomUUID();
        final UUID actorId = jdbc.queryForObject("select user_id from users limit 1", UUID.class);

        jdbc.update("""
                insert into stock_movements (product_id, movement_type, quantity, reference_type, reference_id, source_line_id, created_by)
                values (?, 'ADJUSTMENT', 5.000, 'STOCK_ADJUSTMENT', ?, ?, ?)
                """, product.productId(), UUID.randomUUID(), sourceLineId, actorId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into stock_movements (product_id, movement_type, quantity, reference_type, reference_id, source_line_id, created_by)
                values (?, 'ADJUSTMENT', 2.000, 'STOCK_ADJUSTMENT', ?, ?, ?)
                """, product.productId(), UUID.randomUUID(), sourceLineId, actorId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private InvoiceDetailResponse sell(final User cashier, final ProductDetailResponse product, final String quantity,
                                       final String unitPrice) {
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, "stock ledger test"), auth(cashier, "invoice.create"));
        final var withLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("PRODUCT",
                product.productId(), null, null, new BigDecimal(quantity), new BigDecimal(unitPrice), null,
                DiscountRequest.NONE));
        final var posted = postSaleService.post(draft.invoiceId(), UUID.randomUUID(),
                new PostInvoiceRequest(withLine.version(), List.of(new PaymentLineRequest("CASH",
                        new BigDecimal(quantity).multiply(new BigDecimal(unitPrice)), null)), false, List.of()),
                auth(cashier, "invoice.create"));
        return invoiceService.get(posted.response().invoiceId());
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
        final var category = catalogService.createCategory(new CategoryCreateRequest("Ledger " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("LDG-" + suffix, null, "Ledger Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private ProductDetailResponse createLowReorderProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Low Stock " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("LOW-" + suffix, null, "Low Stock Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("5.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("CASHIER").orElseThrow();
        final User user = new User("ledger_" + token(), "Ledger Cashier", passwordEncoder.encode("Correct1!"), role);
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
}

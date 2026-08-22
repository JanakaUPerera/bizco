package com.bizco.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.customer.application.CustomerService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.inventory.application.StockAdjustmentService;
import com.bizco.server.purchasing.application.GoodsReceiptService;
import com.bizco.server.purchasing.application.PurchaseOrderService;
import com.bizco.server.purchasing.application.SupplierProductService;
import com.bizco.server.purchasing.application.SupplierReturnService;
import com.bizco.server.purchasing.application.SupplierService;
import com.bizco.server.sales.application.HeldSaleService;
import com.bizco.server.sales.application.InvoiceService;
import com.bizco.server.sales.application.PostSaleService;
import com.bizco.server.scheduling.application.JobCardService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Phase 6 Week 18 (DevelopmentPlan.md Week 18 task 18.6, DatabaseDesign.md §56.3 Step 5).
 * Exercises every module's write path this week repointed to variant granularity, then verifies
 * the integrity check §56.3 Step 5 asks for: every non-null {@code product_variant_id} row
 * resolves to a variant whose own {@code product_id} matches the row's own {@code product_id}, for
 * each of the ten affected tables. Covers acceptance IDs VAR-CUTOVER-001..004.
 */
class VariantCutoverIntegrityIT extends PostgresIntegrationTest {

    private static final List<String> VARIANT_TABLES = List.of("invoice_lines", "held_sale_items", "job_parts",
            "stock_movements", "stock_adjustments", "supplier_products", "purchase_order_items",
            "goods_receipt_items", "supplier_return_items", "product_cost_history");

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private PostSaleService postSaleService;
    @Autowired
    private HeldSaleService heldSaleService;
    @Autowired
    private JobCardService jobCardService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private SupplierProductService supplierProductService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private SupplierReturnService supplierReturnService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private User actor;

    @Test
    void varCutover001EveryVariantIdAcrossAllTenTablesResolvesToItsOwnProductId() {
        actor = createUser();
        final ProductDetailResponse product = createProduct();

        // stock_adjustments + stock_movements (ADJUSTMENT)
        final var adjustment = stockAdjustmentService.create(new CreateStockAdjustmentRequest(product.productId(), null,
                "POSITIVE", new BigDecimal("100.000"), "Opening stock"), auth("inventory.adjustment.create"));
        stockAdjustmentService.approve(UUID.randomUUID(), adjustment.stockAdjustmentId(),
                new DecideStockAdjustmentRequest("Confirmed", adjustment.version()), auth("inventory.adjustment.approve"));

        // invoice_lines + stock_movements (SALE)
        final var draft = invoiceService.createDraft(new CreateDraftInvoiceRequest(LocalDate.now(), null, "SALES",
                null, "VAR-CUTOVER"), auth("invoice.create"));
        final var withLine = invoiceService.addLine(draft.invoiceId(), new AddInvoiceLineRequest("PRODUCT", product.productId(), null, null, null, BigDecimal.ONE, product.sellingPrice(), null, DiscountRequest.NONE));
        postSaleService.post(draft.invoiceId(), UUID.randomUUID(), new PostInvoiceRequest(withLine.version(),
                List.of(new PaymentLineRequest("CASH", product.sellingPrice(), null)), false, List.of()),
                auth("invoice.create"));

        // held_sale_items
        heldSaleService.hold(new HoldSaleRequest(null, List.of(new HeldSaleItemRequest(product.productId(), null,
                BigDecimal.ONE, null, null)), null), auth("invoice.hold_bill"));

        // job_parts + stock_movements (JOB_PART)
        final JobCardResponse job = jobCardService.createWalkIn(new CreateJobCardRequest(createCustomer(), null,
                "Phone", "Samsung", "A54", "SN" + token(), "Broken display", null, null, null),
                auth("jobcard.create"));
        jobCardService.addPart(UUID.randomUUID(), job.jobCardId(), new AddJobPartRequest(product.productId(),
                BigDecimal.ONE, null, false), auth("jobcard.parts.add"));

        // supplier_products
        final SupplierDetailResponse supplier = createSupplier();
        supplierProductService.create(new SupplierProductCreateRequest(supplier.supplierId(), product.productId(),
                null, new BigDecimal("50.00"), BigDecimal.ONE, null, true),
                auth("purchasing.supplier_product.create"));

        // purchase_order_items
        final PurchaseOrderDetailResponse po = purchaseOrderService.createDraft(new CreatePurchaseOrderRequest(
                supplier.supplierId(), LocalDate.now(), null, null, "VAR-CUTOVER"), auth("purchasing.po.create"));
        purchaseOrderService.addItem(po.purchaseOrderId(), new AddPurchaseOrderItemRequest(product.productId(),
                new BigDecimal("10.000"), new BigDecimal("50.00")));

        // goods_receipt_items + stock_movements (GRN) + product_cost_history
        final GoodsReceiptDetailResponse grDraft = goodsReceiptService.createDraft(new CreateGoodsReceiptRequest(
                po.purchaseOrderId(), supplier.supplierId(), "REF-" + token(), LocalDate.now(), null),
                auth("purchasing.grn.create"));
        final GoodsReceiptDetailResponse grWithItem = goodsReceiptService.addItem(grDraft.goodsReceiptId(),
                new AddGoodsReceiptItemRequest(null, product.productId(), new BigDecimal("10.000"), null, null,
                        new BigDecimal("50.00")));
        final GoodsReceiptDetailResponse gr = goodsReceiptService.post(UUID.randomUUID(), grWithItem.goodsReceiptId(),
                new PostGoodsReceiptRequest(grWithItem.version()), auth("purchasing.grn.create")).response();

        // supplier_return_items + stock_movements (SUPPLIER_RETURN)
        supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(supplier.supplierId(),
                gr.goodsReceiptId(), "Wrong item shipped",
                List.of(new CreateSupplierReturnItemRequest(gr.items().get(0).goodsReceiptItemId(),
                        new BigDecimal("2.000")))), auth("purchasing.return.create"));

        for (final String table : VARIANT_TABLES) {
            final Long mismatches = jdbc.queryForObject(("""
                    select count(*) from %s t
                    join product_variants pv on pv.product_variant_id = t.product_variant_id
                    where t.product_variant_id is not null and pv.product_id <> t.product_id
                    """).formatted(table), Long.class);
            assertThat(mismatches).as("table %s: product_variant_id must resolve to the row's own product_id", table)
                    .isZero();

            final Long populated = jdbc.queryForObject(
                    ("select count(*) from %s where product_variant_id is not null").formatted(table), Long.class);
            assertThat(populated).as("table %s: this scenario's write path should have populated product_variant_id",
                    table).isGreaterThan(0);
        }
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final CategoryResponse category = catalogService.createCategory(new CategoryCreateRequest("Cutover " + suffix,
                null, null), auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("VCUT-" + suffix, null, "Cutover Widget " + suffix,
                null, category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private SupplierDetailResponse createSupplier() {
        final String suffix = token();
        return supplierService.create(new SupplierCreateRequest("SUP-VCUT-" + suffix, "Cutover Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private UUID createCustomer() {
        return customerService.create(new CustomerCreateRequest("Cutover Customer " + token(), "0771234567", null,
                null, null, null, null, null, "RETAIL", BigDecimal.ZERO, false, false),
                auth("customer.create")).customerId();
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        final User user = new User("vcut_" + token(), "Cutover Test Staff", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private UsernamePasswordAuthenticationToken auth(final String... permissions) {
        return new UsernamePasswordAuthenticationToken(actor.getUsername(), "n/a",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}

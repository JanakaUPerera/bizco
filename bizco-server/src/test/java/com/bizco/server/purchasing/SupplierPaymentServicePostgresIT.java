package com.bizco.server.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptOutstandingResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentAllocationRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/** PUR-PAY-001..004, FIN-AP-001..003 (DevelopmentPlan.md Week 15). */
class SupplierPaymentServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private SupplierPaymentService supplierPaymentService;
    @Autowired
    private SupplierReturnService supplierReturnService;
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

    @Test
    void purPay001PaymentAllocatedToOneReceiptReducesOutstandingAndWritesCashbookEntry() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("10.000"),
                new BigDecimal("20.00")); // total 200.00

        final var result = supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(
                supplier.supplierId(), null, "BANK_TRANSFER", new BigDecimal("150.00"), "TXN-001", null,
                List.of(new SupplierPaymentAllocationRequest(receipt.goodsReceiptId(), new BigDecimal("150.00")))),
                auth(user));

        assertThat(result.replayed()).isFalse();
        final SupplierPaymentResponse response = result.response();
        assertThat(response.allocations()).hasSize(1);
        assertThat(response.allocations().get(0).outstandingAfter()).isEqualByComparingTo("50.00");

        final GoodsReceiptOutstandingResponse outstanding = firstOutstanding(supplier.supplierId());
        assertThat(outstanding.outstandingAmount()).isEqualByComparingTo("50.00");

        final Long cashbookCount = jdbc.queryForObject(
                "select count(*) from cashbook_entries where direction = 'OUT' and source_type = 'SUPPLIER_PAYMENT' "
                        + "and reference_id = ? and amount = 150.00", Long.class, response.supplierPaymentId());
        assertThat(cashbookCount).isEqualTo(1L);
    }

    @Test
    void purPay002PaymentCanSpanMultipleReceipts() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receiptA = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00")); // 50.00
        final GoodsReceiptDetailResponse receiptB = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00")); // 50.00

        final var result = supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(
                supplier.supplierId(), null, "CASH", new BigDecimal("100.00"), null, null,
                List.of(new SupplierPaymentAllocationRequest(receiptA.goodsReceiptId(), new BigDecimal("50.00")),
                        new SupplierPaymentAllocationRequest(receiptB.goodsReceiptId(), new BigDecimal("50.00")))),
                auth(user));

        assertThat(result.response().allocations()).hasSize(2);
        assertThat(result.response().allocations().stream().map(a -> a.outstandingAfter())
                .allMatch(amount -> amount.compareTo(BigDecimal.ZERO) == 0)).isTrue();
    }

    @Test
    void purPay003AllocationCannotExceedOutstandingBalance() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("5.000"),
                new BigDecimal("10.00")); // 50.00

        assertThatThrownBy(() -> supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(
                supplier.supplierId(), null, "CASH", new BigDecimal("100.00"), null, null,
                List.of(new SupplierPaymentAllocationRequest(receipt.goodsReceiptId(), new BigDecimal("100.00")))),
                auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.PAYMENT_ALLOCATION_EXCEEDS_BALANCE);
    }

    @Test
    void purPay004UnallocatedPaymentIsAllowedAsSupplierCredit() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();

        final var result = supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(
                supplier.supplierId(), null, "CASH", new BigDecimal("75.00"), null, "Prepayment", List.of()),
                auth(user));

        assertThat(result.response().allocations()).isEmpty();
        assertThat(result.response().amount()).isEqualByComparingTo("75.00");
    }

    @Test
    void finAp001OutstandingViewReflectsReceiptReturnsAndPayments() {
        final User user = createUser();
        final SupplierDetailResponse supplier = createSupplier();
        final ProductDetailResponse product = createProduct();
        final GoodsReceiptDetailResponse receipt = postedReceipt(user, supplier, product, new BigDecimal("10.000"),
                new BigDecimal("10.00")); // total 100.00

        supplierReturnService.create(UUID.randomUUID(), new CreateSupplierReturnRequest(supplier.supplierId(),
                receipt.goodsReceiptId(), "Damaged",
                List.of(new CreateSupplierReturnItemRequest(receipt.items().get(0).goodsReceiptItemId(),
                        new BigDecimal("2.000")))), auth(user)); // returned 20.00

        supplierPaymentService.record(UUID.randomUUID(), new RecordSupplierPaymentRequest(supplier.supplierId(), null,
                "CASH", new BigDecimal("30.00"), null, null,
                List.of(new SupplierPaymentAllocationRequest(receipt.goodsReceiptId(), new BigDecimal("30.00")))),
                auth(user)); // paid 30.00

        final GoodsReceiptOutstandingResponse outstanding = firstOutstanding(supplier.supplierId());
        assertThat(outstanding.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(outstanding.returnedAmount()).isEqualByComparingTo("20.00");
        assertThat(outstanding.paidAmount()).isEqualByComparingTo("30.00");
        assertThat(outstanding.outstandingAmount()).isEqualByComparingTo("50.00"); // 100 - 20 - 30
    }

    private GoodsReceiptOutstandingResponse firstOutstanding(final UUID supplierId) {
        return goodsReceiptService.outstanding(supplierId, false, 0, 20).data().get(0);
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
        return supplierService.create(new SupplierCreateRequest("SUP-PAY-" + suffix, "Payment Test Supplier " + suffix,
                null, null, null, null, null, null, BigDecimal.ZERO), auth("supplier.create"));
    }

    private ProductDetailResponse createProduct() {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Payment Test " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("PAYI-" + suffix, null, "Payment Widget " + suffix,
                null, category.categoryId(), pcs, "INVENTORY", "STANDARD", new BigDecimal("1.00"),
                new BigDecimal("20.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("STORE_KEEPER").orElseThrow();
        final User user = new User("spay_" + token(), "Payment Test Staff", passwordEncoder.encode("Correct1!"), role);
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
}

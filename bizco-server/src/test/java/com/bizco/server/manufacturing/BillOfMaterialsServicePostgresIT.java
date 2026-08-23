package com.bizco.server.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSearchResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSummaryResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.UpdateBomRequest;
import com.bizco.server.catalog.application.CatalogService;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.ApiValidationException;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.manufacturing.application.BillOfMaterialsService;
import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BOM-001..004 (DevelopmentPlan.md Week 20 task 20.5). */
class BillOfMaterialsServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private BillOfMaterialsService bomService;
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
    void bom001CreateBomAddItemsRollsUpCost() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID glass = variantOf(createProduct("3.50"));
        final UUID finished = variantOf(createProduct("0.00"));

        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        final BomDetailResponse withFrame = bomService.addItem(bom.bomId(),
                new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));
        final BomDetailResponse withGlass = bomService.addItem(withFrame.bomId(),
                new BomItemRequest(glass, new BigDecimal("2.000"), new BigDecimal("0.500")), auth(user));

        assertThat(withGlass.items()).hasSize(2);
        // 1 * 8.00 + 2 * 3.50 = 15.00 (wastage excluded from the estimate, SRS.md §6.4.11.4).
        assertThat(withGlass.totalEstimatedCost()).isEqualByComparingTo("15.00");
    }

    @Test
    void bom002UpdateAndRemoveItem() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));
        final BomDetailResponse withItem = bomService.addItem(bom.bomId(),
                new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));
        final UUID itemId = withItem.items().get(0).bomItemId();

        final BomDetailResponse updated = bomService.updateItem(bom.bomId(), itemId,
                new BomItemRequest(frame, new BigDecimal("2.000"), null), auth(user));
        assertThat(updated.items().get(0).quantity()).isEqualByComparingTo("2.000");

        final BomDetailResponse removed = bomService.removeItem(bom.bomId(), itemId, auth(user));
        assertThat(removed.items()).isEmpty();
    }

    @Test
    void bom003DuplicateFinishedVariantAndDuplicateComponentAreRejected() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));

        assertThatThrownBy(() -> bomService.create(new CreateBomRequest(finished, "Duplicate"), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BOM_ALREADY_EXISTS_FOR_VARIANT);

        bomService.addItem(bom.bomId(), new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user));
        assertThatThrownBy(() -> bomService.addItem(bom.bomId(),
                new BomItemRequest(frame, new BigDecimal("1.000"), null), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BOM_ITEM_DUPLICATE_COMPONENT);
    }

    @Test
    void bom004DirectAndTransitiveCircularReferencesAreRejected() {
        final User user = createUser();
        final UUID finishedA = variantOf(createProduct("0.00"));
        final UUID finishedB = variantOf(createProduct("0.00"));
        final BomDetailResponse bomA = bomService.create(new CreateBomRequest(finishedA, "A"), auth(user));

        // Direct: A's own finished variant cannot be a component of A.
        assertThatThrownBy(() -> bomService.addItem(bomA.bomId(),
                new BomItemRequest(finishedA, new BigDecimal("1.000"), null), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BOM_ITEM_CIRCULAR_REFERENCE);

        // Transitive: B is built from A, so A cannot then take B as a component.
        final BomDetailResponse bomB = bomService.create(new CreateBomRequest(finishedB, "B"), auth(user));
        bomService.addItem(bomB.bomId(), new BomItemRequest(finishedA, new BigDecimal("1.000"), null), auth(user));
        assertThatThrownBy(() -> bomService.addItem(bomA.bomId(),
                new BomItemRequest(finishedB, new BigDecimal("1.000"), null), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BOM_ITEM_CIRCULAR_REFERENCE);
    }

    @Test
    void bomUpdateEnforcesOptimisticLocking() {
        final User user = createUser();
        final UUID finished = variantOf(createProduct("0.00"));
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));

        assertThatThrownBy(() -> bomService.update(bom.bomId(), new UpdateBomRequest("Renamed", true, 99L), auth(user)))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void addItemRejectsNonPositiveQuantity() {
        final User user = createUser();
        final UUID frame = variantOf(createProduct("8.00"));
        final UUID finished = variantOf(createProduct("0.00"));
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "Framed Photo"), auth(user));

        assertThatThrownBy(() -> bomService.addItem(bom.bomId(),
                new BomItemRequest(frame, BigDecimal.ZERO, null), auth(user)))
                .isInstanceOf(ApiValidationException.class);
    }

    @Test
    void bom005SearchFiltersByActiveOnly() {
        final User user = createUser();
        final UUID finishedActive = variantOf(createProduct("0.00"));
        final UUID finishedInactive = variantOf(createProduct("0.00"));
        final BomDetailResponse active = bomService.create(new CreateBomRequest(finishedActive, "Active " + token()),
                auth(user));
        final BomDetailResponse inactive = bomService.create(
                new CreateBomRequest(finishedInactive, "Inactive " + token()), auth(user));
        bomService.update(inactive.bomId(), new UpdateBomRequest(inactive.name(), false, inactive.version()),
                auth(user));

        // Exercises BillOfMaterialsRepository.search's "(:activeOnly = false or b.active = true)"
        // idiom directly - this is the only test that proves it filters, not just compiles.
        final BomSearchResponse activeOnly = bomService.search(true, 0, 100);
        assertThat(activeOnly.data()).extracting(BomSummaryResponse::bomId).contains(active.bomId())
                .doesNotContain(inactive.bomId());

        final BomSearchResponse all = bomService.search(false, 0, 100);
        assertThat(all.data()).extracting(BomSummaryResponse::bomId).contains(active.bomId(), inactive.bomId());
    }

    @Test
    void bom006ByFinishedVariantReturnsMatchAndThrowsWhenMissing() {
        final User user = createUser();
        final UUID finished = variantOf(createProduct("0.00"));
        final BomDetailResponse bom = bomService.create(new CreateBomRequest(finished, "By Variant Lookup"),
                auth(user));

        final BomDetailResponse found = bomService.byFinishedVariant(finished);
        assertThat(found.bomId()).isEqualTo(bom.bomId());

        final UUID variantWithNoBom = variantOf(createProduct("0.00"));
        assertThatThrownBy(() -> bomService.byFinishedVariant(variantWithNoBom))
                .isInstanceOf(IdentityException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BOM_NOT_FOUND);
    }

    private UUID variantOf(final ProductDetailResponse product) {
        return jdbc.queryForObject(
                "select product_variant_id from product_variants where product_id = ? and is_default = true",
                UUID.class, product.productId());
    }

    private ProductDetailResponse createProduct(final String costPrice) {
        final String suffix = token();
        final var category = catalogService.createCategory(new CategoryCreateRequest("Bom " + suffix, null, null),
                auth("product.category.create"));
        final Long pcs = jdbc.queryForObject("select uom_id from uom where code = 'PCS'", Long.class);
        return catalogService.createProduct(new ProductCreateRequest("BOM-" + suffix, null, "Bom Part " + suffix, null,
                category.categoryId(), null, pcs, "INVENTORY", "STANDARD", new BigDecimal(costPrice),
                new BigDecimal("100.00"), null, new BigDecimal("2.000"), null), auth("product.create"));
    }

    private User createUser() {
        final Role role = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        final User user = new User("bom_" + token(), "Bom Tester", passwordEncoder.encode("Correct1!"), role);
        return userRepository.saveAndFlush(user);
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsernamePasswordAuthenticationToken auth(final User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a",
                java.util.List.of(new SimpleGrantedAuthority("manufacturing.bom.manage")));
    }

    private UsernamePasswordAuthenticationToken auth(final String permission) {
        return new UsernamePasswordAuthenticationToken("superadmin", "n/a", java.util.List.of(new SimpleGrantedAuthority(permission)));
    }
}

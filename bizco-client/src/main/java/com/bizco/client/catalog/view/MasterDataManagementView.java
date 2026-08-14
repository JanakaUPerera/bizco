package com.bizco.client.catalog.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.purchasing.service.SupplierApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ProductDetailResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceUpdateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.UomResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierCreateRequest;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierDetailResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierUpdateRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class MasterDataManagementView {

    private static final int PAGE_SIZE = 20;

    private final CatalogApiClient catalogApi;
    private final SupplierApiClient supplierApi;
    private final boolean canProductCreate;
    private final boolean canProductUpdate;
    private final boolean canProductDelete;
    private final boolean canViewCost;
    private final boolean canCategoryChange;
    private final boolean canServiceChange;
    private final boolean canSupplierCreate;
    private final boolean canSupplierUpdate;
    private final boolean canSupplierDeactivate;
    private final ProductPane productPane = new ProductPane();
    private final CategoryPane categoryPane = new CategoryPane();
    private final ServicePane servicePane = new ServicePane();
    private final SupplierPane supplierPane = new SupplierPane();

    public MasterDataManagementView(final CatalogApiClient catalogApi, final SupplierApiClient supplierApi,
                                    final boolean canProductCreate, final boolean canProductUpdate,
                                    final boolean canProductDelete, final boolean canViewCost,
                                    final boolean canCategoryChange, final boolean canServiceChange,
                                    final boolean canSupplierCreate, final boolean canSupplierUpdate,
                                    final boolean canSupplierDeactivate) {
        this.catalogApi = catalogApi;
        this.supplierApi = supplierApi;
        this.canProductCreate = canProductCreate;
        this.canProductUpdate = canProductUpdate;
        this.canProductDelete = canProductDelete;
        this.canViewCost = canViewCost;
        this.canCategoryChange = canCategoryChange;
        this.canServiceChange = canServiceChange;
        this.canSupplierCreate = canSupplierCreate;
        this.canSupplierUpdate = canSupplierUpdate;
        this.canSupplierDeactivate = canSupplierDeactivate;
    }

    public Parent createView() {
        final TabPane tabs = new TabPane(
                tab("Products", productPane.create()),
                tab("Categories", categoryPane.create()),
                tab("Services", servicePane.create()),
                tab("Suppliers", supplierPane.create()));
        tabs.getStyleClass().add("content-surface");
        refreshReferences();
        productPane.load(0);
        servicePane.load(0);
        supplierPane.load(0);
        return tabs;
    }

    private void refreshReferences() {
        UiSupport.onFx(catalogApi.categories(), categories -> {
            categoryPane.setCategories(categories);
            productPane.setCategories(categories);
        }, "Categories could not be loaded.");
        UiSupport.onFx(catalogApi.uom(), productPane::setUom, "UOM could not be loaded.");
    }

    private Tab tab(final String title, final Parent content) {
        final Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private class ProductPane {
        private final TableView<ProductSummaryResponse> table = new TableView<>();
        private final TextField search = new TextField();
        private final ComboBox<CategoryResponse> categoryFilter = new ComboBox<>();
        private final ComboBox<String> typeFilter = new ComboBox<>();
        private final ComboBox<String> activeFilter = new ComboBox<>();
        private final Pagination pagination = new Pagination(1, 0);
        private final Label state = new Label("Loading products...");
        private final TextField sku = new TextField();
        private final TextField barcode = new TextField();
        private final TextField name = new TextField();
        private final TextArea description = new TextArea();
        private final ComboBox<CategoryResponse> category = new ComboBox<>();
        private final ComboBox<UomResponse> uom = new ComboBox<>();
        private final ComboBox<String> type = new ComboBox<>();
        private final ComboBox<String> tax = new ComboBox<>();
        private final TextField cost = new TextField("0.00");
        private final TextField retail = new TextField("0.00");
        private final TextField wholesale = new TextField();
        private final TextField reorder = new TextField("0.000");
        private final CheckBox active = new CheckBox("Active");
        private final Button save = Icons.button("Save", FontAwesomeSolid.SAVE);
        private final Button deactivate = Icons.button("Deactivate", FontAwesomeSolid.BAN);
        private final Button activate = Icons.button("Activate", FontAwesomeSolid.CHECK_CIRCLE);
        private ProductDetailResponse selected;

        Parent create() {
            configureTable();
            typeFilter.getItems().setAll("", "INVENTORY", "SERVICE");
            activeFilter.getItems().setAll("", "true", "false");
            type.getItems().setAll("INVENTORY", "SERVICE");
            tax.getItems().setAll("STANDARD", "EXEMPT", "ZERO_RATED");
            active.setSelected(true);
            save.setOnAction(event -> save());
            deactivate.setOnAction(event -> productAction(false));
            activate.setOnAction(event -> productAction(true));
            final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
            final Button add = Icons.button("New", FontAwesomeSolid.PLUS);
            add.setDisable(!canProductCreate);
            add.setOnAction(event -> clearProduct());
            searchButton.setOnAction(event -> load(0));
            pagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> load(newValue.intValue()));
            final HBox header = new HBox(10, UiSupport.label("Products", "screen-title"), spacer(), search,
                    categoryFilter, typeFilter, activeFilter, searchButton, add);
            header.getStyleClass().add("screen-header");
            final BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(new VBox(8, state, table, pagination));
            root.setRight(productForm());
            return root;
        }

        void setCategories(final List<CategoryResponse> categories) {
            category.getItems().setAll(categories);
            categoryFilter.getItems().setAll(categories);
            category.setConverter(categoryConverter());
            categoryFilter.setConverter(categoryConverter());
        }

        void setUom(final List<UomResponse> values) {
            uom.getItems().setAll(values);
            uom.setConverter(new javafx.util.StringConverter<>() {
                public String toString(final UomResponse value) { return value == null ? "" : value.code(); }
                public UomResponse fromString(final String value) { return null; }
            });
        }

        void load(final int page) {
            final Boolean activeValue = activeFilter.getValue() == null || activeFilter.getValue().isBlank()
                    ? null : Boolean.valueOf(activeFilter.getValue());
            final Long categoryId = categoryFilter.getValue() == null ? null : categoryFilter.getValue().categoryId();
            UiSupport.onFx(catalogApi.products(search.getText(), categoryId, typeFilter.getValue(), activeValue,
                    page, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                pagination.setPageCount(Math.max(result.totalPages(), 1));
                pagination.setCurrentPageIndex(result.page());
                state.setText(result.totalElements() == 0 ? "No products found." : result.totalElements() + " products");
            }, "Products could not be loaded.");
        }

        private VBox productForm() {
            final GridPane form = grid();
            int row = 0;
            addRow(form, row++, "SKU", sku);
            addRow(form, row++, "Barcode", barcode);
            addRow(form, row++, "Name", name);
            addRow(form, row++, "Description", description);
            addRow(form, row++, "Category", category);
            addRow(form, row++, "UOM", uom);
            addRow(form, row++, "Type", type);
            addRow(form, row++, "Tax", tax);
            addRow(form, row++, "Cost", cost);
            addRow(form, row++, "Retail", retail);
            addRow(form, row++, "Wholesale", wholesale);
            addRow(form, row++, "Reorder", reorder);
            form.add(active, 1, row);
            final HBox buttons = new HBox(8, save, deactivate, activate);
            final VBox panel = new VBox(12, UiSupport.label("Product Detail", "panel-title"), form, buttons);
            panel.getStyleClass().add("side-panel");
            panel.setPadding(new Insets(16));
            panel.setPrefWidth(430);
            return panel;
        }

        private void configureTable() {
            table.getColumns().setAll(
                    column("SKU", ProductSummaryResponse::sku),
                    column("Barcode", p -> nullToBlank(p.barcode())),
                    column("Name", ProductSummaryResponse::name),
                    column("Category", ProductSummaryResponse::categoryName),
                    column("UOM", ProductSummaryResponse::uomCode),
                    column("Type", ProductSummaryResponse::productType),
                    column("Tax", ProductSummaryResponse::taxCategory),
                    column("Retail", p -> text(p.sellingPrice())),
                    column("Wholesale", p -> text(p.wholesalePrice())),
                    column("Cost", p -> canViewCost ? text(p.costPrice()) : ""),
                    column("Status", p -> p.active() ? "ACTIVE" : "INACTIVE"));
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, p) -> {
                if (p != null) UiSupport.onFx(catalogApi.getProduct(p.productId()), this::selectProduct,
                        "Product detail could not be loaded.");
            });
            VBox.setVgrow(table, Priority.ALWAYS);
        }

        private void selectProduct(final ProductDetailResponse p) {
            selected = p;
            sku.setText(p.sku());
            barcode.setText(nullToBlank(p.barcode()));
            name.setText(p.name());
            description.setText(nullToBlank(p.description()));
            category.getItems().stream().filter(c -> c.categoryId().equals(p.categoryId())).findFirst().ifPresent(category::setValue);
            uom.getItems().stream().filter(value -> value.uomId().equals(p.uomId())).findFirst().ifPresent(uom::setValue);
            type.setValue(p.productType());
            tax.setValue(p.taxCategory());
            cost.setText(p.costPrice() == null ? "" : p.costPrice().toPlainString());
            retail.setText(p.sellingPrice().toPlainString());
            wholesale.setText(text(p.wholesalePrice()));
            reorder.setText(p.reorderPoint().toPlainString());
            active.setSelected(p.active());
            save.setDisable(!canProductUpdate);
            deactivate.setDisable(!canProductDelete || !p.active());
            activate.setDisable(!canProductUpdate || p.active());
        }

        private void clearProduct() {
            selected = null;
            sku.clear();
            barcode.clear();
            name.clear();
            description.clear();
            category.setValue(null);
            uom.setValue(null);
            type.setValue("INVENTORY");
            tax.setValue("STANDARD");
            cost.setText("0.00");
            retail.setText("0.00");
            wholesale.clear();
            reorder.setText("0.000");
            active.setSelected(true);
            save.setDisable(!canProductCreate);
            deactivate.setDisable(true);
            activate.setDisable(true);
        }

        private void save() {
            try {
                if (selected == null) {
                    UiSupport.onFx(catalogApi.createProduct(new ProductCreateRequest(sku.getText(), barcode.getText(),
                            name.getText(), description.getText(), id(category.getValue()), id(uom.getValue()),
                            type.getValue(), tax.getValue(), decimal(cost), decimal(retail), decimalOrNull(wholesale),
                            decimal(reorder), null)), p -> { selectProduct(p); load(0); }, "Product could not be created.");
                    return;
                }
                UiSupport.onFx(catalogApi.updateProduct(selected.productId(), new ProductUpdateRequest(sku.getText(),
                        barcode.getText(), name.getText(), description.getText(), id(category.getValue()),
                        id(uom.getValue()), type.getValue(), tax.getValue(), decimal(cost), decimal(retail),
                        decimalOrNull(wholesale), decimal(reorder), active.isSelected(), null, selected.version())),
                        p -> { selectProduct(p); load(pagination.getCurrentPageIndex()); }, "Product could not be saved.");
            } catch (final RuntimeException ex) {
                UiSupport.alert("Numeric fields must contain valid decimal values.");
            }
        }

        private void productAction(final boolean makeActive) {
            if (selected == null) return;
            UiSupport.onFx(makeActive ? catalogApi.activateProduct(selected.productId())
                            : catalogApi.deactivateProduct(selected.productId()),
                    p -> { selectProduct(p); load(pagination.getCurrentPageIndex()); },
                    "Product status could not be changed.");
        }
    }

    private class CategoryPane {
        private final TableView<CategoryResponse> table = new TableView<>();
        private final TextField name = new TextField();
        private final ComboBox<CategoryResponse> parent = new ComboBox<>();
        private final TextArea description = new TextArea();
        private final CheckBox active = new CheckBox("Active");
        private CategoryResponse selected;

        Parent create() {
            table.getColumns().setAll(column("Name", CategoryResponse::name), column("Parent", c -> nullToBlank(c.parentName())),
                    column("Status", c -> c.active() ? "ACTIVE" : "INACTIVE"));
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> selectCategory(c));
            final Button save = Icons.button("Save", FontAwesomeSolid.SAVE);
            final Button add = Icons.button("New", FontAwesomeSolid.PLUS);
            save.setDisable(!canCategoryChange);
            add.setDisable(!canCategoryChange);
            add.setOnAction(event -> selectCategory(null));
            save.setOnAction(event -> saveCategory());
            final GridPane form = grid();
            addRow(form, 0, "Name", name);
            addRow(form, 1, "Parent", parent);
            addRow(form, 2, "Description", description);
            form.add(active, 1, 3);
            final BorderPane root = new BorderPane(table);
            root.setTop(new HBox(10, UiSupport.label("Categories", "screen-title"), spacer(), add));
            root.setRight(new VBox(12, UiSupport.label("Category Detail", "panel-title"), form, save));
            return root;
        }

        void setCategories(final List<CategoryResponse> categories) {
            table.setItems(FXCollections.observableArrayList(categories));
            parent.getItems().setAll(categories);
            parent.setConverter(categoryConverter());
        }

        private void selectCategory(final CategoryResponse c) {
            selected = c;
            name.setText(c == null ? "" : c.name());
            description.setText(c == null ? "" : nullToBlank(c.description()));
            active.setSelected(c == null || c.active());
            parent.setValue(null);
            if (c != null && c.parentId() != null) {
                parent.getItems().stream().filter(p -> p.categoryId().equals(c.parentId())).findFirst().ifPresent(parent::setValue);
            }
        }

        private void saveCategory() {
            if (selected == null) {
                UiSupport.onFx(catalogApi.createCategory(new CategoryCreateRequest(name.getText(), id(parent.getValue()),
                        description.getText())), ignored -> refreshReferences(), "Category could not be created.");
                return;
            }
            UiSupport.onFx(catalogApi.updateCategory(selected.categoryId(), new CategoryUpdateRequest(name.getText(),
                    id(parent.getValue()), description.getText(), active.isSelected(), selected.version())),
                    ignored -> refreshReferences(), "Category could not be saved.");
        }
    }

    private class ServicePane {
        private final TableView<ServiceResponse> table = new TableView<>();
        private final TextField search = new TextField();
        private final Pagination pagination = new Pagination(1, 0);
        private final TextField code = new TextField();
        private final TextField name = new TextField();
        private final TextField category = new TextField();
        private final TextField price = new TextField("0.00");
        private final TextField duration = new TextField("60");
        private final CheckBox estimate = new CheckBox("Requires estimate");
        private final TextField warranty = new TextField("0");
        private ServiceResponse selected;

        Parent create() {
            table.getColumns().setAll(column("Code", ServiceResponse::serviceCode), column("Name", ServiceResponse::name),
                    column("Category", s -> nullToBlank(s.category())), column("Price", s -> text(s.basePrice())),
                    column("Duration", s -> Integer.toString(s.estimatedDurationMinutes())),
                    column("Status", s -> s.active() ? "ACTIVE" : "INACTIVE"));
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, s) -> selectService(s));
            final Button find = Icons.button("Search", FontAwesomeSolid.SEARCH);
            final Button add = Icons.button("New", FontAwesomeSolid.PLUS);
            final Button save = Icons.button("Save", FontAwesomeSolid.SAVE);
            final Button deactivate = Icons.button("Deactivate", FontAwesomeSolid.BAN);
            final Button activate = Icons.button("Activate", FontAwesomeSolid.CHECK_CIRCLE);
            save.setDisable(!canServiceChange);
            add.setDisable(!canServiceChange);
            deactivate.setDisable(!canServiceChange);
            activate.setDisable(!canServiceChange);
            find.setOnAction(event -> load(0));
            add.setOnAction(event -> selectService(null));
            save.setOnAction(event -> saveService());
            deactivate.setOnAction(event -> serviceStatus(false));
            activate.setOnAction(event -> serviceStatus(true));
            pagination.currentPageIndexProperty().addListener((obs, old, page) -> load(page.intValue()));
            final GridPane form = grid();
            addRow(form, 0, "Code", code); addRow(form, 1, "Name", name); addRow(form, 2, "Category", category);
            addRow(form, 3, "Base Price", price); addRow(form, 4, "Minutes", duration); form.add(estimate, 1, 5);
            addRow(form, 6, "Warranty Days", warranty);
            final BorderPane root = new BorderPane(new VBox(8, table, pagination));
            root.setTop(new HBox(10, UiSupport.label("Services", "screen-title"), spacer(), search, find, add));
            root.setRight(new VBox(12, UiSupport.label("Service Detail", "panel-title"), form,
                    new HBox(8, save, deactivate, activate)));
            return root;
        }

        void load(final int page) {
            UiSupport.onFx(catalogApi.services(search.getText(), null, page, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                pagination.setPageCount(Math.max(result.totalPages(), 1));
            }, "Services could not be loaded.");
        }

        private void selectService(final ServiceResponse s) {
            selected = s;
            code.setText(s == null ? "" : s.serviceCode());
            name.setText(s == null ? "" : s.name());
            category.setText(s == null ? "" : nullToBlank(s.category()));
            price.setText(s == null ? "0.00" : s.basePrice().toPlainString());
            duration.setText(s == null ? "60" : Integer.toString(s.estimatedDurationMinutes()));
            estimate.setSelected(s != null && s.requiresEstimate());
            warranty.setText(s == null ? "0" : Integer.toString(s.warrantyDays()));
        }

        private void saveService() {
            if (selected == null) {
                UiSupport.onFx(catalogApi.createService(new ServiceCreateRequest(code.getText(), name.getText(), null,
                        category.getText(), new BigDecimal(price.getText()), Integer.valueOf(duration.getText()),
                        estimate.isSelected(), Integer.valueOf(warranty.getText()))), ignored -> load(0),
                        "Service could not be created.");
                return;
            }
            UiSupport.onFx(catalogApi.updateService(selected.serviceId(), new ServiceUpdateRequest(code.getText(),
                    name.getText(), null, category.getText(), new BigDecimal(price.getText()),
                    Integer.valueOf(duration.getText()), estimate.isSelected(), Integer.valueOf(warranty.getText()),
                    selected.active(), selected.version())), ignored -> load(pagination.getCurrentPageIndex()),
                    "Service could not be saved.");
        }

        private void serviceStatus(final boolean makeActive) {
            if (selected == null) return;
            UiSupport.onFx(makeActive ? catalogApi.activateService(selected.serviceId())
                            : catalogApi.deactivateService(selected.serviceId()),
                    ignored -> load(pagination.getCurrentPageIndex()), "Service status could not be changed.");
        }
    }

    private class SupplierPane {
        private final TableView<SupplierSummaryResponse> table = new TableView<>();
        private final TextField search = new TextField();
        private final Pagination pagination = new Pagination(1, 0);
        private final TextField code = new TextField();
        private final TextField name = new TextField();
        private final TextField contact = new TextField();
        private final TextField phone = new TextField();
        private final TextField email = new TextField();
        private final TextField opening = new TextField("0.00");
        private SupplierDetailResponse selected;

        Parent create() {
            table.getColumns().setAll(column("Code", SupplierSummaryResponse::supplierCode),
                    column("Name", SupplierSummaryResponse::name), column("Contact", s -> nullToBlank(s.contactPerson())),
                    column("Phone", s -> nullToBlank(s.phone())), column("Opening", s -> text(s.openingBalance())),
                    column("Status", SupplierSummaryResponse::status));
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, s) -> {
                if (s != null) UiSupport.onFx(supplierApi.get(s.supplierId()), this::selectSupplier,
                        "Supplier detail could not be loaded.");
            });
            final Button find = Icons.button("Search", FontAwesomeSolid.SEARCH);
            final Button add = Icons.button("New", FontAwesomeSolid.PLUS);
            final Button save = Icons.button("Save", FontAwesomeSolid.SAVE);
            final Button deactivate = Icons.button("Deactivate", FontAwesomeSolid.BAN);
            final Button activate = Icons.button("Activate", FontAwesomeSolid.CHECK_CIRCLE);
            add.setDisable(!canSupplierCreate); save.setDisable(!canSupplierUpdate);
            deactivate.setDisable(!canSupplierDeactivate); activate.setDisable(!canSupplierUpdate);
            find.setOnAction(event -> load(0)); add.setOnAction(event -> selectSupplier(null));
            save.setOnAction(event -> saveSupplier()); deactivate.setOnAction(event -> supplierStatus(false));
            activate.setOnAction(event -> supplierStatus(true));
            pagination.currentPageIndexProperty().addListener((obs, old, page) -> load(page.intValue()));
            final GridPane form = grid();
            addRow(form, 0, "Code", code); addRow(form, 1, "Name", name); addRow(form, 2, "Contact", contact);
            addRow(form, 3, "Phone", phone); addRow(form, 4, "Email", email); addRow(form, 5, "Opening", opening);
            final BorderPane root = new BorderPane(new VBox(8, table, pagination));
            root.setTop(new HBox(10, UiSupport.label("Suppliers", "screen-title"), spacer(), search, find, add));
            root.setRight(new VBox(12, UiSupport.label("Supplier Detail", "panel-title"), form,
                    new HBox(8, save, deactivate, activate)));
            return root;
        }

        void load(final int page) {
            UiSupport.onFx(supplierApi.search(search.getText(), null, page, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                pagination.setPageCount(Math.max(result.totalPages(), 1));
            }, "Suppliers could not be loaded.");
        }

        private void selectSupplier(final SupplierDetailResponse s) {
            selected = s;
            code.setText(s == null ? "" : s.supplierCode());
            name.setText(s == null ? "" : s.name());
            contact.setText(s == null ? "" : nullToBlank(s.contactPerson()));
            phone.setText(s == null ? "" : nullToBlank(s.phone()));
            email.setText(s == null ? "" : nullToBlank(s.email()));
            opening.setText(s == null ? "0.00" : s.openingBalance().toPlainString());
        }

        private void saveSupplier() {
            if (selected == null) {
                UiSupport.onFx(supplierApi.create(new SupplierCreateRequest(code.getText(), name.getText(),
                        contact.getText(), null, phone.getText(), email.getText(), null, null,
                        new BigDecimal(opening.getText()))), ignored -> load(0), "Supplier could not be created.");
                return;
            }
            UiSupport.onFx(supplierApi.update(selected.supplierId(), new SupplierUpdateRequest(code.getText(),
                    name.getText(), contact.getText(), null, phone.getText(), email.getText(), null, null,
                    new BigDecimal(opening.getText()), selected.status(), selected.version())),
                    ignored -> load(pagination.getCurrentPageIndex()), "Supplier could not be saved.");
        }

        private void supplierStatus(final boolean makeActive) {
            if (selected == null) return;
            UiSupport.onFx(makeActive ? supplierApi.activate(selected.supplierId()) : supplierApi.deactivate(selected.supplierId()),
                    ignored -> load(pagination.getCurrentPageIndex()), "Supplier status could not be changed.");
        }
    }

    private GridPane grid() {
        final GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(16));
        return form;
    }

    private <T> TableColumn<T, String> column(final String title, final java.util.function.Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(120);
        return column;
    }

    private void addRow(final GridPane grid, final int row, final String label, final javafx.scene.Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
    }

    private javafx.util.StringConverter<CategoryResponse> categoryConverter() {
        return new javafx.util.StringConverter<>() {
            public String toString(final CategoryResponse value) { return value == null ? "" : value.name(); }
            public CategoryResponse fromString(final String value) { return null; }
        };
    }

    private Long id(final CategoryResponse value) { return value == null ? null : value.categoryId(); }
    private Long id(final UomResponse value) { return value == null ? null : value.uomId(); }
    private BigDecimal decimal(final TextField field) { return new BigDecimal(field.getText().trim()); }
    private BigDecimal decimalOrNull(final TextField field) {
        return field.getText() == null || field.getText().isBlank() ? null : decimal(field);
    }
    private String text(final BigDecimal value) { return value == null ? "" : value.toPlainString(); }
    private String nullToBlank(final String value) { return value == null ? "" : value; }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

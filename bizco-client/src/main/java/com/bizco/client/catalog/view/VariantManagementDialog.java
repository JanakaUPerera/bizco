package com.bizco.client.catalog.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.catalog.service.VariantApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.AttributeValueResponse;
import com.bizco.common.dto.catalog.CatalogDtos.CategoryAttributeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantAttributeSelection;
import com.bizco.common.dto.catalog.CatalogDtos.VariantCreateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantGenerateRequest;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantUpdateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/** Phase 6 Week 17 (DevelopmentPlan.md Week 17 task 17.5): variant list/CRUD for one product,
 *  plus attribute-driven cartesian generation (Color × Size) restricted to the ENUM attributes
 *  Week 16 assigned to the product's category. Follows {@code SupplierReturnDialog}'s modal-
 *  {@link Stage} pattern. */
class VariantManagementDialog {

    private final VariantApiClient variantApi;
    private final CatalogApiClient catalogApi;
    private final UUID productId;
    private final Long categoryId;
    private final boolean canChange;
    private final TableView<VariantResponse> table = new TableView<>();
    private final TextField sku = new TextField();
    private final TextField barcode = new TextField();
    private final TextField label = new TextField();
    private final TextField cost = new TextField("0.00");
    private final TextField selling = new TextField("0.00");
    private final TextField wholesale = new TextField();
    private final TextField reorder = new TextField("0.000");
    private final TextField imagePath = new TextField();
    private final ImageView imagePreview = new ImageView();
    private final CheckBox active = new CheckBox("Active");
    private final VBox generatePanel = new VBox(8);
    private final Label generateCount = new Label();
    private final List<AttributeAxis> axes = new ArrayList<>();
    private VariantResponse selected;
    private Stage stage;

    VariantManagementDialog(final VariantApiClient variantApi, final CatalogApiClient catalogApi,
                            final UUID productId, final Long categoryId, final boolean canChange) {
        this.variantApi = variantApi;
        this.catalogApi = catalogApi;
        this.productId = productId;
        this.categoryId = categoryId;
        this.canChange = canChange;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Product Variants");
        stage.initModality(Modality.APPLICATION_MODAL);

        table.getColumns().setAll(
                column("Label", v -> v.variantLabel() == null ? "" : v.variantLabel()),
                column("SKU", VariantResponse::sku),
                column("Barcode", v -> v.barcode() == null ? "" : v.barcode()),
                column("Selling", v -> v.sellingPrice() == null ? "" : v.sellingPrice().toPlainString()),
                column("Status", v -> v.active() ? "ACTIVE" : "INACTIVE"),
                column("Default", v -> v.defaultVariant() ? "YES" : ""));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, v) -> select(v));

        final Button addButton = Icons.button("New", FontAwesomeSolid.PLUS);
        final Button saveButton = Icons.button("Save", FontAwesomeSolid.SAVE);
        addButton.setDisable(!canChange);
        saveButton.setDisable(!canChange);
        addButton.setOnAction(event -> select(null));
        saveButton.setOnAction(event -> save());

        final GridPane form = grid();
        addRow(form, 0, "Label", label);
        addRow(form, 1, "SKU", sku);
        addRow(form, 2, "Barcode", barcode);
        addRow(form, 3, "Cost", cost);
        addRow(form, 4, "Selling", selling);
        addRow(form, 5, "Wholesale", wholesale);
        addRow(form, 6, "Reorder", reorder);
        addRow(form, 7, "Image Path", imagePath);
        form.add(active, 1, 8);
        imagePreview.setFitWidth(80);
        imagePreview.setFitHeight(80);
        imagePreview.setPreserveRatio(true);
        imagePath.textProperty().addListener((obs, old, path) -> loadThumbnail(imagePreview, path));
        final VBox detail = new VBox(10, UiSupport.label("Variant Detail", "panel-title"), form, imagePreview,
                saveButton);
        detail.setPadding(new Insets(16));
        detail.setPrefWidth(360);

        final Button generateButton = Icons.button("Generate Variants", FontAwesomeSolid.MAGIC);
        generateButton.setDisable(!canChange);
        generateButton.setOnAction(event -> generate());
        generatePanel.getChildren().add(UiSupport.label("Generate from category attributes", "panel-title"));
        loadGenerationAxes();

        final VBox generateBox = new VBox(10, generatePanel, generateCount, generateButton);
        generateBox.setPadding(new Insets(16));
        generateBox.setPrefWidth(320);

        final BorderPane root = new BorderPane(table);
        root.setTop(new HBox(10, UiSupport.label("Variants", "screen-title"), spacer(), addButton));
        root.setRight(new HBox(0, detail, generateBox));

        stage.setScene(new Scene(root, 1080, 560));
        load();
        stage.show();
    }

    private void load() {
        UiSupport.onFx(variantApi.variants(productId), variants -> {
            table.setItems(FXCollections.observableArrayList(variants));
            select(null);
        }, "Variants could not be loaded.");
    }

    private void loadGenerationAxes() {
        if (categoryId == null) {
            generatePanel.getChildren().add(new Label("Product has no category."));
            return;
        }
        UiSupport.onFx(catalogApi.categoryAttributes(categoryId), assignments -> {
            for (final CategoryAttributeResponse assignment : assignments) {
                if (!"ENUM".equals(assignment.dataType())) {
                    continue; // only ENUM attributes have a discrete value list to generate from
                }
                final ListView<AttributeValueResponse> values = new ListView<>();
                values.setPrefHeight(90);
                values.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                values.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
                    @Override
                    protected void updateItem(final AttributeValueResponse item, final boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.value());
                    }
                });
                UiSupport.onFx(catalogApi.attributeValues(assignment.attributeId()),
                        list -> values.setItems(FXCollections.observableArrayList(list)),
                        "Attribute values could not be loaded.");
                values.getSelectionModel().getSelectedItems()
                        .addListener((javafx.collections.ListChangeListener<AttributeValueResponse>) change -> updateGenerateCount());
                axes.add(new AttributeAxis(assignment.attributeId(), assignment.attributeName(), values));
                generatePanel.getChildren().addAll(new Label(assignment.attributeName()), values);
            }
            if (axes.isEmpty()) {
                generatePanel.getChildren().add(new Label("No ENUM attributes assigned to this category."));
            }
            updateGenerateCount();
        }, "Category attributes could not be loaded.");
    }

    private void updateGenerateCount() {
        long count = 1;
        boolean any = false;
        for (final AttributeAxis axis : axes) {
            final int selectedCount = axis.values.getSelectionModel().getSelectedItems().size();
            if (selectedCount > 0) {
                count *= selectedCount;
                any = true;
            }
        }
        generateCount.setText(any ? count + " variant(s) will be generated" : "");
    }

    private void generate() {
        final List<VariantAttributeSelection> selections = new ArrayList<>();
        for (final AttributeAxis axis : axes) {
            final List<Long> ids = axis.values.getSelectionModel().getSelectedItems().stream()
                    .map(AttributeValueResponse::attributeValueId).toList();
            if (!ids.isEmpty()) {
                selections.add(new VariantAttributeSelection(axis.attributeId, ids));
            }
        }
        if (selections.isEmpty()) {
            UiSupport.alert("Select at least one value for at least one attribute.");
            return;
        }
        UiSupport.onFx(variantApi.generateVariants(productId, new VariantGenerateRequest(selections)),
                created -> load(), "Variants could not be generated.");
    }

    private void select(final VariantResponse v) {
        selected = v;
        label.setText(v == null ? "" : nullToBlank(v.variantLabel()));
        sku.setText(v == null ? "" : v.sku());
        barcode.setText(v == null ? "" : nullToBlank(v.barcode()));
        cost.setText(v == null ? "0.00" : text(v.costPrice()));
        selling.setText(v == null ? "0.00" : text(v.sellingPrice()));
        wholesale.setText(v == null ? "" : text(v.wholesalePrice()));
        reorder.setText(v == null ? "0.000" : v.reorderPoint().toPlainString());
        imagePath.setText(v == null ? "" : nullToBlank(v.imagePath()));
        active.setSelected(v == null || v.active());
    }

    private void save() {
        try {
            if (selected == null) {
                UiSupport.onFx(variantApi.createVariant(productId, new VariantCreateRequest(sku.getText(),
                        barcode.getText(), label.getText(), decimal(cost), decimal(selling), decimalOrNull(wholesale),
                        decimal(reorder), imagePath.getText())), v -> load(), "Variant could not be created.");
                return;
            }
            UiSupport.onFx(variantApi.updateVariant(productId, selected.productVariantId(), new VariantUpdateRequest(
                    sku.getText(), barcode.getText(), label.getText(), decimal(cost), decimal(selling),
                    decimalOrNull(wholesale), decimal(reorder), active.isSelected(), imagePath.getText(),
                    selected.version())),
                    v -> load(), "Variant could not be saved.");
        } catch (final RuntimeException ex) {
            UiSupport.alert("Numeric fields must contain valid decimal values.");
        }
    }

    private GridPane grid() {
        final GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.setHgap(10);
        form.setVgap(8);
        return form;
    }

    private void addRow(final GridPane grid, final int row, final String text, final javafx.scene.Node field) {
        grid.add(new Label(text), 0, row);
        grid.add(field, 1, row);
    }

    private <T> TableColumn<T, String> column(final String title, final Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(110);
        return column;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private String nullToBlank(final String value) { return value == null ? "" : value; }
    private String text(final BigDecimal value) { return value == null ? "" : value.toPlainString(); }

    /** Phase 6 Week 19 (task 19.4): mirrors {@code MasterDataManagementView.loadThumbnail} - a
     *  free-text path/URL with no upload/storage to validate against, so failures just clear the
     *  preview rather than showing an error. */
    private void loadThumbnail(final ImageView view, final String path) {
        if (path == null || path.isBlank()) {
            view.setImage(null);
            return;
        }
        try {
            final Image image = new Image(path.contains("://") ? path : new java.io.File(path).toURI().toString(),
                    true);
            view.setImage(image);
        } catch (final RuntimeException ex) {
            view.setImage(null);
        }
    }
    private BigDecimal decimal(final TextField field) { return new BigDecimal(field.getText().trim()); }
    private BigDecimal decimalOrNull(final TextField field) {
        return field.getText() == null || field.getText().isBlank() ? null : decimal(field);
    }

    private record AttributeAxis(Long attributeId, String attributeName, ListView<AttributeValueResponse> values) {
    }
}

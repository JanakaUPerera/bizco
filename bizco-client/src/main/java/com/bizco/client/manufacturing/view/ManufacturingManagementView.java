package com.bizco.client.manufacturing.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.catalog.service.VariantApiClient;
import com.bizco.client.manufacturing.service.BillOfMaterialsApiClient;
import com.bizco.client.manufacturing.service.ProductionOrderApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomDetailResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemRequest;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomItemResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.BomSummaryResponse;
import com.bizco.common.dto.manufacturing.BillOfMaterialsDtos.CreateBomRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProduceRequest;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderItemResponse;
import com.bizco.common.dto.manufacturing.ProductionOrderDtos.ProductionOrderSummaryResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/** Bill of Materials management and the Produce transaction (DevelopmentPlan.md Weeks 20-21):
 *  two tabs, mirroring {@code PurchasingManagementView}'s per-workflow-pane shape.
 *
 *  <p>The catalog has no cross-product variant search endpoint - only
 *  {@code GET /products/{id}/variants} (confirmed via {@code VariantController}/{@code
 *  VariantApiClient}). Every finished-product/component picker here therefore resolves a variant
 *  the same two-step way {@code PosView} and {@code PurchasingManagementView}'s product pickers
 *  already do: a typeahead product search ({@code CatalogApiClient.products}), then that
 *  product's variant list ({@code VariantApiClient.variants}). That is why this view takes a
 *  {@code CatalogApiClient} the originating plan brief did not anticipate. */
public class ManufacturingManagementView {

    private static final int PAGE_SIZE = 50;

    private final BillOfMaterialsApiClient bomApi;
    private final ProductionOrderApiClient productionApi;
    private final VariantApiClient variantApi;
    private final CatalogApiClient catalogApi;
    private final boolean canManageBom;
    private final boolean canProduce;

    public ManufacturingManagementView(final BillOfMaterialsApiClient bomApi, final ProductionOrderApiClient productionApi,
                                       final VariantApiClient variantApi, final CatalogApiClient catalogApi,
                                       final boolean canManageBom, final boolean canProduce) {
        this.bomApi = bomApi;
        this.productionApi = productionApi;
        this.variantApi = variantApi;
        this.catalogApi = catalogApi;
        this.canManageBom = canManageBom;
        this.canProduce = canProduce;
    }

    public Parent createView() {
        final TabPane tabs = new TabPane(tab("Bills of Materials", new BillsOfMaterialsPane().create()),
                tab("Production", new ProductionPane().create()));
        tabs.getTabs().forEach(t -> t.setClosable(false));
        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setCenter(tabs);
        return root;
    }

    private Tab tab(final String title, final Parent content) {
        return new Tab(title, content);
    }

    /** Task 20.2: BOM list + detail (items table) + create/add-item/remove-item, mirrors
     *  {@code PurchasingManagementView.GoodsReceiptsPane}'s table/detail-panel layout. */
    private final class BillsOfMaterialsPane {

        private final TableView<BomSummaryResponse> table = new TableView<>();
        private final TableView<BomItemResponse> itemsTable = new TableView<>();
        private final Label stateLabel = new Label("Loading Bills of Materials...");
        private final Label detailLabel = new Label("Select a Bill of Materials");
        private final Button newButton = Icons.button("New BOM", FontAwesomeSolid.PLUS);
        private final Button addItemButton = Icons.button("Add Component", FontAwesomeSolid.PLUS_CIRCLE);
        private final Button removeItemButton = Icons.button("Remove Component", FontAwesomeSolid.MINUS_CIRCLE);
        private BomDetailResponse selected;

        Parent create() {
            configureTable();
            configureItemsTable();
            newButton.setDisable(!canManageBom);
            newButton.setOnAction(event -> openCreateDialog());
            final HBox header = new HBox(10, UiSupport.label("Bills of Materials", "screen-title"), spacer(), newButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, table);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(table, Priority.ALWAYS);

            addItemButton.setDisable(true);
            removeItemButton.setDisable(true);
            addItemButton.setOnAction(event -> openAddItemDialog());
            removeItemButton.setOnAction(event -> removeSelectedItem());
            final HBox actions = new HBox(8, addItemButton, removeItemButton);

            final VBox detailPanel = new VBox(12, UiSupport.label("Recipe", "panel-title"), detailLabel, actions,
                    itemsTable);
            detailPanel.getStyleClass().add("side-panel");
            detailPanel.setPadding(new Insets(16));
            detailPanel.setPrefWidth(650);
            VBox.setVgrow(itemsTable, Priority.ALWAYS);

            final BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(center);
            root.setRight(detailPanel);
            load();
            return root;
        }

        private void configureTable() {
            table.getStyleClass().add("data-table");
            table.getColumns().setAll(
                    column("Name", BomSummaryResponse::name),
                    column("Finished Product", BomSummaryResponse::finishedVariantName),
                    column("SKU", BomSummaryResponse::finishedVariantSku),
                    column("Active", bom -> bom.active() ? "Yes" : "No"),
                    column("Est. Cost", bom -> bom.totalEstimatedCost().toPlainString()));
            table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, bom) -> {
                if (bom != null) {
                    selectBom(bom.bomId());
                }
            });
        }

        private void configureItemsTable() {
            itemsTable.getStyleClass().add("data-table");
            itemsTable.getColumns().setAll(
                    column("Component", BomItemResponse::componentName),
                    column("SKU", BomItemResponse::componentSku),
                    column("Qty", i -> i.quantity().stripTrailingZeros().toPlainString()),
                    column("Wastage", i -> i.wastageQty().stripTrailingZeros().toPlainString()),
                    column("Cost Price", i -> i.componentCostPrice().toPlainString()),
                    column("Est. Cost", i -> i.estimatedCost().toPlainString()));
        }

        private void load() {
            stateLabel.setText("Loading Bills of Materials...");
            UiSupport.onFx(bomApi.search(false, 0, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No Bills of Materials found."
                        : result.totalElements() + " Bills of Materials");
            }, "Bills of Materials could not be loaded.");
        }

        private void selectBom(final UUID bomId) {
            UiSupport.onFx(bomApi.get(bomId), bom -> {
                selected = bom;
                detailLabel.setText(bom.name() + " -> " + bom.finishedVariantName() + "   Est. cost: "
                        + bom.totalEstimatedCost().toPlainString());
                itemsTable.setItems(FXCollections.observableArrayList(bom.items()));
                addItemButton.setDisable(!canManageBom);
                removeItemButton.setDisable(!canManageBom);
            }, "Bill of Materials detail could not be loaded.");
        }

        private void openCreateDialog() {
            final Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("New Bill of Materials");
            final VariantPickerField finishedVariant = new VariantPickerField();
            final TextField nameField = new TextField();
            final GridPane grid = grid();
            addRow(grid, 0, "Finished Product", finishedVariant.node());
            addRow(grid, 1, "Name", nameField);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    final VariantResponse variant = finishedVariant.getValue();
                    if (variant == null || nameField.getText().isBlank()) {
                        UiSupport.alert("Select the finished product's variant and enter a name.");
                        return null;
                    }
                    UiSupport.onFx(bomApi.create(new CreateBomRequest(variant.productVariantId(), nameField.getText())),
                            created -> load(), "Bill of Materials could not be created.");
                }
                return null;
            });
            dialog.showAndWait();
        }

        private void openAddItemDialog() {
            if (selected == null) {
                return;
            }
            final Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Add Component");
            final VariantPickerField component = new VariantPickerField();
            final TextField quantityField = new TextField();
            final TextField wastageField = new TextField("0");
            final GridPane grid = grid();
            addRow(grid, 0, "Component", component.node());
            addRow(grid, 1, "Quantity", quantityField);
            addRow(grid, 2, "Wastage Qty", wastageField);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    final VariantResponse variant = component.getValue();
                    if (variant == null) {
                        UiSupport.alert("Select the component's variant.");
                        return null;
                    }
                    try {
                        final BigDecimal quantity = new BigDecimal(quantityField.getText());
                        final BigDecimal wastage = new BigDecimal(wastageField.getText().isBlank() ? "0"
                                : wastageField.getText());
                        UiSupport.onFx(bomApi.addItem(selected.bomId(), new BomItemRequest(
                                variant.productVariantId(), quantity, wastage)), updated -> {
                            selected = updated;
                            itemsTable.setItems(FXCollections.observableArrayList(updated.items()));
                            load();
                        }, "Component could not be added.");
                    } catch (final NumberFormatException ex) {
                        UiSupport.alert("Quantity and wastage must be valid numbers.");
                    }
                }
                return null;
            });
            dialog.showAndWait();
        }

        private void removeSelectedItem() {
            final BomItemResponse item = itemsTable.getSelectionModel().getSelectedItem();
            if (selected == null || item == null) {
                return;
            }
            UiSupport.onFx(bomApi.removeItem(selected.bomId(), item.bomItemId()), updated -> {
                selected = updated;
                itemsTable.setItems(FXCollections.observableArrayList(updated.items()));
                load();
            }, "Component could not be removed.");
        }
    }

    /** Task 21.1-21.4: BOM picker + quantity + STOCKED/MADE_TO_ORDER mode, posts a Produce
     *  transaction, and lists production history for traceability. */
    private final class ProductionPane {

        private final TableView<ProductionOrderSummaryResponse> historyTable = new TableView<>();
        private final TableView<ProductionOrderItemResponse> detailItemsTable = new TableView<>();
        private final Label stateLabel = new Label("Loading production history...");
        private final Label detailLabel = new Label("Select a production order");
        private final Button produceButton = Icons.button("Produce", FontAwesomeSolid.INDUSTRY);

        Parent create() {
            configureHistoryTable();
            configureDetailItemsTable();
            produceButton.setDisable(!canProduce);
            produceButton.setOnAction(event -> openProduceDialog());
            final HBox header = new HBox(10, UiSupport.label("Production History", "screen-title"), spacer(), produceButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, historyTable);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(historyTable, Priority.ALWAYS);

            final VBox detailPanel = new VBox(12, UiSupport.label("Consumption", "panel-title"), detailLabel,
                    detailItemsTable);
            detailPanel.getStyleClass().add("side-panel");
            detailPanel.setPadding(new Insets(16));
            detailPanel.setPrefWidth(600);
            VBox.setVgrow(detailItemsTable, Priority.ALWAYS);

            final BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(center);
            root.setRight(detailPanel);
            load();
            return root;
        }

        private void configureHistoryTable() {
            historyTable.getStyleClass().add("data-table");
            historyTable.getColumns().setAll(
                    column("Production #", ProductionOrderSummaryResponse::productionNumber),
                    column("Finished Product", ProductionOrderSummaryResponse::finishedVariantName),
                    column("Qty Produced", o -> o.quantityProduced().stripTrailingZeros().toPlainString()),
                    column("Mode", ProductionOrderSummaryResponse::productionMode),
                    column("Component Cost", o -> o.totalComponentCost().toPlainString()),
                    column("Date", o -> String.valueOf(o.createdAt())));
            historyTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, order) -> {
                if (order != null) {
                    selectOrder(order.productionOrderId());
                }
            });
        }

        private void configureDetailItemsTable() {
            detailItemsTable.getStyleClass().add("data-table");
            detailItemsTable.getColumns().setAll(
                    column("Component", ProductionOrderItemResponse::componentName),
                    column("SKU", ProductionOrderItemResponse::componentSku),
                    column("Qty Consumed", i -> i.quantityConsumed().stripTrailingZeros().toPlainString()),
                    column("Unit Cost", i -> i.unitCostAtProduction().toPlainString()),
                    column("Total Cost", i -> i.totalCost().toPlainString()));
        }

        private void load() {
            stateLabel.setText("Loading production history...");
            UiSupport.onFx(productionApi.search(null, null, 0, PAGE_SIZE), result -> {
                historyTable.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No production history found."
                        : result.totalElements() + " production orders");
            }, "Production history could not be loaded.");
        }

        private void selectOrder(final UUID productionOrderId) {
            UiSupport.onFx(productionApi.get(productionOrderId), order -> {
                detailLabel.setText(order.productionNumber() + " (" + order.productionMode() + ")   Total cost: "
                        + order.totalComponentCost().toPlainString());
                detailItemsTable.setItems(FXCollections.observableArrayList(order.items()));
            }, "Production order detail could not be loaded.");
        }

        private void openProduceDialog() {
            UiSupport.onFx(bomApi.search(true, 0, 200), boms -> {
                final Dialog<Void> dialog = new Dialog<>();
                dialog.setTitle("Produce");
                final ComboBox<BomSummaryResponse> bom = new ComboBox<>(FXCollections.observableArrayList(boms.data()));
                bom.setConverter(nameConverter(b -> b.name() + " -> " + b.finishedVariantName()));
                bom.setPromptText("Select Bill of Materials");
                final TextField quantityField = new TextField();
                final ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList("STOCKED", "MADE_TO_ORDER"));
                mode.setValue("STOCKED");
                final TextField notesField = new TextField();
                final GridPane grid = grid();
                addRow(grid, 0, "Bill of Materials", bom);
                addRow(grid, 1, "Quantity to Produce", quantityField);
                addRow(grid, 2, "Mode", mode);
                addRow(grid, 3, "Notes", notesField);
                dialog.getDialogPane().setContent(grid);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                dialog.setResultConverter(button -> {
                    if (button == ButtonType.OK && bom.getValue() != null) {
                        try {
                            final BigDecimal quantity = new BigDecimal(quantityField.getText());
                            UiSupport.onFx(productionApi.produce(UUID.randomUUID(), new ProduceRequest(bom.getValue().bomId(),
                                    quantity, mode.getValue(), notesField.getText())), result -> {
                                UiSupport.alert("Production " + result.productionNumber() + " posted.");
                                load();
                            }, "Production could not be posted.");
                        } catch (final NumberFormatException ex) {
                            UiSupport.alert("Quantity to produce must be a valid number.");
                        }
                    }
                    return null;
                });
                dialog.showAndWait();
            }, "Active Bills of Materials could not be loaded.");
        }
    }

    /** Two-step product -> variant picker embedded in a dialog form. There is no cross-product
     *  variant search endpoint on the server (only {@code GET /products/{id}/variants}), so this
     *  resolves a variant the same way {@code PosView}'s barcode/search resolution and {@code
     *  PurchasingManagementView}'s product pickers already do: a typeahead product search, then
     *  that product's variant list. */
    private final class VariantPickerField {
        private final ComboBox<ProductSummaryResponse> productCombo = new ComboBox<>();
        private final ComboBox<VariantResponse> variantCombo = new ComboBox<>();

        VariantPickerField() {
            productCombo.setConverter(nameConverter(p -> p.sku() + " - " + p.name()));
            productCombo.setEditable(true);
            productCombo.setPromptText("Type to search, then pick");
            productCombo.getEditor().setOnAction(event -> UiSupport.onFx(
                    catalogApi.products(productCombo.getEditor().getText(), null, "INVENTORY", true, 0, 50),
                    result -> productCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Products could not be searched."));
            productCombo.valueProperty().addListener((observable, oldValue, product) -> loadVariants(product));
            variantCombo.setConverter(nameConverter(v -> v.variantLabel() == null || v.variantLabel().isBlank()
                    ? v.sku() : v.sku() + " - " + v.variantLabel()));
            variantCombo.setPromptText("Select variant");
        }

        private void loadVariants(final ProductSummaryResponse product) {
            variantCombo.getItems().clear();
            variantCombo.setValue(null);
            if (product == null) {
                return;
            }
            UiSupport.onFx(variantApi.variants(product.productId()),
                    variants -> variantCombo.setItems(FXCollections.observableArrayList(variants)),
                    "Variants could not be loaded.");
        }

        Node node() {
            return new VBox(4, productCombo, variantCombo);
        }

        VariantResponse getValue() {
            return variantCombo.getValue();
        }
    }

    private <T> TableColumn<T, String> column(final String title, final Function<T, String> valueFn) {
        final TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(valueFn.apply(data.getValue()))));
        return col;
    }

    private String nullToBlank(final String value) {
        return value == null ? "" : value;
    }

    private static <T> StringConverter<T> nameConverter(final Function<T, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(final T value) {
                return value == null ? "" : toText.apply(value);
            }

            @Override
            public T fromString(final String string) {
                return null;
            }
        };
    }

    private GridPane grid() {
        final GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        return grid;
    }

    private void addRow(final GridPane grid, final int row, final String labelText, final Node field) {
        grid.add(new Label(labelText), 0, row);
        grid.add(field, 1, row);
    }

    private Region spacer() {
        final Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}

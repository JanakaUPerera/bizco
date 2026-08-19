package com.bizco.client.inventory.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.inventory.service.InventoryApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.inventory.StockDtos.CreateStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.DecideStockAdjustmentRequest;
import com.bizco.common.dto.inventory.StockDtos.StockAdjustmentResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
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

/**
 * Inventory screen (DevelopmentPlan.md Week 12 tasks 12.3/12.5/12.6/12.8): browse stock
 * levels/movement history (StockController) and run the stock adjustment PENDING -&gt;
 * APPROVED/REJECTED workflow (StockAdjustmentController), gated by the same
 * {@code inventory.adjustment.create}/{@code inventory.adjustment.approve} permissions the server
 * enforces.
 */
public class InventoryManagementView {

    private static final int PAGE_SIZE = 25;

    private final InventoryApiClient inventoryApi;
    private final CatalogApiClient catalogApi;
    private final boolean canCreateAdjustment;
    private final boolean canApproveAdjustment;
    private final StockLevelsPane stockLevelsPane = new StockLevelsPane();
    private final AdjustmentsPane adjustmentsPane = new AdjustmentsPane();

    public InventoryManagementView(final InventoryApiClient inventoryApi, final CatalogApiClient catalogApi,
                                   final boolean canCreateAdjustment, final boolean canApproveAdjustment) {
        this.inventoryApi = inventoryApi;
        this.catalogApi = catalogApi;
        this.canCreateAdjustment = canCreateAdjustment;
        this.canApproveAdjustment = canApproveAdjustment;
    }

    public Parent createView() {
        final TabPane tabs = new TabPane(tab("Stock Levels", stockLevelsPane.create()),
                tab("Adjustments", adjustmentsPane.create()));
        tabs.getTabs().forEach(t -> t.setClosable(false));
        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setCenter(tabs);
        return root;
    }

    private Tab tab(final String title, final Parent content) {
        return new Tab(title, content);
    }

    /** 12.3/12.5/12.8: stock-level browser with a low-stock filter (REC-STK-003) and movement
     *  history (12.5) for whichever product is selected. */
    private final class StockLevelsPane {

        private final TextField searchField = new TextField();
        private final CheckBox lowStockOnly = new CheckBox("Low stock only");
        private final TableView<StockLevelResponse> levelsTable = new TableView<>();
        private final TableView<StockMovementResponse> movementsTable = new TableView<>();
        private final Label stateLabel = new Label("Loading stock levels...");
        private final Label detailLabel = new Label("Select a product");

        Parent create() {
            configureLevelsTable();
            configureMovementsTable();

            searchField.setPromptText("Search SKU or name");
            searchField.setOnAction(event -> load());
            lowStockOnly.setOnAction(event -> load());
            final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
            searchButton.setOnAction(event -> load());
            final HBox header = new HBox(10, UiSupport.label("Stock Levels", "screen-title"), spacer(), searchField,
                    lowStockOnly, searchButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, levelsTable);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(levelsTable, Priority.ALWAYS);

            final VBox detailPanel = new VBox(12, UiSupport.label("Movement History", "panel-title"), detailLabel,
                    movementsTable);
            detailPanel.getStyleClass().add("side-panel");
            detailPanel.setPadding(new Insets(16));
            detailPanel.setPrefWidth(480);
            VBox.setVgrow(movementsTable, Priority.ALWAYS);

            final BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(center);
            root.setRight(detailPanel);
            load();
            return root;
        }

        private void configureLevelsTable() {
            levelsTable.getStyleClass().add("data-table");
            levelsTable.getColumns().setAll(
                    column("SKU", StockLevelResponse::sku),
                    column("Name", StockLevelResponse::name),
                    column("Physical", l -> l.physicalStock().stripTrailingZeros().toPlainString()),
                    column("Reserved", l -> l.reservedStock().stripTrailingZeros().toPlainString()),
                    column("Available", l -> l.availableStock().stripTrailingZeros().toPlainString()),
                    column("Reorder Pt.", l -> l.reorderPoint() == null ? "" : l.reorderPoint().stripTrailingZeros().toPlainString()),
                    column("Low Stock", l -> l.lowStock() ? "Yes" : ""));
            levelsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, level) -> {
                if (level != null) {
                    detailLabel.setText(level.sku() + " - " + level.name());
                    loadMovements(level.productId());
                }
            });
        }

        private void configureMovementsTable() {
            movementsTable.getStyleClass().add("data-table");
            movementsTable.getColumns().setAll(
                    column("Date", m -> String.valueOf(m.createdAt())),
                    column("Type", StockMovementResponse::movementType),
                    column("Qty", m -> m.quantity().stripTrailingZeros().toPlainString()),
                    column("Reference", m -> m.referenceType() + " " + m.referenceId()));
        }

        private void load() {
            stateLabel.setText("Loading stock levels...");
            final var future = lowStockOnly.isSelected() ? inventoryApi.lowStock(0, PAGE_SIZE)
                    : inventoryApi.levels(blankToNull(searchField.getText()), 0, PAGE_SIZE);
            UiSupport.onFx(future, result -> {
                levelsTable.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No products found." : result.totalElements() + " products");
            }, "Stock levels could not be loaded.");
        }

        private void loadMovements(final UUID productId) {
            UiSupport.onFx(inventoryApi.movements(productId, 0, 50),
                    result -> movementsTable.setItems(FXCollections.observableArrayList(result.data())),
                    "Movement history could not be loaded.");
        }
    }

    /** 12.6/12.7: request/approve/reject workflow (StateMachines.md &sect;17). */
    private final class AdjustmentsPane {

        private final ComboBox<String> statusFilter = new ComboBox<>();
        private final TableView<StockAdjustmentResponse> table = new TableView<>();
        private final Label stateLabel = new Label("Loading adjustments...");
        private final Button newButton = Icons.button("New Adjustment", FontAwesomeSolid.PLUS);
        private final Button approveButton = Icons.button("Approve", FontAwesomeSolid.CHECK);
        private final Button rejectButton = Icons.button("Reject", FontAwesomeSolid.TIMES);
        private StockAdjustmentResponse selected;

        Parent create() {
            configureTable();
            statusFilter.getItems().setAll("", "PENDING", "APPROVED", "REJECTED");
            statusFilter.setValue("PENDING");
            statusFilter.setOnAction(event -> load());
            newButton.setDisable(!canCreateAdjustment);
            newButton.setOnAction(event -> openCreateDialog());
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            approveButton.setOnAction(event -> decide(true));
            rejectButton.setOnAction(event -> decide(false));
            final HBox header = new HBox(10, UiSupport.label("Stock Adjustments", "screen-title"), spacer(),
                    statusFilter, newButton, approveButton, rejectButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, table);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(table, Priority.ALWAYS);

            final BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(center);
            load();
            return root;
        }

        private void configureTable() {
            table.getStyleClass().add("data-table");
            table.getColumns().setAll(
                    column("SKU", StockAdjustmentResponse::sku),
                    column("Product", StockAdjustmentResponse::productName),
                    column("Type", StockAdjustmentResponse::adjustmentType),
                    column("Qty", a -> a.quantity().stripTrailingZeros().toPlainString()),
                    column("Reason", StockAdjustmentResponse::reason),
                    column("Status", StockAdjustmentResponse::status));
            table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, adjustment) -> {
                selected = adjustment;
                final boolean pending = adjustment != null && "PENDING".equals(adjustment.status());
                approveButton.setDisable(!pending || !canApproveAdjustment);
                rejectButton.setDisable(!pending || !canApproveAdjustment);
            });
        }

        private void load() {
            stateLabel.setText("Loading adjustments...");
            UiSupport.onFx(inventoryApi.adjustments(null, blankToNull(statusFilter.getValue()), 0, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No adjustments found." : result.totalElements() + " adjustments");
            }, "Stock adjustments could not be loaded.");
        }

        private void decide(final boolean approve) {
            if (selected == null) {
                return;
            }
            final String reason = promptText(approve ? "Approve Adjustment" : "Reject Adjustment",
                    (approve ? "Approval" : "Rejection") + " reason for " + selected.sku() + ":");
            if (reason == null) {
                return;
            }
            final var request = new DecideStockAdjustmentRequest(reason, selected.version());
            final var future = approve ? inventoryApi.approveAdjustment(selected.stockAdjustmentId(), UUID.randomUUID(), request)
                    : inventoryApi.rejectAdjustment(selected.stockAdjustmentId(), UUID.randomUUID(), request);
            UiSupport.onFx(future, result -> {
                UiSupport.alert("Adjustment " + (approve ? "approved." : "rejected."));
                load();
            }, "Adjustment could not be decided.");
        }

        private void openCreateDialog() {
            final CreateStockAdjustmentRequest request = promptAdjustment();
            if (request == null) {
                return;
            }
            UiSupport.onFx(inventoryApi.createAdjustment(request), created -> {
                UiSupport.alert("Adjustment requested for " + created.sku() + ".");
                statusFilter.setValue("PENDING");
                load();
            }, "Adjustment could not be created.");
        }

        private CreateStockAdjustmentRequest promptAdjustment() {
            final Dialog<CreateStockAdjustmentRequest> dialog = new Dialog<>();
            dialog.setTitle("New Stock Adjustment");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final TextField productSearch = new TextField();
            productSearch.setPromptText("SKU or name");
            final ComboBox<ProductSummaryResponse> productCombo = new ComboBox<>();
            productCombo.setPromptText("Search, then pick a product");
            productCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(final ProductSummaryResponse p) {
                    return p == null ? "" : p.sku() + " - " + p.name();
                }

                @Override
                public ProductSummaryResponse fromString(final String string) {
                    return null;
                }
            });
            final Button productSearchButton = Icons.button("Find", FontAwesomeSolid.SEARCH);
            productSearchButton.setOnAction(event -> UiSupport.onFx(
                    catalogApi.products(blankToNull(productSearch.getText()), null, "INVENTORY", true, 0, 50),
                    result -> productCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Products could not be searched."));

            final ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("POSITIVE", "NEGATIVE", "DAMAGE"));
            type.setValue("POSITIVE");
            final TextField quantity = new TextField();
            final TextArea reason = new TextArea();
            reason.setPrefRowCount(3);

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Product"), new HBox(6, productSearch, productSearchButton));
            grid.addRow(1, new Label(""), productCombo);
            grid.addRow(2, new Label("Type"), type);
            grid.addRow(3, new Label("Quantity"), quantity);
            grid.addRow(4, new Label("Reason"), reason);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                final ProductSummaryResponse product = productCombo.getValue();
                if (product == null) {
                    UiSupport.alert("Select a product.");
                    return null;
                }
                try {
                    final BigDecimal qty = new BigDecimal(quantity.getText().trim());
                    return new CreateStockAdjustmentRequest(product.productId(), type.getValue(), qty, reason.getText());
                } catch (final NumberFormatException | NullPointerException exception) {
                    UiSupport.alert("Quantity must be a number.");
                    return null;
                }
            });
            return dialog.showAndWait().orElse(null);
        }

        private String promptText(final String title, final String message) {
            final Dialog<String> dialog = new Dialog<>();
            dialog.setTitle(title);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            final TextArea reasonArea = new TextArea();
            reasonArea.setPrefRowCount(3);
            final VBox content = new VBox(8, new Label(message), reasonArea);
            content.setPadding(new Insets(8));
            dialog.getDialogPane().setContent(content);
            dialog.setResultConverter(button -> button == ButtonType.OK ? reasonArea.getText() : null);
            return dialog.showAndWait().orElse(null);
        }
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static <T> TableColumn<T, String> column(final String title, final Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(110);
        return column;
    }

    private static HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

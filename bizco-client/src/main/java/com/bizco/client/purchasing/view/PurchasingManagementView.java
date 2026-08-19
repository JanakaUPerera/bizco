package com.bizco.client.purchasing.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.purchasing.service.GoodsReceiptApiClient;
import com.bizco.client.purchasing.service.PurchaseOrderApiClient;
import com.bizco.client.purchasing.service.SupplierApiClient;
import com.bizco.client.purchasing.service.SupplierProductApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.AddGoodsReceiptItemRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.CreateGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptDetailResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptItemResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSummaryResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.PostGoodsReceiptRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.AddPurchaseOrderItemRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CancelPurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.CreatePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.DecidePurchaseOrderRequest;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderDetailResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderItemResponse;
import com.bizco.common.dto.purchasing.PurchaseOrderDtos.PurchaseOrderSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductCreateRequest;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductResponse;
import com.bizco.common.dto.purchasing.SupplierProductDtos.SupplierProductUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Purchasing screen (DevelopmentPlan.md Week 13): the per-supplier product catalog and the
 * Purchase Order DRAFT/APPROVED/SENT workflow, gated by {@code purchasing.supplier_product.*} and
 * {@code purchasing.po.*} permissions.
 */
public class PurchasingManagementView {

    private static final int PAGE_SIZE = 25;

    private final SupplierProductApiClient supplierProductApi;
    private final PurchaseOrderApiClient purchaseOrderApi;
    private final GoodsReceiptApiClient goodsReceiptApi;
    private final SupplierApiClient supplierApi;
    private final CatalogApiClient catalogApi;
    private final boolean canManageSupplierProduct;
    private final boolean canCreatePo;
    private final boolean canApprovePo;
    private final boolean canCreateGoodsReceipt;
    private final SupplierProductsPane supplierProductsPane = new SupplierProductsPane();
    private final PurchaseOrdersPane purchaseOrdersPane = new PurchaseOrdersPane();
    private final GoodsReceiptsPane goodsReceiptsPane = new GoodsReceiptsPane();

    public PurchasingManagementView(final SupplierProductApiClient supplierProductApi,
                                    final PurchaseOrderApiClient purchaseOrderApi,
                                    final GoodsReceiptApiClient goodsReceiptApi, final SupplierApiClient supplierApi,
                                    final CatalogApiClient catalogApi, final boolean canManageSupplierProduct,
                                    final boolean canCreatePo, final boolean canApprovePo,
                                    final boolean canCreateGoodsReceipt) {
        this.supplierProductApi = supplierProductApi;
        this.purchaseOrderApi = purchaseOrderApi;
        this.goodsReceiptApi = goodsReceiptApi;
        this.supplierApi = supplierApi;
        this.catalogApi = catalogApi;
        this.canManageSupplierProduct = canManageSupplierProduct;
        this.canCreatePo = canCreatePo;
        this.canApprovePo = canApprovePo;
        this.canCreateGoodsReceipt = canCreateGoodsReceipt;
    }

    public Parent createView() {
        final TabPane tabs = new TabPane(tab("Supplier Products", supplierProductsPane.create()),
                tab("Purchase Orders", purchaseOrdersPane.create()),
                tab("Goods Receipts", goodsReceiptsPane.create()));
        tabs.getTabs().forEach(t -> t.setClosable(false));
        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setCenter(tabs);
        return root;
    }

    private Tab tab(final String title, final Parent content) {
        return new Tab(title, content);
    }

    /** 13.2: per-supplier product catalog. */
    private final class SupplierProductsPane {

        private final ComboBox<SupplierSummaryResponse> supplierFilter = new ComboBox<>();
        private final TableView<SupplierProductResponse> table = new TableView<>();
        private final Label stateLabel = new Label("Loading supplier products...");
        private final Button newButton = Icons.button("New Entry", FontAwesomeSolid.PLUS);

        Parent create() {
            configureTable();
            supplierFilter.setPromptText("All suppliers");
            supplierFilter.setConverter(nameConverter(SupplierSummaryResponse::name));
            UiSupport.onFx(supplierApi.search(null, "ACTIVE", 0, 200),
                    result -> supplierFilter.setItems(FXCollections.observableArrayList(result.data())),
                    "Suppliers could not be loaded.");
            supplierFilter.setOnAction(event -> load());
            newButton.setDisable(!canManageSupplierProduct);
            newButton.setOnAction(event -> openCreateDialog());
            final HBox header = new HBox(10, UiSupport.label("Supplier Products", "screen-title"), spacer(),
                    supplierFilter, newButton);
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
                    column("Supplier", SupplierProductResponse::supplierName),
                    column("SKU", SupplierProductResponse::sku),
                    column("Product", SupplierProductResponse::productName),
                    column("Supplier SKU", sp -> sp.supplierSku() == null ? "" : sp.supplierSku()),
                    column("Price", sp -> sp.purchasePrice().toPlainString()),
                    column("Min Qty", sp -> sp.minOrderQty().stripTrailingZeros().toPlainString()),
                    column("Lead Time", sp -> sp.leadTimeDays() == null ? "" : sp.leadTimeDays() + " d"),
                    column("Last Price", sp -> sp.lastPurchasePrice() == null ? "" : sp.lastPurchasePrice().toPlainString()),
                    column("Preferred", sp -> sp.preferred() ? "Yes" : ""));
            table.setRowFactory(view -> {
                final javafx.scene.control.TableRow<SupplierProductResponse> row = new javafx.scene.control.TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty() && canManageSupplierProduct) {
                        openEditDialog(row.getItem());
                    }
                });
                return row;
            });
        }

        private void load() {
            stateLabel.setText("Loading supplier products...");
            final SupplierSummaryResponse supplier = supplierFilter.getValue();
            UiSupport.onFx(supplierProductApi.search(supplier == null ? null : supplier.supplierId(), null, 0, PAGE_SIZE),
                    result -> {
                        table.setItems(FXCollections.observableArrayList(result.data()));
                        stateLabel.setText(result.totalElements() == 0 ? "No supplier products found."
                                : result.totalElements() + " supplier products");
                    }, "Supplier products could not be loaded.");
        }

        private void openCreateDialog() {
            final Dialog<SupplierProductCreateRequest> dialog = new Dialog<>();
            dialog.setTitle("New Supplier Product");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final ComboBox<SupplierSummaryResponse> supplierCombo = new ComboBox<>(supplierFilter.getItems());
            supplierCombo.setConverter(nameConverter(SupplierSummaryResponse::name));
            supplierCombo.setPromptText("Select supplier");
            final ComboBox<ProductSummaryResponse> productCombo = productPicker();
            final TextField supplierSku = new TextField();
            final TextField price = new TextField();
            final TextField minQty = new TextField("1");
            final TextField leadTime = new TextField();
            final CheckBox preferred = new CheckBox("Preferred supplier for this product");

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Supplier"), supplierCombo);
            grid.addRow(1, new Label("Product"), productCombo);
            grid.addRow(2, new Label("Supplier SKU"), supplierSku);
            grid.addRow(3, new Label("Purchase Price"), price);
            grid.addRow(4, new Label("Min Order Qty"), minQty);
            grid.addRow(5, new Label("Lead Time (days)"), leadTime);
            grid.addRow(6, new Label(""), preferred);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                if (supplierCombo.getValue() == null || productCombo.getValue() == null) {
                    UiSupport.alert("Select a supplier and a product.");
                    return null;
                }
                try {
                    return new SupplierProductCreateRequest(supplierCombo.getValue().supplierId(),
                            productCombo.getValue().productId(), blankToNull(supplierSku.getText()),
                            new BigDecimal(price.getText().trim()), new BigDecimal(minQty.getText().trim()),
                            leadTime.getText().isBlank() ? null : Integer.valueOf(leadTime.getText().trim()),
                            preferred.isSelected());
                } catch (final NumberFormatException exception) {
                    UiSupport.alert("Price/quantity/lead time must be numbers.");
                    return null;
                }
            });
            final SupplierProductCreateRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(supplierProductApi.create(request), created -> {
                UiSupport.alert("Supplier product entry created.");
                load();
            }, "Supplier product could not be created.");
        }

        private void openEditDialog(final SupplierProductResponse entry) {
            final Dialog<SupplierProductUpdateRequest> dialog = new Dialog<>();
            dialog.setTitle("Edit Supplier Product - " + entry.sku());
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final TextField supplierSku = new TextField(entry.supplierSku() == null ? "" : entry.supplierSku());
            final TextField price = new TextField(entry.purchasePrice().toPlainString());
            final TextField minQty = new TextField(entry.minOrderQty().stripTrailingZeros().toPlainString());
            final TextField leadTime = new TextField(entry.leadTimeDays() == null ? "" : String.valueOf(entry.leadTimeDays()));
            final CheckBox preferred = new CheckBox("Preferred supplier for this product");
            preferred.setSelected(entry.preferred());

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Supplier SKU"), supplierSku);
            grid.addRow(1, new Label("Purchase Price"), price);
            grid.addRow(2, new Label("Min Order Qty"), minQty);
            grid.addRow(3, new Label("Lead Time (days)"), leadTime);
            grid.addRow(4, new Label(""), preferred);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                try {
                    return new SupplierProductUpdateRequest(blankToNull(supplierSku.getText()),
                            new BigDecimal(price.getText().trim()), new BigDecimal(minQty.getText().trim()),
                            leadTime.getText().isBlank() ? null : Integer.valueOf(leadTime.getText().trim()),
                            preferred.isSelected(), entry.version());
                } catch (final NumberFormatException exception) {
                    UiSupport.alert("Price/quantity/lead time must be numbers.");
                    return null;
                }
            });
            final SupplierProductUpdateRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(supplierProductApi.update(entry.supplierProductId(), request), updated -> {
                UiSupport.alert("Supplier product entry updated.");
                load();
            }, "Supplier product could not be updated.");
        }

        private ComboBox<ProductSummaryResponse> productPicker() {
            final ComboBox<ProductSummaryResponse> combo = new ComboBox<>();
            combo.setConverter(nameConverter(p -> p.sku() + " - " + p.name()));
            combo.setEditable(true);
            combo.setPromptText("Type to search, then pick");
            combo.getEditor().setOnAction(event -> UiSupport.onFx(
                    catalogApi.products(combo.getEditor().getText(), null, "INVENTORY", true, 0, 50),
                    result -> combo.setItems(FXCollections.observableArrayList(result.data())),
                    "Products could not be searched."));
            return combo;
        }
    }

    /** 13.4-13.7: Purchase Order draft/approve/send/cancel workflow. */
    private final class PurchaseOrdersPane {

        private final ComboBox<String> statusFilter = new ComboBox<>();
        private final TableView<PurchaseOrderSummaryResponse> table = new TableView<>();
        private final TableView<PurchaseOrderItemResponse> itemsTable = new TableView<>();
        private final Label stateLabel = new Label("Loading purchase orders...");
        private final Label detailLabel = new Label("Select a purchase order");
        private final Button newButton = Icons.button("New PO", FontAwesomeSolid.PLUS);
        private final Button addItemButton = Icons.button("Add Item", FontAwesomeSolid.PLUS_CIRCLE);
        private final Button approveButton = Icons.button("Approve", FontAwesomeSolid.CHECK);
        private final Button sendButton = Icons.button("Send", FontAwesomeSolid.PAPER_PLANE);
        private final Button cancelButton = Icons.button("Cancel", FontAwesomeSolid.BAN);
        private PurchaseOrderDetailResponse selected;

        Parent create() {
            configureTable();
            configureItemsTable();
            statusFilter.getItems().setAll("", "DRAFT", "APPROVED", "SENT", "PARTIALLY_RECEIVED", "FULLY_RECEIVED",
                    "CLOSED", "CANCELLED");
            statusFilter.setValue("");
            statusFilter.setOnAction(event -> load());
            newButton.setDisable(!canCreatePo);
            newButton.setOnAction(event -> openCreateDialog());
            final HBox header = new HBox(10, UiSupport.label("Purchase Orders", "screen-title"), spacer(),
                    statusFilter, newButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, table);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(table, Priority.ALWAYS);

            addItemButton.setDisable(true);
            approveButton.setDisable(true);
            sendButton.setDisable(true);
            cancelButton.setDisable(true);
            addItemButton.setOnAction(event -> openAddItemDialog());
            approveButton.setOnAction(event -> decide(true));
            sendButton.setOnAction(event -> decide(false));
            cancelButton.setOnAction(event -> cancelSelected());
            final HBox actions = new HBox(8, addItemButton, approveButton, sendButton, cancelButton);

            final VBox detailPanel = new VBox(12, UiSupport.label("Purchase Order Detail", "panel-title"), detailLabel,
                    actions, itemsTable);
            detailPanel.getStyleClass().add("side-panel");
            detailPanel.setPadding(new Insets(16));
            detailPanel.setPrefWidth(560);
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
                    column("PO Number", po -> po.poNumber() == null ? "(draft)" : po.poNumber()),
                    column("Supplier", PurchaseOrderSummaryResponse::supplierName),
                    column("Date", po -> String.valueOf(po.poDate())),
                    column("Status", PurchaseOrderSummaryResponse::status),
                    column("Total", po -> po.totalAmount().toPlainString()));
            table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, po) -> {
                if (po != null) {
                    selectOrder(po.purchaseOrderId());
                }
            });
        }

        private void configureItemsTable() {
            itemsTable.getStyleClass().add("data-table");
            itemsTable.getColumns().setAll(
                    column("SKU", PurchaseOrderItemResponse::sku),
                    column("Product", PurchaseOrderItemResponse::productName),
                    column("Qty", i -> i.quantityOrdered().stripTrailingZeros().toPlainString()),
                    column("Unit Price", i -> i.unitPrice().toPlainString()),
                    column("Total", i -> i.lineTotal().toPlainString()));
        }

        private void load() {
            stateLabel.setText("Loading purchase orders...");
            UiSupport.onFx(purchaseOrderApi.search(null, blankToNull(statusFilter.getValue()), 0, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No purchase orders found."
                        : result.totalElements() + " purchase orders");
            }, "Purchase orders could not be loaded.");
        }

        private void selectOrder(final UUID id) {
            UiSupport.onFx(purchaseOrderApi.get(id), po -> {
                selected = po;
                detailLabel.setText((po.poNumber() == null ? "(draft)" : po.poNumber()) + " - " + po.status()
                        + "   Total: " + po.totalAmount().toPlainString());
                itemsTable.setItems(FXCollections.observableArrayList(po.items()));
                final boolean draft = "DRAFT".equals(po.status());
                addItemButton.setDisable(!draft || !canCreatePo);
                approveButton.setDisable(!draft || !canApprovePo);
                sendButton.setDisable(!(draft || "APPROVED".equals(po.status())) || !canCreatePo);
                cancelButton.setDisable(!canCreatePo || java.util.Set.of("CLOSED", "CANCELLED", "PARTIALLY_RECEIVED",
                        "FULLY_RECEIVED").contains(po.status()));
            }, "Purchase order detail could not be loaded.");
        }

        private void openCreateDialog() {
            final Dialog<CreatePurchaseOrderRequest> dialog = new Dialog<>();
            dialog.setTitle("New Purchase Order");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final ComboBox<SupplierSummaryResponse> supplierCombo = new ComboBox<>();
            supplierCombo.setConverter(nameConverter(SupplierSummaryResponse::name));
            supplierCombo.setPromptText("Select supplier");
            UiSupport.onFx(supplierApi.search(null, "ACTIVE", 0, 200),
                    result -> supplierCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Suppliers could not be loaded.");
            final TextArea notes = new TextArea();
            notes.setPrefRowCount(2);

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Supplier"), supplierCombo);
            grid.addRow(1, new Label("Notes"), notes);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                if (supplierCombo.getValue() == null) {
                    UiSupport.alert("Select a supplier.");
                    return null;
                }
                return new CreatePurchaseOrderRequest(supplierCombo.getValue().supplierId(), LocalDate.now(), null,
                        null, notes.getText());
            });
            final CreatePurchaseOrderRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(purchaseOrderApi.create(request), created -> {
                UiSupport.alert("Purchase order draft created.");
                load();
            }, "Purchase order could not be created.");
        }

        private void openAddItemDialog() {
            if (selected == null) {
                return;
            }
            final Dialog<AddPurchaseOrderItemRequest> dialog = new Dialog<>();
            dialog.setTitle("Add Item");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final ComboBox<ProductSummaryResponse> productCombo = new ComboBox<>();
            productCombo.setConverter(nameConverter(p -> p.sku() + " - " + p.name()));
            productCombo.setEditable(true);
            productCombo.setPromptText("Type to search, then pick");
            productCombo.getEditor().setOnAction(event -> UiSupport.onFx(
                    catalogApi.products(productCombo.getEditor().getText(), null, "INVENTORY", true, 0, 50),
                    result -> productCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Products could not be searched."));
            final TextField quantity = new TextField();
            final TextField unitPrice = new TextField();

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Product"), productCombo);
            grid.addRow(1, new Label("Quantity"), quantity);
            grid.addRow(2, new Label("Unit Price"), unitPrice);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                if (productCombo.getValue() == null) {
                    UiSupport.alert("Select a product.");
                    return null;
                }
                try {
                    return new AddPurchaseOrderItemRequest(productCombo.getValue().productId(),
                            new BigDecimal(quantity.getText().trim()), new BigDecimal(unitPrice.getText().trim()));
                } catch (final NumberFormatException exception) {
                    UiSupport.alert("Quantity/price must be numbers.");
                    return null;
                }
            });
            final AddPurchaseOrderItemRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(purchaseOrderApi.addItem(selected.purchaseOrderId(), request), updated -> {
                selectOrder(selected.purchaseOrderId());
                load();
            }, "Item could not be added.");
        }

        private void decide(final boolean approve) {
            if (selected == null) {
                return;
            }
            final var request = new DecidePurchaseOrderRequest(selected.version());
            final var future = approve ? purchaseOrderApi.approve(selected.purchaseOrderId(), UUID.randomUUID(), request)
                    : purchaseOrderApi.send(selected.purchaseOrderId(), UUID.randomUUID(), request);
            UiSupport.onFx(future, result -> {
                UiSupport.alert(approve ? "Purchase order approved." : "Purchase order sent.");
                selectOrder(selected.purchaseOrderId());
                load();
            }, approve ? "Purchase order could not be approved." : "Purchase order could not be sent.");
        }

        private void cancelSelected() {
            if (selected == null) {
                return;
            }
            final Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Cancel Purchase Order");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            final TextArea reason = new TextArea();
            reason.setPrefRowCount(3);
            final VBox content = new VBox(8, new Label("Reason for cancelling " + (selected.poNumber() == null
                    ? "this draft" : selected.poNumber()) + ":"), reason);
            content.setPadding(new Insets(8));
            dialog.getDialogPane().setContent(content);
            dialog.setResultConverter(button -> button == ButtonType.OK ? reason.getText() : null);
            final String cancelReason = dialog.showAndWait().orElse(null);
            if (cancelReason == null || cancelReason.isBlank()) {
                return;
            }
            UiSupport.onFx(purchaseOrderApi.cancel(selected.purchaseOrderId(),
                    new CancelPurchaseOrderRequest(cancelReason, selected.version())), result -> {
                UiSupport.alert("Purchase order cancelled.");
                selectOrder(selected.purchaseOrderId());
                load();
            }, "Purchase order could not be cancelled.");
        }
    }

    /** 14.2-14.7: Goods Receipt draft/post workflow, with damaged/rejected qty per line. */
    private final class GoodsReceiptsPane {

        private final ComboBox<String> statusFilter = new ComboBox<>();
        private final TableView<GoodsReceiptSummaryResponse> table = new TableView<>();
        private final TableView<GoodsReceiptItemResponse> itemsTable = new TableView<>();
        private final Label stateLabel = new Label("Loading goods receipts...");
        private final Label detailLabel = new Label("Select a goods receipt");
        private final Button newButton = Icons.button("New Receipt", FontAwesomeSolid.PLUS);
        private final Button addItemButton = Icons.button("Add Item", FontAwesomeSolid.PLUS_CIRCLE);
        private final Button postButton = Icons.button("Post", FontAwesomeSolid.CHECK);
        private GoodsReceiptDetailResponse selected;

        Parent create() {
            configureTable();
            configureItemsTable();
            statusFilter.getItems().setAll("", "DRAFT", "POSTED", "REVERSED");
            statusFilter.setValue("");
            statusFilter.setOnAction(event -> load());
            newButton.setDisable(!canCreateGoodsReceipt);
            newButton.setOnAction(event -> openCreateDialog());
            final HBox header = new HBox(10, UiSupport.label("Goods Receipts", "screen-title"), spacer(),
                    statusFilter, newButton);
            header.getStyleClass().add("screen-header");

            final VBox center = new VBox(8, stateLabel, table);
            center.setPadding(new Insets(0, 12, 12, 12));
            VBox.setVgrow(table, Priority.ALWAYS);

            addItemButton.setDisable(true);
            postButton.setDisable(true);
            addItemButton.setOnAction(event -> openAddItemDialog());
            postButton.setOnAction(event -> postSelected());
            final HBox actions = new HBox(8, addItemButton, postButton);

            final VBox detailPanel = new VBox(12, UiSupport.label("Goods Receipt Detail", "panel-title"), detailLabel,
                    actions, itemsTable);
            detailPanel.getStyleClass().add("side-panel");
            detailPanel.setPadding(new Insets(16));
            detailPanel.setPrefWidth(600);
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
                    column("Receipt #", gr -> gr.receiptNumber() == null ? "(draft)" : gr.receiptNumber()),
                    column("Supplier", GoodsReceiptSummaryResponse::supplierName),
                    column("Date", gr -> String.valueOf(gr.receiptDate())),
                    column("Status", GoodsReceiptSummaryResponse::status),
                    column("Total", gr -> gr.totalAmount().toPlainString()));
            table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, gr) -> {
                if (gr != null) {
                    selectReceipt(gr.goodsReceiptId());
                }
            });
        }

        private void configureItemsTable() {
            itemsTable.getStyleClass().add("data-table");
            itemsTable.getColumns().setAll(
                    column("SKU", GoodsReceiptItemResponse::sku),
                    column("Product", GoodsReceiptItemResponse::productName),
                    column("Received", i -> i.quantityReceived().stripTrailingZeros().toPlainString()),
                    column("Damaged", i -> i.quantityDamaged().stripTrailingZeros().toPlainString()),
                    column("Rejected", i -> i.quantityRejected().stripTrailingZeros().toPlainString()),
                    column("Usable", i -> i.usableQuantity().stripTrailingZeros().toPlainString()),
                    column("Unit Cost", i -> i.unitCost().toPlainString()),
                    column("Total", i -> i.totalCost().toPlainString()));
        }

        private void load() {
            stateLabel.setText("Loading goods receipts...");
            UiSupport.onFx(goodsReceiptApi.search(null, blankToNull(statusFilter.getValue()), 0, PAGE_SIZE), result -> {
                table.setItems(FXCollections.observableArrayList(result.data()));
                stateLabel.setText(result.totalElements() == 0 ? "No goods receipts found."
                        : result.totalElements() + " goods receipts");
            }, "Goods receipts could not be loaded.");
        }

        private void selectReceipt(final UUID id) {
            UiSupport.onFx(goodsReceiptApi.get(id), gr -> {
                selected = gr;
                detailLabel.setText((gr.receiptNumber() == null ? "(draft)" : gr.receiptNumber()) + " - " + gr.status()
                        + "   Total: " + gr.totalAmount().toPlainString());
                itemsTable.setItems(FXCollections.observableArrayList(gr.items()));
                final boolean draft = "DRAFT".equals(gr.status());
                addItemButton.setDisable(!draft || !canCreateGoodsReceipt);
                postButton.setDisable(!draft || !canCreateGoodsReceipt);
            }, "Goods receipt detail could not be loaded.");
        }

        private void openCreateDialog() {
            final Dialog<CreateGoodsReceiptRequest> dialog = new Dialog<>();
            dialog.setTitle("New Goods Receipt");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final ComboBox<SupplierSummaryResponse> supplierCombo = new ComboBox<>();
            supplierCombo.setConverter(nameConverter(SupplierSummaryResponse::name));
            supplierCombo.setPromptText("Select supplier");
            UiSupport.onFx(supplierApi.search(null, "ACTIVE", 0, 200),
                    result -> supplierCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Suppliers could not be loaded.");
            final TextField supplierReference = new TextField();
            supplierReference.setPromptText("Supplier invoice/delivery note # (optional)");
            final TextArea notes = new TextArea();
            notes.setPrefRowCount(2);

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Supplier"), supplierCombo);
            grid.addRow(1, new Label("Supplier Reference"), supplierReference);
            grid.addRow(2, new Label("Notes"), notes);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                if (supplierCombo.getValue() == null) {
                    UiSupport.alert("Select a supplier.");
                    return null;
                }
                return new CreateGoodsReceiptRequest(null, supplierCombo.getValue().supplierId(),
                        blankToNull(supplierReference.getText()), LocalDate.now(), notes.getText());
            });
            final CreateGoodsReceiptRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(goodsReceiptApi.create(request), created -> {
                UiSupport.alert("Goods receipt draft created.");
                load();
            }, "Goods receipt could not be created.");
        }

        private void openAddItemDialog() {
            if (selected == null) {
                return;
            }
            final Dialog<AddGoodsReceiptItemRequest> dialog = new Dialog<>();
            dialog.setTitle("Add Item");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            final ComboBox<ProductSummaryResponse> productCombo = new ComboBox<>();
            productCombo.setConverter(nameConverter(p -> p.sku() + " - " + p.name()));
            productCombo.setEditable(true);
            productCombo.setPromptText("Type to search, then pick");
            productCombo.getEditor().setOnAction(event -> UiSupport.onFx(
                    catalogApi.products(productCombo.getEditor().getText(), null, "INVENTORY", true, 0, 50),
                    result -> productCombo.setItems(FXCollections.observableArrayList(result.data())),
                    "Products could not be searched."));
            final TextField quantityReceived = new TextField();
            final TextField quantityDamaged = new TextField("0");
            final TextField quantityRejected = new TextField("0");
            final TextField unitCost = new TextField();

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.addRow(0, new Label("Product"), productCombo);
            grid.addRow(1, new Label("Qty Received"), quantityReceived);
            grid.addRow(2, new Label("Qty Damaged"), quantityDamaged);
            grid.addRow(3, new Label("Qty Rejected"), quantityRejected);
            grid.addRow(4, new Label("Unit Cost"), unitCost);
            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button -> {
                if (button != ButtonType.OK) {
                    return null;
                }
                if (productCombo.getValue() == null) {
                    UiSupport.alert("Select a product.");
                    return null;
                }
                try {
                    return new AddGoodsReceiptItemRequest(null, productCombo.getValue().productId(),
                            new BigDecimal(quantityReceived.getText().trim()),
                            new BigDecimal(quantityDamaged.getText().trim()),
                            new BigDecimal(quantityRejected.getText().trim()),
                            new BigDecimal(unitCost.getText().trim()));
                } catch (final NumberFormatException exception) {
                    UiSupport.alert("Quantities/cost must be numbers.");
                    return null;
                }
            });
            final AddGoodsReceiptItemRequest request = dialog.showAndWait().orElse(null);
            if (request == null) {
                return;
            }
            UiSupport.onFx(goodsReceiptApi.addItem(selected.goodsReceiptId(), request), updated -> {
                selectReceipt(selected.goodsReceiptId());
                load();
            }, "Item could not be added.");
        }

        private void postSelected() {
            if (selected == null) {
                return;
            }
            UiSupport.onFx(goodsReceiptApi.post(selected.goodsReceiptId(), UUID.randomUUID(),
                    new PostGoodsReceiptRequest(selected.version())), result -> {
                UiSupport.alert("Goods receipt " + result.receiptNumber() + " posted.");
                selectReceipt(selected.goodsReceiptId());
                load();
            }, "Goods receipt could not be posted.");
        }
    }

    private static <T> javafx.util.StringConverter<T> nameConverter(final Function<T, String> toText) {
        return new javafx.util.StringConverter<>() {
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

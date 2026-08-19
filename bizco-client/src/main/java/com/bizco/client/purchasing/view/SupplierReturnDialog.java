package com.bizco.client.purchasing.view;

import com.bizco.client.purchasing.service.GoodsReceiptApiClient;
import com.bizco.client.purchasing.service.SupplierApiClient;
import com.bizco.client.purchasing.service.SupplierReturnApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptItemResponse;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnItemRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.CreateSupplierReturnRequest;
import com.bizco.common.dto.purchasing.SupplierReturnDtos.SupplierReturnResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Return-to-supplier dialog (DevelopmentPlan.md Week 15 task 15.6): pick a supplier, pick one of
 * that supplier's POSTED goods receipts, and enter a return quantity per line - mirrors
 * {@code ReturnDialog}'s per-line eligibility pattern on the sales side.
 */
class SupplierReturnDialog {

    private final SupplierReturnApiClient supplierReturnApi;
    private final GoodsReceiptApiClient goodsReceiptApi;
    private final SupplierApiClient supplierApi;
    private final Consumer<SupplierReturnResponse> onCreated;
    private final ComboBox<SupplierSummaryResponse> supplierCombo = new ComboBox<>();
    private final ComboBox<GoodsReceiptSummaryResponse> receiptCombo = new ComboBox<>();
    private final VBox lineRows = new VBox(6);
    private final List<ReturnLineRow> rows = new ArrayList<>();
    private final TextArea reasonField = new TextArea();
    private Stage stage;

    SupplierReturnDialog(final SupplierReturnApiClient supplierReturnApi, final GoodsReceiptApiClient goodsReceiptApi,
                         final SupplierApiClient supplierApi, final Consumer<SupplierReturnResponse> onCreated) {
        this.supplierReturnApi = supplierReturnApi;
        this.goodsReceiptApi = goodsReceiptApi;
        this.supplierApi = supplierApi;
        this.onCreated = onCreated;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Return to Supplier");
        stage.initModality(Modality.APPLICATION_MODAL);

        supplierCombo.setConverter(nameConverter(SupplierSummaryResponse::name));
        supplierCombo.setPromptText("Select supplier");
        UiSupport.onFx(supplierApi.search(null, "ACTIVE", 0, 200),
                result -> supplierCombo.setItems(FXCollections.observableArrayList(result.data())),
                "Suppliers could not be loaded.");
        supplierCombo.setOnAction(event -> loadPostedReceipts());

        receiptCombo.setConverter(nameConverter(GoodsReceiptSummaryResponse::receiptNumber));
        receiptCombo.setPromptText("Select goods receipt");
        receiptCombo.setOnAction(event -> loadReceiptItems());

        reasonField.setPromptText("Reason for return");
        reasonField.setPrefRowCount(2);

        final Button submitButton = Icons.button("Create Return", FontAwesomeSolid.UNDO);
        submitButton.setOnAction(event -> submit());
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final HBox actions = new HBox(10, spacer(), cancelButton, submitButton);

        final VBox content = new VBox(12, new Label("Supplier"), supplierCombo, new Label("Goods Receipt"),
                receiptCombo, new Label("Enter the quantity to return for each line:"), lineRows,
                new Label("Reason"), reasonField, actions);
        content.setPadding(new Insets(16));
        content.setPrefWidth(560);

        stage.setScene(new Scene(content));
        stage.show();
    }

    private void loadPostedReceipts() {
        receiptCombo.getItems().clear();
        lineRows.getChildren().clear();
        rows.clear();
        final SupplierSummaryResponse supplier = supplierCombo.getValue();
        if (supplier == null) {
            return;
        }
        UiSupport.onFx(goodsReceiptApi.search(supplier.supplierId(), "POSTED", 0, 100),
                result -> receiptCombo.setItems(FXCollections.observableArrayList(result.data())),
                "Goods receipts could not be loaded.");
    }

    private void loadReceiptItems() {
        lineRows.getChildren().clear();
        rows.clear();
        final GoodsReceiptSummaryResponse receipt = receiptCombo.getValue();
        if (receipt == null) {
            return;
        }
        UiSupport.onFx(goodsReceiptApi.get(receipt.goodsReceiptId()), detail -> {
            for (final GoodsReceiptItemResponse item : detail.items()) {
                if (item.usableQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                lineRows.getChildren().add(buildRow(item));
            }
            if (rows.isEmpty()) {
                lineRows.getChildren().add(UiSupport.label("No returnable lines on this receipt.", "form-caption"));
            }
        }, "Goods receipt detail could not be loaded.");
    }

    private HBox buildRow(final GoodsReceiptItemResponse item) {
        final Label description = new Label(item.sku() + " - " + item.productName()
                + "  (received " + item.usableQuantity().stripTrailingZeros().toPlainString() + " usable)");
        description.setPrefWidth(340);
        final TextField quantity = new TextField("0");
        quantity.setPrefWidth(70);
        final HBox row = new HBox(10, description, quantity);
        row.setAlignment(Pos.CENTER_LEFT);
        rows.add(new ReturnLineRow(item.goodsReceiptItemId(), quantity));
        return row;
    }

    private void submit() {
        final GoodsReceiptSummaryResponse receipt = receiptCombo.getValue();
        final SupplierSummaryResponse supplier = supplierCombo.getValue();
        if (supplier == null || receipt == null) {
            UiSupport.alert("Select a supplier and a goods receipt.");
            return;
        }
        if (reasonField.getText() == null || reasonField.getText().isBlank()) {
            UiSupport.alert("Enter a reason for the return.");
            return;
        }
        final List<CreateSupplierReturnItemRequest> items = new ArrayList<>();
        for (final ReturnLineRow row : rows) {
            final BigDecimal quantity = parse(row.quantityField.getText());
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                items.add(new CreateSupplierReturnItemRequest(row.goodsReceiptItemId, quantity));
            }
        }
        if (items.isEmpty()) {
            UiSupport.alert("Enter a return quantity for at least one line.");
            return;
        }
        UiSupport.onFx(supplierReturnApi.create(UUID.randomUUID(), new CreateSupplierReturnRequest(
                        supplier.supplierId(), receipt.goodsReceiptId(), reasonField.getText(), items)),
                created -> {
                    stage.close();
                    onCreated.accept(created);
                }, "Supplier return could not be created.");
    }

    private BigDecimal parse(final String text) {
        try {
            return text == null || text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text.trim());
        } catch (final NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private <T> StringConverter<T> nameConverter(final java.util.function.Function<T, String> toText) {
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

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record ReturnLineRow(UUID goodsReceiptItemId, TextField quantityField) {
    }
}

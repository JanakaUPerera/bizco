package com.bizco.client.purchasing.view;

import com.bizco.client.purchasing.service.SupplierApiClient;
import com.bizco.client.purchasing.service.SupplierPaymentApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.purchasing.GoodsReceiptDtos.GoodsReceiptOutstandingResponse;
import com.bizco.common.dto.purchasing.SupplierDtos.SupplierSummaryResponse;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.RecordSupplierPaymentRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentAllocationRequest;
import com.bizco.common.dto.purchasing.SupplierPaymentDtos.SupplierPaymentResponse;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Supplier payment dialog (DevelopmentPlan.md Week 15 task 15.6): pick a supplier, enter the total
 * amount paid, and optionally allocate it across one or more of that supplier's outstanding goods
 * receipts - mirrors {@code PaymentDialog}'s multi-row pattern on the sales side. An unallocated
 * remainder is left as supplier-account credit (DatabaseDesign.md &sect;17.11).
 */
class SupplierPaymentDialog {

    private final SupplierPaymentApiClient supplierPaymentApi;
    private final SupplierApiClient supplierApi;
    private final Consumer<SupplierPaymentResponse> onCreated;
    private final ComboBox<SupplierSummaryResponse> supplierCombo = new ComboBox<>();
    private final ComboBox<String> methodCombo = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final TextField referenceField = new TextField();
    private final TextArea notesField = new TextArea();
    private final VBox allocationRows = new VBox(6);
    private final List<AllocationRow> rows = new ArrayList<>();
    private final UUID preselectedSupplierId;
    private Stage stage;

    SupplierPaymentDialog(final SupplierPaymentApiClient supplierPaymentApi, final SupplierApiClient supplierApi,
                          final UUID preselectedSupplierId, final Consumer<SupplierPaymentResponse> onCreated) {
        this.supplierPaymentApi = supplierPaymentApi;
        this.supplierApi = supplierApi;
        this.preselectedSupplierId = preselectedSupplierId;
        this.onCreated = onCreated;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Record Supplier Payment");
        stage.initModality(Modality.APPLICATION_MODAL);

        supplierCombo.setConverter(nameConverter(SupplierSummaryResponse::name));
        supplierCombo.setPromptText("Select supplier");
        UiSupport.onFx(supplierApi.search(null, "ACTIVE", 0, 200), result -> {
            supplierCombo.setItems(FXCollections.observableArrayList(result.data()));
            if (preselectedSupplierId != null) {
                result.data().stream().filter(s -> s.supplierId().equals(preselectedSupplierId)).findFirst()
                        .ifPresent(supplierCombo::setValue);
                loadOutstanding();
            }
        }, "Suppliers could not be loaded.");
        supplierCombo.setOnAction(event -> loadOutstanding());

        methodCombo.getItems().setAll("CASH", "CARD", "BANK_TRANSFER", "CHEQUE");
        methodCombo.setValue("CASH");
        referenceField.setPromptText("Reference (cheque / transfer no.)");
        notesField.setPromptText("Notes");
        notesField.setPrefRowCount(2);

        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Supplier"), supplierCombo);
        grid.addRow(1, new Label("Amount"), amountField);
        grid.addRow(2, new Label("Method"), methodCombo);
        grid.addRow(3, new Label("Reference"), referenceField);
        grid.addRow(4, new Label("Notes"), notesField);

        final Button submitButton = Icons.button("Record Payment", FontAwesomeSolid.MONEY_BILL_WAVE);
        submitButton.setOnAction(event -> submit());
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final HBox actions = new HBox(10, spacer(), cancelButton, submitButton);

        final VBox content = new VBox(12, grid,
                new Label("Allocate to outstanding goods receipts (optional - leave blank for supplier credit):"),
                allocationRows, actions);
        content.setPadding(new Insets(16));
        content.setPrefWidth(560);

        stage.setScene(new Scene(content));
        stage.show();
    }

    private void loadOutstanding() {
        allocationRows.getChildren().clear();
        rows.clear();
        final SupplierSummaryResponse supplier = supplierCombo.getValue();
        if (supplier == null) {
            return;
        }
        UiSupport.onFx(supplierPaymentApi.outstanding(supplier.supplierId(), true, 0, 100), result -> {
            for (final GoodsReceiptOutstandingResponse row : result.data()) {
                allocationRows.getChildren().add(buildRow(row));
            }
            if (rows.isEmpty()) {
                allocationRows.getChildren().add(UiSupport.label("No outstanding goods receipts for this supplier.",
                        "form-caption"));
            }
        }, "Outstanding goods receipts could not be loaded.");
    }

    private HBox buildRow(final GoodsReceiptOutstandingResponse row) {
        final Label description = new Label(row.receiptNumber() + "  (outstanding "
                + row.outstandingAmount().toPlainString() + ")");
        description.setPrefWidth(300);
        final TextField amount = new TextField();
        amount.setPromptText("0.00");
        amount.setPrefWidth(90);
        final HBox line = new HBox(10, description, amount);
        line.setAlignment(Pos.CENTER_LEFT);
        rows.add(new AllocationRow(row.goodsReceiptId(), amount));
        return line;
    }

    private void submit() {
        final SupplierSummaryResponse supplier = supplierCombo.getValue();
        if (supplier == null) {
            UiSupport.alert("Select a supplier.");
            return;
        }
        final BigDecimal amount = parse(amountField.getText());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            UiSupport.alert("Enter a payment amount greater than zero.");
            return;
        }
        final List<SupplierPaymentAllocationRequest> allocations = new ArrayList<>();
        for (final AllocationRow row : rows) {
            final BigDecimal allocated = parse(row.amountField.getText());
            if (allocated.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new SupplierPaymentAllocationRequest(row.goodsReceiptId, allocated));
            }
        }
        final RecordSupplierPaymentRequest request = new RecordSupplierPaymentRequest(supplier.supplierId(), null,
                methodCombo.getValue(), amount, blankToNull(referenceField.getText()), notesField.getText(), allocations);
        UiSupport.onFx(supplierPaymentApi.record(UUID.randomUUID(), request), created -> {
            stage.close();
            onCreated.accept(created);
        }, "Supplier payment could not be recorded.");
    }

    private BigDecimal parse(final String text) {
        try {
            return text == null || text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text.trim());
        } catch (final NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
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

    private record AllocationRow(UUID goodsReceiptId, TextField amountField) {
    }
}

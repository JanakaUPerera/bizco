package com.bizco.client.sales.view;

import com.bizco.client.sales.service.CreditNoteApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.sales.CreditNoteDtos.CreateCreditNoteRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteLineRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.ReturnEligibilityLineResponse;
import com.bizco.common.dto.sales.CreditNoteDtos.SettlementRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Return / credit note dialog (ApiContracts.md &sect;18): loads return-eligibility for the
 * invoice, lets the cashier/manager enter a return quantity and restock flag per eligible line,
 * and issues the credit note with the chosen settlement in one request.
 */
class ReturnDialog {

    private final CreditNoteApiClient creditNoteApiClient;
    private final InvoiceDetailResponse invoice;
    private final Consumer<CreditNoteResponse> onIssued;
    private final VBox lineRows = new VBox(6);
    private final List<ReturnLineRow> rows = new ArrayList<>();
    private final ComboBox<String> settlementType = new ComboBox<>();
    private final ComboBox<String> paymentMethod = new ComboBox<>();
    private final TextArea reasonField = new TextArea();
    private Stage stage;

    ReturnDialog(final CreditNoteApiClient creditNoteApiClient, final InvoiceDetailResponse invoice,
                final Consumer<CreditNoteResponse> onIssued) {
        this.creditNoteApiClient = creditNoteApiClient;
        this.invoice = invoice;
        this.onIssued = onIssued;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Return / Credit Note - " + invoice.invoiceNumber());
        stage.initModality(Modality.APPLICATION_MODAL);

        reasonField.setPromptText("Reason for return");
        reasonField.setPrefRowCount(2);
        settlementType.getItems().setAll("APPLY_TO_BALANCE", "REFUND", "CUSTOMER_CREDIT");
        settlementType.setValue("APPLY_TO_BALANCE");
        paymentMethod.getItems().setAll("CASH", "CARD", "BANK_TRANSFER", "CHEQUE");
        paymentMethod.setValue("CASH");
        paymentMethod.disableProperty().bind(settlementType.valueProperty().isNotEqualTo("REFUND"));

        final GridPane settlementGrid = new GridPane();
        settlementGrid.setHgap(10);
        settlementGrid.setVgap(8);
        settlementGrid.addRow(0, new Label("Reason"), reasonField);
        settlementGrid.addRow(1, new Label("Settlement"), settlementType);
        settlementGrid.addRow(2, new Label("Refund Method"), paymentMethod);

        final Button submitButton = Icons.button("Issue Credit Note", FontAwesomeSolid.UNDO);
        submitButton.setOnAction(event -> submit());
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final HBox actions = new HBox(10, spacer(), cancelButton, submitButton);

        final VBox content = new VBox(12, header(), lineRows, settlementGrid, actions);
        content.setPadding(new Insets(16));
        content.setPrefWidth(560);

        stage.setScene(new Scene(content));
        loadEligibility();
        stage.show();
    }

    private HBox header() {
        final Label label = new Label("Enter the quantity to return for each eligible line:");
        return new HBox(label);
    }

    private void loadEligibility() {
        UiSupport.onFx(creditNoteApiClient.returnEligibility(invoice.invoiceId()), eligibility -> {
            lineRows.getChildren().clear();
            rows.clear();
            if (!eligibility.withinWindow()) {
                lineRows.getChildren().add(UiSupport.label(
                        "Return window expired on " + eligibility.returnDeadline() + " - a manager override is required.",
                        "form-caption"));
            }
            for (final ReturnEligibilityLineResponse line : eligibility.lines()) {
                if (line.quantityRemaining().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                lineRows.getChildren().add(buildRow(line));
            }
            if (rows.isEmpty()) {
                lineRows.getChildren().add(UiSupport.label("No eligible lines remain for return.", "form-caption"));
            }
        }, "Return eligibility could not be loaded.");
    }

    private HBox buildRow(final ReturnEligibilityLineResponse line) {
        final Label description = new Label(line.description() + "  (sold " + line.quantitySold().stripTrailingZeros()
                + ", remaining " + line.quantityRemaining().stripTrailingZeros() + ")");
        description.setPrefWidth(300);
        final TextField quantity = new TextField("0");
        quantity.setPrefWidth(70);
        final CheckBox restock = new CheckBox("Restock");
        restock.setSelected(line.restockEligible());
        restock.setDisable(!line.restockEligible());
        final HBox row = new HBox(10, description, quantity, restock);
        row.setAlignment(Pos.CENTER_LEFT);
        rows.add(new ReturnLineRow(line.invoiceLineId(), quantity, restock));
        return row;
    }

    private void submit() {
        final List<CreditNoteLineRequest> lines = new ArrayList<>();
        for (final ReturnLineRow row : rows) {
            final BigDecimal quantity = parse(row.quantityField.getText());
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(new CreditNoteLineRequest(row.invoiceLineId, quantity, row.restockBox.isSelected()));
            }
        }
        if (lines.isEmpty()) {
            UiSupport.alert("Enter a return quantity for at least one line.");
            return;
        }
        final SettlementRequest settlement = new SettlementRequest(settlementType.getValue(),
                "REFUND".equals(settlementType.getValue()) ? paymentMethod.getValue() : null, null);
        UiSupport.onFx(creditNoteApiClient.issue(UUID.randomUUID(), new CreateCreditNoteRequest(invoice.invoiceId(),
                        reasonField.getText(), lines, settlement)),
                creditNote -> {
                    stage.close();
                    onIssued.accept(creditNote);
                }, "Return could not be issued.");
    }

    private BigDecimal parse(final String text) {
        try {
            return text == null || text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text.trim());
        } catch (final NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record ReturnLineRow(UUID invoiceLineId, TextField quantityField, CheckBox restockBox) {
    }
}

package com.bizco.client.sales.view;

import com.bizco.client.sales.service.InvoiceApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.PaymentLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.PostInvoiceResponse;
import com.bizco.common.dto.sales.SalesApprovalDtos.SalesApprovalRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * POS checkout screen (DevelopmentPlan.md Week 7 task 7.8): collect one or more payment lines,
 * optionally leave a credit balance, capture manager approval evidence when the server rejects a
 * discount/below-cost line, and post. Approval detection is reactive rather than pre-computed
 * client-side: submit posts as normal, and if the server rejects for
 * INVOICE_DISCOUNT_APPROVAL_REQUIRED/INVOICE_BELOW_COST_APPROVAL_REQUIRED the cashier adds the
 * matching approval here (server-side re-authentication of the approver, ApiContracts.md &sect;14)
 * and retries - the same evidence flow SalesApprovalController already exposes, just surfaced at
 * the point of friction instead of guessed at up front.
 */
class PaymentDialog {

    private final InvoiceApiClient invoiceApiClient;
    private final InvoiceDetailResponse invoice;
    private final Consumer<PostInvoiceResponse> onPosted;
    private final VBox paymentLines = new VBox(8);
    private final List<PaymentRow> rows = new ArrayList<>();
    private final List<UUID> approvalIds = new ArrayList<>();
    private final javafx.scene.control.ListView<String> approvalList = new javafx.scene.control.ListView<>();
    private final CheckBox creditSaleBox = new CheckBox("Credit sale (leave a balance on the customer's account)");
    private final Label balanceLabel = new Label();
    private Stage stage;

    PaymentDialog(final InvoiceApiClient invoiceApiClient, final InvoiceDetailResponse invoice,
                 final Consumer<PostInvoiceResponse> onPosted) {
        this.invoiceApiClient = invoiceApiClient;
        this.invoice = invoice;
        this.onPosted = onPosted;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Complete Sale - " + invoice.totalAmount().toPlainString());
        stage.initModality(Modality.APPLICATION_MODAL);

        creditSaleBox.setDisable(invoice.customerId() == null);
        creditSaleBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateBalance());

        addPaymentRow();
        final Button addRowButton = Icons.button("Add Payment Line", FontAwesomeSolid.PLUS);
        addRowButton.setOnAction(event -> addPaymentRow());
        final Button exactCashButton = Icons.button("Exact Cash", FontAwesomeSolid.MONEY_BILL_WAVE);
        exactCashButton.setOnAction(event -> {
            rows.clear();
            paymentLines.getChildren().clear();
            addPaymentRow();
            rows.get(0).amountField.setText(invoice.totalAmount().toPlainString());
            updateBalance();
        });

        approvalList.setPrefHeight(80);
        final Button addApprovalButton = Icons.button("Request Manager Approval", FontAwesomeSolid.USER_SHIELD);
        addApprovalButton.setOnAction(event -> requestApproval());

        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final Button completeButton = Icons.button("Complete Sale", FontAwesomeSolid.CHECK_CIRCLE);
        completeButton.setOnAction(event -> submit());

        final VBox content = new VBox(12,
                summary(),
                creditSaleBox,
                new HBox(10, new javafx.scene.control.Label("Payments"), spacer(), exactCashButton, addRowButton),
                paymentLines,
                balanceLabel,
                new javafx.scene.control.Separator(),
                new javafx.scene.control.Label("Manager Approvals (if a discount or below-cost sale needs one)"),
                approvalList,
                addApprovalButton,
                new HBox(10, spacer(), cancelButton, completeButton));
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);
        updateBalance();

        stage.setScene(new Scene(content));
        stage.showAndWait();
    }

    private GridPane summary() {
        final GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);
        grid.addRow(0, new Label("Subtotal"), new Label(invoice.subtotal().toPlainString()));
        grid.addRow(1, new Label("VAT"), new Label(invoice.vatAmount().toPlainString()));
        grid.addRow(2, new Label("Total Due"), new Label(invoice.totalAmount().toPlainString()));
        return grid;
    }

    private void addPaymentRow() {
        final ComboBox<String> method = new ComboBox<>(FXCollections.observableArrayList(
                "CASH", "CARD", "BANK_TRANSFER", "CHEQUE"));
        method.setValue("CASH");
        final TextField amount = numberField("");
        amount.setPromptText("Amount");
        amount.textProperty().addListener((observable, oldValue, newValue) -> updateBalance());
        final TextField reference = new TextField();
        reference.setPromptText("Reference (card auth / cheque no.)");
        final Button remove = Icons.iconOnlyButton(FontAwesomeSolid.TIMES, "btn-icon-glyph");
        final PaymentRow row = new PaymentRow(method, amount, reference);
        remove.setOnAction(event -> {
            rows.remove(row);
            paymentLines.getChildren().remove(row.container);
            updateBalance();
        });
        final HBox line = new HBox(8, method, amount, reference, remove);
        HBox.setHgrow(reference, Priority.ALWAYS);
        row.container = line;
        rows.add(row);
        paymentLines.getChildren().add(line);
    }

    private void updateBalance() {
        final BigDecimal paid = rows.stream().map(PaymentRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal balance = invoice.totalAmount().subtract(paid);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            balanceLabel.setText("Balance: 0.00 (paid in full)");
        } else if (creditSaleBox.isSelected()) {
            balanceLabel.setText("Balance: " + balance.toPlainString() + " (goes to customer's account)");
        } else {
            balanceLabel.setText("Balance: " + balance.toPlainString() + " - immediate sales must be paid in full");
        }
    }

    private void requestApproval() {
        final Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Manager Approval");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button);
        final ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                "DISCOUNT_10_25", "DISCOUNT_OVER_25", "BELOW_COST", "PRICE_OVERRIDE"));
        type.setValue("DISCOUNT_10_25");
        final TextField reason = new TextField();
        reason.setPromptText("Reason");
        final TextField approverUsername = new TextField();
        approverUsername.setPromptText("Manager username");
        final PasswordField approverPassword = new PasswordField();
        approverPassword.setPromptText("Manager password");
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Type"), type);
        grid.addRow(1, new Label("Reason"), reason);
        grid.addRow(2, new Label("Approver Username"), approverUsername);
        grid.addRow(3, new Label("Approver Password"), approverPassword);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button ->
                UiSupport.onFx(invoiceApiClient.requestApproval(invoice.invoiceId(), new SalesApprovalRequest(
                                type.getValue(), null, null, reason.getText(), approverUsername.getText(),
                                approverPassword.getText())),
                        approval -> {
                            approvalIds.add(approval.approvalId());
                            approvalList.setItems(FXCollections.observableArrayList(
                                    approvalIds.stream().map(id -> type.getValue() + " approved").toList()));
                        }, "Approval could not be granted."));
    }

    private void submit() {
        final List<PaymentLineRequest> payments = rows.stream()
                .filter(row -> row.amount().compareTo(BigDecimal.ZERO) > 0)
                .map(row -> new PaymentLineRequest(row.method.getValue(), row.amount(),
                        row.referenceField.getText().isBlank() ? null : row.referenceField.getText()))
                .toList();
        final PostInvoiceRequest request = new PostInvoiceRequest(invoice.version(), payments,
                creditSaleBox.isSelected(), approvalIds);
        UiSupport.onFx(invoiceApiClient.postInvoice(invoice.invoiceId(), UUID.randomUUID(), request), posted -> {
            stage.close();
            onPosted.accept(posted);
        }, "Sale could not be completed.");
    }

    private TextField numberField(final String initial) {
        final TextField field = new TextField(initial);
        field.setTextFormatter(new TextFormatter<>(new BigDecimalStringConverter()));
        return field;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static final class PaymentRow {
        private final ComboBox<String> method;
        private final TextField amountField;
        private final TextField referenceField;
        private HBox container;

        private PaymentRow(final ComboBox<String> method, final TextField amountField, final TextField referenceField) {
            this.method = method;
            this.amountField = amountField;
            this.referenceField = referenceField;
        }

        private BigDecimal amount() {
            try {
                return amountField.getText() == null || amountField.getText().isBlank()
                        ? BigDecimal.ZERO : new BigDecimal(amountField.getText().trim());
            } catch (final NumberFormatException exception) {
                return BigDecimal.ZERO;
            }
        }
    }
}

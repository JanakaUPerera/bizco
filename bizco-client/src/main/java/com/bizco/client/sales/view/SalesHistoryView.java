package com.bizco.client.sales.view;

import com.bizco.client.sales.service.CreditNoteApiClient;
import com.bizco.client.sales.service.InvoiceApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.finance.PaymentDtos.PaymentAllocationResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordInvoicePaymentRequest;
import com.bizco.common.dto.sales.CreditNoteDtos.CreditNoteResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceLineResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceSummaryResponse;
import com.bizco.common.dto.sales.InvoiceDtos.VoidInvoiceRequest;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Invoice history screen (DevelopmentPlan.md Week 8 task 8.6): search/browse posted invoices and
 * drill into one for its lines, payment history, and return history, with void/record-payment/
 * return/receipt actions gated by the same permissions the server enforces.
 */
public class SalesHistoryView {

    private static final int PAGE_SIZE = 25;

    private final InvoiceApiClient invoiceApiClient;
    private final CreditNoteApiClient creditNoteApiClient;
    private final boolean canVoid;
    private final boolean canRecordPayment;
    private final boolean canReturn;
    private final boolean canReprint;

    private final TextField numberField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<InvoiceSummaryResponse> table = new TableView<>();
    private final Label stateLabel = new Label("Loading invoices...");
    private final Label detailLabel = new Label("Select an invoice");
    private final TableView<InvoiceLineResponse> linesTable = new TableView<>();
    private final Label totalsLabel = new Label();
    private final TableView<PaymentRow> paymentsTable = new TableView<>();
    private final TableView<CreditNoteResponse> returnsTable = new TableView<>();
    private final Button voidButton = Icons.button("Void", FontAwesomeSolid.BAN);
    private final Button recordPaymentButton = Icons.button("Record Payment", FontAwesomeSolid.MONEY_BILL_WAVE);
    private final Button returnButton = Icons.button("New Return", FontAwesomeSolid.UNDO);
    private final Button receiptButton = Icons.button("View Receipt", FontAwesomeSolid.FILE_PDF);
    private final Button reprintButton = Icons.button("Reprint", FontAwesomeSolid.PRINT);

    private InvoiceDetailResponse selected;

    public SalesHistoryView(final InvoiceApiClient invoiceApiClient, final CreditNoteApiClient creditNoteApiClient,
                            final boolean canVoid, final boolean canRecordPayment, final boolean canReturn,
                            final boolean canReprint) {
        this.invoiceApiClient = invoiceApiClient;
        this.creditNoteApiClient = creditNoteApiClient;
        this.canVoid = canVoid;
        this.canRecordPayment = canRecordPayment;
        this.canReturn = canReturn;
        this.canReprint = canReprint;
    }

    public Parent createView() {
        configureTable();
        configureLinesTable();
        configurePaymentsTable();
        configureReturnsTable();

        statusFilter.getItems().setAll("", "DRAFT", "POSTED", "VOIDED");
        statusFilter.setValue("");
        numberField.setPromptText("Search invoice number");
        numberField.setOnAction(event -> load());
        statusFilter.setOnAction(event -> load());
        final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
        searchButton.setOnAction(event -> load());
        final HBox header = new HBox(10, UiSupport.label("Sales History", "screen-title"), spacer(), numberField,
                statusFilter, searchButton);
        header.getStyleClass().add("screen-header");

        final VBox center = new VBox(8, stateLabel, table);
        center.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(table, Priority.ALWAYS);

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header);
        root.setCenter(center);
        root.setRight(detailPanel());
        load();
        return root;
    }

    private void configureTable() {
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Number", InvoiceSummaryResponse::invoiceNumber),
                column("Date", i -> String.valueOf(i.invoiceDate())),
                column("Status", InvoiceSummaryResponse::status),
                column("Total", i -> i.totalAmount().toPlainString()));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, invoice) -> {
            if (invoice != null) {
                selectInvoice(invoice.invoiceId());
            }
        });
    }

    private VBox detailPanel() {
        voidButton.setDisable(true);
        recordPaymentButton.setDisable(true);
        returnButton.setDisable(true);
        receiptButton.setDisable(true);
        reprintButton.setDisable(true);
        voidButton.setOnAction(event -> voidSelected());
        recordPaymentButton.setOnAction(event -> recordPaymentForSelected());
        returnButton.setOnAction(event -> new ReturnDialog(creditNoteApiClient, selected, response -> {
            UiSupport.alert("Credit note " + response.creditNoteNumber() + " issued.");
            selectInvoice(selected.invoiceId());
        }).show());
        receiptButton.setOnAction(event -> openPdf(invoiceApiClient.receipt(selected.invoiceId())));
        reprintButton.setOnAction(event -> openPdf(invoiceApiClient.reprintReceipt(selected.invoiceId())));
        final HBox actions = new HBox(8, voidButton, recordPaymentButton, returnButton, receiptButton, reprintButton);
        actions.setStyle("-fx-wrap-text: true;");

        final VBox detailsTab = new VBox(8, linesTable, totalsLabel);
        VBox.setVgrow(linesTable, Priority.ALWAYS);
        final Tab details = new Tab("Details", detailsTab);
        final Tab payments = new Tab("Payments", paymentsTable);
        final Tab returns = new Tab("Returns", returnsTable);
        final TabPane tabs = new TabPane(details, payments, returns);
        tabs.getTabs().forEach(tab -> tab.setClosable(false));

        final VBox panel = new VBox(12, UiSupport.label("Invoice Detail", "panel-title"), detailLabel, actions, tabs);
        panel.getStyleClass().add("side-panel");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(520);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return panel;
    }

    private void configureLinesTable() {
        linesTable.getStyleClass().add("data-table");
        linesTable.getColumns().setAll(
                column("Description", InvoiceLineResponse::descriptionSnapshot),
                column("Qty", l -> l.quantity().stripTrailingZeros().toPlainString()),
                column("Unit Price", l -> l.unitPrice().toPlainString()),
                column("Total", l -> l.lineTotalInclVat().toPlainString()));
    }

    private void configurePaymentsTable() {
        paymentsTable.getStyleClass().add("data-table");
        paymentsTable.getColumns().setAll(
                column("Date", p -> String.valueOf(p.paymentDate())),
                column("Method", PaymentRow::paymentMethod),
                column("Amount", p -> p.amount().toPlainString()),
                column("Reference", p -> p.referenceNumber() == null ? "" : p.referenceNumber()));
    }

    private void configureReturnsTable() {
        returnsTable.getStyleClass().add("data-table");
        returnsTable.getColumns().setAll(
                column("Number", CreditNoteResponse::creditNoteNumber),
                column("Status", CreditNoteResponse::status),
                column("Total", cn -> cn.totalAmount().toPlainString()),
                column("Reason", CreditNoteResponse::reason));
    }

    private void load() {
        stateLabel.setText("Loading invoices...");
        UiSupport.onFx(invoiceApiClient.search(blankToNull(numberField.getText()), null,
                blankToNull(statusFilter.getValue()), null, null, null, 0, PAGE_SIZE), result -> {
            table.setItems(FXCollections.observableArrayList(result.data()));
            stateLabel.setText(result.totalElements() == 0 ? "No invoices found." : result.totalElements() + " invoices");
        }, "Invoices could not be loaded.");
    }

    private void selectInvoice(final UUID invoiceId) {
        UiSupport.onFx(invoiceApiClient.get(invoiceId), invoice -> {
            selected = invoice;
            detailLabel.setText(invoice.invoiceNumber() + " - " + invoice.status());
            linesTable.setItems(FXCollections.observableArrayList(invoice.lines()));
            totalsLabel.setText("Subtotal: %s   VAT: %s   Total: %s".formatted(invoice.subtotal().toPlainString(),
                    invoice.vatAmount().toPlainString(), invoice.totalAmount().toPlainString()));
            final boolean posted = "POSTED".equals(invoice.status());
            voidButton.setDisable(!posted || !canVoid);
            recordPaymentButton.setDisable(!posted || !canRecordPayment);
            returnButton.setDisable(!posted || !canReturn);
            receiptButton.setDisable(invoice.postedAt() == null);
            reprintButton.setDisable(invoice.postedAt() == null || !canReprint);
            loadPayments(invoiceId);
            loadReturns(invoiceId);
        }, "Invoice detail could not be loaded.");
    }

    private void loadPayments(final UUID invoiceId) {
        UiSupport.onFx(invoiceApiClient.payments(invoiceId), result -> {
            final var rows = result.data().stream()
                    .flatMap(payment -> payment.allocations().stream()
                            .filter(allocation -> invoiceId.equals(allocation.invoiceId()))
                            .map(allocation -> new PaymentRow(payment.paymentDate(), payment.paymentMethod(),
                                    allocation.amount(), payment.referenceNumber())))
                    .toList();
            paymentsTable.setItems(FXCollections.observableArrayList(rows));
        }, "Payment history could not be loaded.");
    }

    private void loadReturns(final UUID invoiceId) {
        UiSupport.onFx(creditNoteApiClient.search(null, invoiceId, null, null, null),
                result -> returnsTable.setItems(FXCollections.observableArrayList(result.data())),
                "Return history could not be loaded.");
    }

    private void voidSelected() {
        if (selected == null) {
            return;
        }
        final String reason = promptText("Void Invoice", "Reason for voiding " + selected.invoiceNumber() + ":");
        if (reason == null || reason.isBlank()) {
            return;
        }
        UiSupport.onFx(invoiceApiClient.voidInvoice(selected.invoiceId(), new VoidInvoiceRequest(reason, selected.version())),
                invoice -> {
                    UiSupport.alert("Invoice " + invoice.invoiceNumber() + " voided.");
                    selectInvoice(invoice.invoiceId());
                    load();
                }, "Invoice could not be voided.");
    }

    private void recordPaymentForSelected() {
        if (selected == null) {
            return;
        }
        final PaymentEntry entry = promptPayment();
        if (entry == null) {
            return;
        }
        UiSupport.onFx(invoiceApiClient.recordPayment(selected.invoiceId(), UUID.randomUUID(),
                        new RecordInvoicePaymentRequest(null, entry.method(), entry.amount(), entry.reference(), null)),
                payment -> {
                    UiSupport.alert("Payment recorded.");
                    selectInvoice(selected.invoiceId());
                }, "Payment could not be recorded.");
    }

    private void openPdf(final CompletableFuture<byte[]> future) {
        UiSupport.onFx(future, bytes -> {
            try {
                final File file = File.createTempFile("bizco-receipt-", ".pdf");
                file.deleteOnExit();
                Files.write(file.toPath(), bytes);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                } else {
                    UiSupport.alert("Receipt saved to " + file.getAbsolutePath());
                }
            } catch (final IOException exception) {
                UiSupport.alert("Receipt could not be opened: " + exception.getMessage());
            }
        }, "Receipt could not be downloaded.");
    }

    private String promptText(final String title, final String message) {
        final Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final TextArea reason = new TextArea();
        reason.setPrefRowCount(3);
        final VBox content = new VBox(8, new Label(message), reason);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == ButtonType.OK ? reason.getText() : null);
        return dialog.showAndWait().orElse(null);
    }

    private PaymentEntry promptPayment() {
        final Dialog<PaymentEntry> dialog = new Dialog<>();
        dialog.setTitle("Record Payment");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final ComboBox<String> method = new ComboBox<>(FXCollections.observableArrayList("CASH", "CARD", "BANK_TRANSFER", "CHEQUE"));
        method.setValue("CASH");
        final TextField amount = new TextField();
        final TextField reference = new TextField();
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Method"), method);
        grid.addRow(1, new Label("Amount"), amount);
        grid.addRow(2, new Label("Reference"), reference);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            try {
                return new PaymentEntry(method.getValue(), new BigDecimal(amount.getText().trim()), reference.getText());
            } catch (final NumberFormatException | NullPointerException exception) {
                UiSupport.alert("Amount must be a number.");
                return null;
            }
        });
        return dialog.showAndWait().orElse(null);
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
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

    private record PaymentRow(java.time.Instant paymentDate, String paymentMethod, BigDecimal amount, String referenceNumber) {
    }

    private record PaymentEntry(String method, BigDecimal amount, String reference) {
    }
}

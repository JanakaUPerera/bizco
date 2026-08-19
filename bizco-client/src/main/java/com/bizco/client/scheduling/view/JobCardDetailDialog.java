package com.bizco.client.scheduling.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.scheduling.service.JobCardApiClient;
import com.bizco.client.scheduling.service.TechnicianApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobPartRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.AddJobServiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateEstimateRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.EstimateResponseRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.GenerateServiceInvoiceRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardStatusRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobEstimateResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobPartResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.JobServiceStatusRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Job card workflow dialog (DevelopmentPlan.md Week 11 task 11.7): services, estimates, and parts
 * sub-tables plus the job's own status-transition/invoice-generation actions. Every mutation
 * reloads this dialog's own state and notifies {@code onChanged} so the parent list refreshes.
 */
class JobCardDetailDialog {

    private final JobCardApiClient jobCardApiClient;
    private final CatalogApiClient catalogApiClient;
    private final TechnicianApiClient technicianApiClient;
    private final UUID jobCardId;
    private final boolean canUpdate;
    private final boolean canStatusChange;
    private final boolean canComplete;
    private final boolean canEstimateCreate;
    private final boolean canEstimateApprove;
    private final boolean canPartsAdd;
    private final boolean canInvoiceCreate;
    private final Runnable onChanged;

    private final Label headerLabel = new Label();
    private final Label stateLabel = new Label();
    private final TableView<JobServiceResponse> servicesTable = new TableView<>();
    private final TableView<JobPartResponse> partsTable = new TableView<>();
    private final TableView<JobEstimateResponse> estimatesTable = new TableView<>();
    private final Button startWorkButton = Icons.button("Start Work", FontAwesomeSolid.PLAY);
    private final Button readyForPickupButton = Icons.button("Ready for Pickup", FontAwesomeSolid.BOX_OPEN);
    private final Button completeButton = Icons.button("Complete & Pickup", FontAwesomeSolid.FLAG_CHECKERED);
    private final Button cancelButton = Icons.button("Cancel Job", FontAwesomeSolid.BAN);
    private final Button addServiceButton = Icons.button("Add Service", FontAwesomeSolid.PLUS);
    private final Button startServiceButton = Icons.button("Start", FontAwesomeSolid.PLAY);
    private final Button completeServiceButton = Icons.button("Complete", FontAwesomeSolid.CHECK);
    private final Button addPartButton = Icons.button("Add Part", FontAwesomeSolid.PLUS);
    private final Button createEstimateButton = Icons.button("Create Estimate", FontAwesomeSolid.FILE_INVOICE);
    private final Button acceptEstimateButton = Icons.button("Accept", FontAwesomeSolid.CHECK);
    private final Button declineEstimateButton = Icons.button("Decline", FontAwesomeSolid.TIMES);
    private final Button generateInvoiceButton = Icons.button("Generate Invoice", FontAwesomeSolid.FILE_INVOICE_DOLLAR);
    private Stage stage;
    private JobCardResponse job;

    JobCardDetailDialog(final JobCardApiClient jobCardApiClient, final CatalogApiClient catalogApiClient,
                        final TechnicianApiClient technicianApiClient, final UUID jobCardId, final boolean canUpdate,
                        final boolean canStatusChange, final boolean canComplete, final boolean canEstimateCreate,
                        final boolean canEstimateApprove, final boolean canPartsAdd, final boolean canInvoiceCreate,
                        final Runnable onChanged) {
        this.jobCardApiClient = jobCardApiClient;
        this.catalogApiClient = catalogApiClient;
        this.technicianApiClient = technicianApiClient;
        this.jobCardId = jobCardId;
        this.canUpdate = canUpdate;
        this.canStatusChange = canStatusChange;
        this.canComplete = canComplete;
        this.canEstimateCreate = canEstimateCreate;
        this.canEstimateApprove = canEstimateApprove;
        this.canPartsAdd = canPartsAdd;
        this.canInvoiceCreate = canInvoiceCreate;
        this.onChanged = onChanged;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("Job Card");
        stage.initModality(Modality.APPLICATION_MODAL);

        configureServicesTable();
        configurePartsTable();
        configureEstimatesTable();

        startWorkButton.setOnAction(event -> changeStatus("IN_PROGRESS", null));
        readyForPickupButton.setOnAction(event -> changeStatus("READY_FOR_PICKUP", null));
        completeButton.setOnAction(event -> changeStatus("COMPLETED", null));
        cancelButton.setOnAction(event -> {
            final String reason = promptText("Cancel Job", "Reason for cancelling:");
            if (reason != null && !reason.isBlank()) {
                changeStatus("CANCELLED", reason);
            }
        });
        addServiceButton.setOnAction(event -> promptAddService());
        startServiceButton.setOnAction(event -> changeServiceStatus("IN_PROGRESS"));
        completeServiceButton.setOnAction(event -> changeServiceStatus("COMPLETED"));
        addPartButton.setOnAction(event -> promptAddPart());
        createEstimateButton.setOnAction(event -> promptCreateEstimate());
        acceptEstimateButton.setOnAction(event -> respondEstimate(true));
        declineEstimateButton.setOnAction(event -> respondEstimate(false));
        generateInvoiceButton.setOnAction(event -> generateInvoice());

        final HBox jobActions = new HBox(8, startWorkButton, readyForPickupButton, completeButton,
                generateInvoiceButton, cancelButton);
        final HBox serviceActions = new HBox(8, addServiceButton, startServiceButton, completeServiceButton);
        final HBox partActions = new HBox(8, addPartButton);
        final HBox estimateActions = new HBox(8, createEstimateButton, acceptEstimateButton, declineEstimateButton);

        final VBox servicesTab = new VBox(8, servicesTable, serviceActions);
        final VBox partsTab = new VBox(8, partsTable, partActions);
        final VBox estimatesTab = new VBox(8, estimatesTable, estimateActions);
        final TabPane tabs = new TabPane(new Tab("Services", servicesTab), new Tab("Parts", partsTab),
                new Tab("Estimates", estimatesTab));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));

        final VBox content = new VBox(12, headerLabel, stateLabel, jobActions, tabs);
        content.setPadding(new Insets(16));
        content.setPrefWidth(720);
        content.setPrefHeight(560);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        stage.setScene(new Scene(content));
        load();
        stage.show();
    }

    private void configureServicesTable() {
        servicesTable.getStyleClass().add("data-table");
        servicesTable.getColumns().setAll(
                column("Service", JobServiceResponse::serviceName),
                column("Est. Cost", s -> s.estimatedCost() == null ? "" : s.estimatedCost().toPlainString()),
                column("Actual Cost", s -> s.actualCost() == null ? "" : s.actualCost().toPlainString()),
                column("Status", JobServiceResponse::status));
    }

    private void configurePartsTable() {
        partsTable.getStyleClass().add("data-table");
        partsTable.getColumns().setAll(
                column("SKU", JobPartResponse::sku),
                column("Product", JobPartResponse::productName),
                column("Qty", p -> p.quantityUsed().stripTrailingZeros().toPlainString()),
                column("Unit Price", p -> p.unitPriceSnapshot().toPlainString()),
                column("Warranty", p -> p.warrantyCovered() ? "Yes" : "No"));
    }

    private void configureEstimatesTable() {
        estimatesTable.getStyleClass().add("data-table");
        estimatesTable.getColumns().setAll(
                column("Version", e -> String.valueOf(e.estimateVersion())),
                column("Total", e -> e.estimatedTotal().toPlainString()),
                column("Description", e -> e.description() == null ? "" : e.description()),
                column("Response", JobEstimateResponse::customerResponse));
    }

    private void load() {
        UiSupport.onFx(jobCardApiClient.get(jobCardId), response -> {
            job = response;
            headerLabel.setText(job.jobNumber() + " - " + job.customerName() + " - "
                    + (job.deviceType() == null ? "" : job.deviceType() + " " + nullToBlank(job.brand()) + " "
                            + nullToBlank(job.model())));
            stateLabel.setText("Status: " + job.status()
                    + (job.technicianName() == null ? "" : "   Technician: " + job.technicianName()));
            servicesTable.setItems(FXCollections.observableArrayList(job.services()));
            partsTable.setItems(FXCollections.observableArrayList(job.parts()));
            estimatesTable.setItems(FXCollections.observableArrayList(job.estimates()));
            updateActionState();
        }, "Job card could not be loaded.");
    }

    private void updateActionState() {
        final boolean terminal = "COMPLETED".equals(job.status()) || "CANCELLED".equals(job.status());
        startWorkButton.setDisable(terminal || !canStatusChange
                || !("CREATED".equals(job.status()) || "ESTIMATE_APPROVED".equals(job.status())));
        readyForPickupButton.setDisable(terminal || !canStatusChange || !"IN_PROGRESS".equals(job.status()));
        completeButton.setDisable(terminal || !canComplete || !"READY_FOR_PICKUP".equals(job.status()));
        cancelButton.setDisable(terminal || !canStatusChange);
        addServiceButton.setDisable(terminal || !canUpdate);
        startServiceButton.setDisable(terminal || !canUpdate);
        completeServiceButton.setDisable(terminal || !canUpdate);
        addPartButton.setDisable(terminal || !canPartsAdd);
        createEstimateButton.setDisable(terminal || !canEstimateCreate);
        acceptEstimateButton.setDisable(terminal || !canEstimateApprove);
        declineEstimateButton.setDisable(terminal || !canEstimateApprove);
        generateInvoiceButton.setDisable(terminal || !canInvoiceCreate);
    }

    private void changeStatus(final String targetStatus, final String reason) {
        UiSupport.onFx(jobCardApiClient.changeStatus(jobCardId,
                        new JobCardStatusRequest(targetStatus, reason, false, job.version())),
                response -> refreshed(), "Job status could not be changed.");
    }

    private void changeServiceStatus(final String targetStatus) {
        final JobServiceResponse selected = servicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.alert("Select a service first.");
            return;
        }
        final BigDecimal actualCost = "COMPLETED".equals(targetStatus)
                ? (selected.actualCost() != null ? selected.actualCost() : selected.estimatedCost()) : null;
        UiSupport.onFx(jobCardApiClient.changeServiceStatus(jobCardId, selected.jobServiceId(),
                        new JobServiceStatusRequest(targetStatus, actualCost)),
                response -> refreshed(), "Service status could not be changed.");
    }

    private void respondEstimate(final boolean accept) {
        final JobEstimateResponse selected = estimatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.alert("Select an estimate first.");
            return;
        }
        final var future = accept
                ? jobCardApiClient.acceptEstimate(jobCardId, selected.jobEstimateId(), new EstimateResponseRequest(null))
                : jobCardApiClient.declineEstimate(jobCardId, selected.jobEstimateId(), new EstimateResponseRequest(null));
        UiSupport.onFx(future, response -> refreshed(), "Estimate response could not be recorded.");
    }

    private void generateInvoice() {
        UiSupport.onFx(jobCardApiClient.generateInvoice(jobCardId,
                        new GenerateServiceInvoiceRequest(true, true, List.of())),
                invoice -> {
                    UiSupport.alert("Draft service invoice " + invoice.invoiceId() + " created. Post it from Sales History.");
                    refreshed();
                }, "Service invoice could not be generated.");
    }

    private void refreshed() {
        load();
        onChanged.run();
    }

    private void promptAddService() {
        final Dialog<AddJobServiceRequest> dialog = new Dialog<>();
        dialog.setTitle("Add Service");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final ComboBox<ServiceResponse> serviceCombo = new ComboBox<>();
        serviceCombo.setConverter(nameConverter(ServiceResponse::name));
        final TextField estimatedCost = new TextField();
        final TextArea notes = new TextArea();
        notes.setPrefRowCount(2);
        UiSupport.onFx(catalogApiClient.services(null, true, 0, 100),
                result -> serviceCombo.setItems(FXCollections.observableArrayList(result.data())),
                "Services could not be loaded.");
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Service"), serviceCombo);
        grid.addRow(1, new Label("Estimated Cost"), estimatedCost);
        grid.addRow(2, new Label("Notes"), notes);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK || serviceCombo.getValue() == null) {
                return null;
            }
            return new AddJobServiceRequest(serviceCombo.getValue().serviceId(), parseDecimal(estimatedCost.getText()),
                    null, blankToNull(notes.getText()));
        });
        dialog.showAndWait().ifPresent(request -> UiSupport.onFx(jobCardApiClient.addService(jobCardId, request),
                response -> refreshed(), "Service could not be added."));
    }

    private void promptAddPart() {
        final Dialog<AddJobPartRequest> dialog = new Dialog<>();
        dialog.setTitle("Add Part");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final TextField productSearch = new TextField();
        productSearch.setPromptText("Search product SKU / name");
        final ComboBox<ProductSummaryResponse> productCombo = new ComboBox<>();
        productCombo.setConverter(nameConverter(p -> p.sku() + " - " + p.name()));
        final Runnable loadProducts = () -> UiSupport.onFx(
                catalogApiClient.products(blankToNull(productSearch.getText()), null, null, true, 0, 50),
                result -> productCombo.setItems(FXCollections.observableArrayList(result.data())),
                "Products could not be loaded.");
        productSearch.setOnAction(event -> loadProducts.run());
        loadProducts.run();
        final TextField quantity = new TextField("1");
        final TextField customerUnitPrice = new TextField();
        customerUnitPrice.setPromptText("Defaults to selling price");
        final CheckBox warrantyCovered = new CheckBox("Warranty covered (not charged)");
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Product"), new HBox(6, productSearch, productCombo));
        grid.addRow(1, new Label("Quantity"), quantity);
        grid.addRow(2, new Label("Unit Price"), customerUnitPrice);
        grid.addRow(3, new Label(""), warrantyCovered);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK || productCombo.getValue() == null) {
                return null;
            }
            final BigDecimal qty = parseDecimal(quantity.getText());
            if (qty == null || qty.signum() <= 0) {
                UiSupport.alert("Enter a valid quantity.");
                return null;
            }
            return new AddJobPartRequest(productCombo.getValue().productId(), qty,
                    parseDecimal(customerUnitPrice.getText()), warrantyCovered.isSelected());
        });
        dialog.showAndWait().ifPresent(request -> UiSupport.onFx(jobCardApiClient.addPart(jobCardId, request),
                response -> refreshed(), "Part could not be added."));
    }

    private void promptCreateEstimate() {
        final Dialog<CreateEstimateRequest> dialog = new Dialog<>();
        dialog.setTitle("Create Estimate");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final TextField estimatedTotal = new TextField();
        final TextArea description = new TextArea();
        description.setPrefRowCount(2);
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Estimated Total"), estimatedTotal);
        grid.addRow(1, new Label("Description"), description);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            final BigDecimal total = parseDecimal(estimatedTotal.getText());
            if (total == null) {
                UiSupport.alert("Enter a valid estimated total.");
                return null;
            }
            return new CreateEstimateRequest(total, blankToNull(description.getText()), null);
        });
        dialog.showAndWait().ifPresent(request -> UiSupport.onFx(jobCardApiClient.createEstimate(jobCardId, request),
                response -> refreshed(), "Estimate could not be created."));
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

    private <T> StringConverter<T> nameConverter(final Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(final T value) {
                return value == null ? "" : toString.apply(value);
            }

            @Override
            public T fromString(final String string) {
                return null;
            }
        };
    }

    private BigDecimal parseDecimal(final String text) {
        try {
            return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToBlank(final String value) {
        return value == null ? "" : value;
    }

    private <T> TableColumn<T, String> column(final String title, final Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(110);
        return column;
    }
}

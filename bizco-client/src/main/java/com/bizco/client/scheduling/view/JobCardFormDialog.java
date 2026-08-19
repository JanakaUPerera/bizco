package com.bizco.client.scheduling.view;

import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.scheduling.service.JobCardApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.CreateJobCardRequest;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardResponse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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
import java.util.function.Consumer;

/** ApiContracts.md &sect;29.2: walk-in job card creation, no appointment. */
class JobCardFormDialog {

    private final JobCardApiClient jobCardApiClient;
    private final CustomerApiClient customerApiClient;
    private final Consumer<JobCardResponse> onCreated;

    private final TextField customerSearch = new TextField();
    private final ComboBox<CustomerSummaryResponse> customerCombo = new ComboBox<>();
    private final TextField deviceType = new TextField();
    private final TextField brand = new TextField();
    private final TextField model = new TextField();
    private final TextField serialNumber = new TextField();
    private final TextArea reportedIssue = new TextArea();
    private Stage stage;

    JobCardFormDialog(final JobCardApiClient jobCardApiClient, final CustomerApiClient customerApiClient,
                      final Consumer<JobCardResponse> onCreated) {
        this.jobCardApiClient = jobCardApiClient;
        this.customerApiClient = customerApiClient;
        this.onCreated = onCreated;
    }

    void show() {
        stage = new Stage();
        stage.setTitle("New Walk-in Job");
        stage.initModality(Modality.APPLICATION_MODAL);

        customerSearch.setPromptText("Search customer");
        customerSearch.setOnAction(event -> loadCustomers());
        customerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(final CustomerSummaryResponse value) {
                return value == null ? "" : value.name() + " (" + value.customerCode() + ")";
            }

            @Override
            public CustomerSummaryResponse fromString(final String string) {
                return null;
            }
        });
        reportedIssue.setPrefRowCount(2);

        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Customer"), new HBox(6, customerSearch, customerCombo));
        grid.addRow(1, new Label("Device Type"), deviceType);
        grid.addRow(2, new Label("Brand"), brand);
        grid.addRow(3, new Label("Model"), model);
        grid.addRow(4, new Label("Serial Number"), serialNumber);
        grid.addRow(5, new Label("Reported Issue"), reportedIssue);

        final Button submitButton = Icons.button("Create", FontAwesomeSolid.CLIPBOARD_LIST);
        submitButton.setOnAction(event -> submit());
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final HBox actions = new HBox(10, spacer(), cancelButton, submitButton);

        final VBox content = new VBox(12, grid, actions);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);

        stage.setScene(new Scene(content));
        loadCustomers();
        stage.show();
    }

    private void loadCustomers() {
        UiSupport.onFx(customerApiClient.search(blankToNull(customerSearch.getText()), null, "ACTIVE", 0, 50),
                result -> customerCombo.setItems(FXCollections.observableArrayList(result.data())),
                "Customers could not be loaded.");
    }

    private void submit() {
        final CustomerSummaryResponse customer = customerCombo.getValue();
        if (customer == null) {
            UiSupport.alert("Select a customer.");
            return;
        }
        final CreateJobCardRequest request = new CreateJobCardRequest(customer.customerId(), null,
                blankToNull(deviceType.getText()), blankToNull(brand.getText()), blankToNull(model.getText()),
                blankToNull(serialNumber.getText()), blankToNull(reportedIssue.getText()), null, null, null);
        UiSupport.onFx(jobCardApiClient.createWalkIn(request), job -> {
            stage.close();
            onCreated.accept(job);
        }, "Job card could not be created.");
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

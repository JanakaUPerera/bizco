package com.bizco.client.scheduling.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.scheduling.service.JobCardApiClient;
import com.bizco.client.scheduling.service.TechnicianApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.scheduling.JobCardDtos.JobCardSummaryResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Job card list screen (DevelopmentPlan.md Week 11 task 11.7): search/browse job cards and drill
 * into one for its services/estimates/parts/status workflow ({@link JobCardDetailDialog}).
 */
public class JobCardListView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    private final JobCardApiClient jobCardApiClient;
    private final CustomerApiClient customerApiClient;
    private final CatalogApiClient catalogApiClient;
    private final TechnicianApiClient technicianApiClient;
    private final boolean canCreate;
    private final boolean canUpdate;
    private final boolean canStatusChange;
    private final boolean canComplete;
    private final boolean canEstimateCreate;
    private final boolean canEstimateApprove;
    private final boolean canPartsAdd;
    private final boolean canInvoiceCreate;

    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<JobCardSummaryResponse> table = new TableView<>();
    private final Label stateLabel = new Label("Loading job cards...");

    public JobCardListView(final JobCardApiClient jobCardApiClient, final CustomerApiClient customerApiClient,
                           final CatalogApiClient catalogApiClient, final TechnicianApiClient technicianApiClient,
                           final boolean canCreate, final boolean canUpdate, final boolean canStatusChange,
                           final boolean canComplete, final boolean canEstimateCreate,
                           final boolean canEstimateApprove, final boolean canPartsAdd,
                           final boolean canInvoiceCreate) {
        this.jobCardApiClient = jobCardApiClient;
        this.customerApiClient = customerApiClient;
        this.catalogApiClient = catalogApiClient;
        this.technicianApiClient = technicianApiClient;
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canStatusChange = canStatusChange;
        this.canComplete = canComplete;
        this.canEstimateCreate = canEstimateCreate;
        this.canEstimateApprove = canEstimateApprove;
        this.canPartsAdd = canPartsAdd;
        this.canInvoiceCreate = canInvoiceCreate;
    }

    public Parent createView() {
        configureTable();
        statusFilter.getItems().setAll("", "CREATED", "ESTIMATE_PENDING", "ESTIMATE_APPROVED", "IN_PROGRESS",
                "READY_FOR_PICKUP", "COMPLETED", "CANCELLED");
        statusFilter.setValue("");
        statusFilter.setOnAction(event -> load());
        searchField.setPromptText("Search job number / serial");
        searchField.setOnAction(event -> load());
        final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
        searchButton.setOnAction(event -> load());
        final Button refreshButton = Icons.button("Refresh", FontAwesomeSolid.SYNC);
        refreshButton.setOnAction(event -> load());
        final Button newButton = Icons.button("New Walk-in Job", FontAwesomeSolid.CLIPBOARD_LIST);
        newButton.setDisable(!canCreate);
        newButton.setOnAction(event -> new JobCardFormDialog(jobCardApiClient, customerApiClient,
                response -> openDetail(response.jobCardId())).show());

        final HBox header = new HBox(10, UiSupport.label("Job Cards", "screen-title"), spacer(), searchField,
                statusFilter, searchButton, refreshButton, newButton);
        header.getStyleClass().add("screen-header");

        final VBox center = new VBox(8, stateLabel, table);
        center.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(table, Priority.ALWAYS);

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header);
        root.setCenter(center);
        load();
        return root;
    }

    private void configureTable() {
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Number", JobCardSummaryResponse::jobNumber),
                column("Customer", JobCardSummaryResponse::customerName),
                column("Technician", j -> j.technicianName() == null ? "Unassigned" : j.technicianName()),
                column("Device", j -> j.deviceType() == null ? "" : j.deviceType()),
                column("Status", JobCardSummaryResponse::status),
                column("Created", j -> DATE_FORMAT.format(j.createdAt())));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                final JobCardSummaryResponse selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openDetail(selected.jobCardId());
                }
            }
        });
    }

    private void openDetail(final java.util.UUID jobCardId) {
        new JobCardDetailDialog(jobCardApiClient, catalogApiClient, technicianApiClient, jobCardId, canUpdate,
                canStatusChange, canComplete, canEstimateCreate, canEstimateApprove, canPartsAdd, canInvoiceCreate,
                this::load).show();
    }

    private void load() {
        stateLabel.setText("Loading job cards...");
        final String status = statusFilter.getValue();
        UiSupport.onFx(jobCardApiClient.search(blankToNull(searchField.getText()),
                        status == null || status.isBlank() ? null : status, null),
                result -> {
                    table.setItems(FXCollections.observableArrayList(result.data()));
                    stateLabel.setText(result.data().isEmpty() ? "No job cards found." : result.data().size() + " job cards");
                }, "Job cards could not be loaded.");
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <T> TableColumn<T, String> column(final String title, final Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(120);
        return column;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

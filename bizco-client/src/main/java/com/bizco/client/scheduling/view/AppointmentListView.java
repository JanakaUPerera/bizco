package com.bizco.client.scheduling.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.scheduling.service.AppointmentApiClient;
import com.bizco.client.scheduling.service.TechnicianApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Appointment list screen (DevelopmentPlan.md Week 9 task 9.7): create, reschedule, and walk an
 * appointment through its state machine. The daily/weekly/monthly calendar and drag-to-reschedule
 * (tasks 10.1-10.6) are Week 10 scope and are not built here - this view is enough to demo
 * create -&gt; conflict -&gt; reschedule -&gt; cancel end-to-end.
 */
public class AppointmentListView {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    private final AppointmentApiClient appointmentApiClient;
    private final CustomerApiClient customerApiClient;
    private final CatalogApiClient catalogApiClient;
    private final TechnicianApiClient technicianApiClient;
    private final boolean canCreate;
    private final boolean canUpdate;
    private final boolean canCancel;

    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<AppointmentResponse> table = new TableView<>();
    private final Label stateLabel = new Label("Loading appointments...");
    private final Button newButton = Icons.button("New Appointment", FontAwesomeSolid.CALENDAR_PLUS);
    private final Button rescheduleButton = Icons.button("Reschedule", FontAwesomeSolid.CLOCK);
    private final Button confirmButton = Icons.button("Confirm", FontAwesomeSolid.CHECK);
    private final Button startButton = Icons.button("Start", FontAwesomeSolid.PLAY);
    private final Button completeButton = Icons.button("Complete", FontAwesomeSolid.FLAG_CHECKERED);
    private final Button noShowButton = Icons.button("No-Show", FontAwesomeSolid.USER_SLASH);
    private final Button cancelButton = Icons.button("Cancel", FontAwesomeSolid.BAN);

    public AppointmentListView(final AppointmentApiClient appointmentApiClient,
                               final CustomerApiClient customerApiClient, final CatalogApiClient catalogApiClient,
                               final TechnicianApiClient technicianApiClient, final boolean canCreate,
                               final boolean canUpdate, final boolean canCancel) {
        this.appointmentApiClient = appointmentApiClient;
        this.customerApiClient = customerApiClient;
        this.catalogApiClient = catalogApiClient;
        this.technicianApiClient = technicianApiClient;
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canCancel = canCancel;
    }

    public Parent createView() {
        configureTable();
        statusFilter.getItems().setAll("", "SCHEDULED", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "NO_SHOW", "CANCELLED");
        statusFilter.setValue("");
        statusFilter.setOnAction(event -> load());

        newButton.setDisable(!canCreate);
        newButton.setOnAction(event -> new AppointmentFormDialog(appointmentApiClient, customerApiClient,
                catalogApiClient, technicianApiClient, null, response -> load()).show());
        final Button refreshButton = Icons.button("Refresh", FontAwesomeSolid.SYNC);
        refreshButton.setOnAction(event -> load());

        final HBox header = new HBox(10, UiSupport.label("Scheduling", "screen-title"), spacer(), statusFilter,
                refreshButton, newButton);
        header.getStyleClass().add("screen-header");

        configureActions();
        final HBox actions = new HBox(8, rescheduleButton, confirmButton, startButton, completeButton, noShowButton,
                cancelButton);
        actions.setPadding(new Insets(0, 12, 8, 12));

        final VBox center = new VBox(8, stateLabel, table, actions);
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
                column("Number", AppointmentResponse::appointmentNumber),
                column("Customer", AppointmentResponse::customerName),
                column("Service", AppointmentResponse::serviceName),
                column("Technician", a -> a.technicianName() == null ? "Unassigned" : a.technicianName()),
                column("Start", a -> TIME_FORMAT.format(a.startAt())),
                column("Status", AppointmentResponse::status));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, appointment) ->
                updateActionState(appointment));
        updateActionState(null);
    }

    private void configureActions() {
        rescheduleButton.setOnAction(event -> selected().ifPresent(appointment ->
                new AppointmentFormDialog(appointmentApiClient, customerApiClient, catalogApiClient,
                        technicianApiClient, appointment, response -> load()).show()));
        confirmButton.setOnAction(event -> changeStatus("CONFIRMED"));
        startButton.setOnAction(event -> changeStatus("IN_PROGRESS"));
        completeButton.setOnAction(event -> changeStatus("COMPLETED"));
        noShowButton.setOnAction(event -> changeStatus("NO_SHOW"));
        cancelButton.setOnAction(event -> selected().ifPresent(appointment ->
                UiSupport.onFx(appointmentApiClient.cancel(appointment.appointmentId(), "Cancelled from schedule"),
                        response -> load(), "Appointment could not be cancelled.")));
    }

    private void changeStatus(final String targetStatus) {
        selected().ifPresent(appointment -> UiSupport.onFx(
                appointmentApiClient.changeStatus(appointment.appointmentId(),
                        new AppointmentStatusRequest(targetStatus, null, appointment.version())),
                response -> load(), "Appointment status could not be changed."));
    }

    private void updateActionState(final AppointmentResponse appointment) {
        final boolean terminal = appointment != null && ("COMPLETED".equals(appointment.status())
                || "NO_SHOW".equals(appointment.status()) || "CANCELLED".equals(appointment.status()));
        final boolean has = appointment != null;
        rescheduleButton.setDisable(!has || !canUpdate || terminal);
        confirmButton.setDisable(!has || !canUpdate || !"SCHEDULED".equals(appointment == null ? "" : appointment.status()));
        startButton.setDisable(!has || !canUpdate || terminal);
        completeButton.setDisable(!has || !canUpdate || appointment == null
                || !"IN_PROGRESS".equals(appointment.status()));
        noShowButton.setDisable(!has || !canUpdate || terminal);
        cancelButton.setDisable(!has || !canCancel || terminal);
    }

    private java.util.Optional<AppointmentResponse> selected() {
        return java.util.Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void load() {
        stateLabel.setText("Loading appointments...");
        final String status = statusFilter.getValue();
        UiSupport.onFx(appointmentApiClient.search(status == null || status.isBlank() ? null : status), result -> {
            table.setItems(FXCollections.observableArrayList(result.data()));
            stateLabel.setText(result.data().isEmpty() ? "No appointments found." : result.data().size() + " appointments");
            updateActionState(null);
        }, "Appointments could not be loaded.");
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

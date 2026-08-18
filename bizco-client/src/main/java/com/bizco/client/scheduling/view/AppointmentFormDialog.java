package com.bizco.client.scheduling.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.scheduling.service.AppointmentApiClient;
import com.bizco.client.scheduling.service.TechnicianApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ServiceResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.CreateAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.TechnicianResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
 * Create/reschedule appointment dialog (DevelopmentPlan.md Week 9 task 9.7). A conflicting slot
 * is a normal, expected outcome here (DomainModel.md &sect;13.6 - client checks are advisory
 * only) so it surfaces as the server's plain error message via {@link UiSupport#onFx}, not a
 * crash; the {@code ex_appointments_technician_overlap} constraint is what actually decides it.
 */
class AppointmentFormDialog {

    /** DatabaseDesign.md Section 39 / "Today's Appointments": business-local day boundary, matching the server. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final AppointmentApiClient appointmentApiClient;
    private final CustomerApiClient customerApiClient;
    private final CatalogApiClient catalogApiClient;
    private final TechnicianApiClient technicianApiClient;
    private final AppointmentResponse existing;
    private final Consumer<AppointmentResponse> onSaved;

    private final TextField customerSearch = new TextField();
    private final ComboBox<CustomerSummaryResponse> customerCombo = new ComboBox<>();
    private final ComboBox<ServiceResponse> serviceCombo = new ComboBox<>();
    private final ComboBox<TechnicianResponse> technicianCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TextField timeField = new TextField();
    private final TextArea notesField = new TextArea();
    private final CheckBox walkInBox = new CheckBox("Walk-in");
    private Stage stage;

    /** {@code existing == null} creates a new appointment; otherwise this reschedules it. */
    AppointmentFormDialog(final AppointmentApiClient appointmentApiClient, final CustomerApiClient customerApiClient,
                          final CatalogApiClient catalogApiClient, final TechnicianApiClient technicianApiClient,
                          final AppointmentResponse existing, final Consumer<AppointmentResponse> onSaved) {
        this.appointmentApiClient = appointmentApiClient;
        this.customerApiClient = customerApiClient;
        this.catalogApiClient = catalogApiClient;
        this.technicianApiClient = technicianApiClient;
        this.existing = existing;
        this.onSaved = onSaved;
    }

    void show() {
        stage = new Stage();
        stage.setTitle(existing == null ? "New Appointment" : "Reschedule " + existing.appointmentNumber());
        stage.initModality(Modality.APPLICATION_MODAL);

        customerSearch.setPromptText("Search customer");
        customerSearch.setOnAction(event -> loadCustomers());
        customerCombo.setConverter(converter(c -> c.name() + " (" + c.customerCode() + ")"));
        serviceCombo.setConverter(converter(ServiceResponse::name));
        technicianCombo.setConverter(converter(t -> t == null ? "Unassigned" : t.fullName()));
        timeField.setPromptText("HH:mm");
        notesField.setPrefRowCount(2);

        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Customer"), new HBox(6, customerSearch, customerCombo));
        grid.addRow(1, new Label("Service"), serviceCombo);
        grid.addRow(2, new Label("Technician"), technicianCombo);
        grid.addRow(3, new Label("Date"), datePicker);
        grid.addRow(4, new Label("Time"), timeField);
        grid.addRow(5, new Label("Notes"), notesField);
        grid.addRow(6, new Label(""), walkInBox);

        final Button submitButton = Icons.button(existing == null ? "Create" : "Reschedule", FontAwesomeSolid.CALENDAR_CHECK);
        submitButton.setOnAction(event -> submit());
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());
        final HBox actions = new HBox(10, spacer(), cancelButton, submitButton);

        final VBox content = new VBox(12, grid, actions);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);

        stage.setScene(new Scene(content));
        prefill();
        loadCustomers();
        loadServices();
        loadTechnicians();
        stage.show();
    }

    private void prefill() {
        if (existing == null) {
            return;
        }
        final var startAt = existing.startAt().atZone(BUSINESS_ZONE);
        datePicker.setValue(startAt.toLocalDate());
        timeField.setText(startAt.toLocalTime().withSecond(0).withNano(0).toString());
        notesField.setText(existing.notes());
        walkInBox.setSelected(existing.walkIn());
        walkInBox.setDisable(true);
    }

    private void loadCustomers() {
        UiSupport.onFx(customerApiClient.search(blankToNull(customerSearch.getText()), null, "ACTIVE", 0, 50),
                result -> {
                    customerCombo.setItems(FXCollections.observableArrayList(result.data()));
                    if (existing != null) {
                        result.data().stream().filter(c -> c.customerId().equals(existing.customerId())).findFirst()
                                .ifPresent(customerCombo::setValue);
                    }
                }, "Customers could not be loaded.");
    }

    private void loadServices() {
        UiSupport.onFx(catalogApiClient.services(null, true, 0, 100), result -> {
            serviceCombo.setItems(FXCollections.observableArrayList(result.data()));
            if (existing != null) {
                result.data().stream().filter(s -> s.serviceId().equals(existing.serviceId())).findFirst()
                        .ifPresent(serviceCombo::setValue);
            } else if (!result.data().isEmpty()) {
                serviceCombo.setValue(result.data().get(0));
            }
        }, "Services could not be loaded.");
    }

    private void loadTechnicians() {
        UiSupport.onFx(technicianApiClient.listActive(), result -> {
            final var items = FXCollections.<TechnicianResponse>observableArrayList();
            items.add(null);
            items.addAll(result.data());
            technicianCombo.setItems(items);
            if (existing != null && existing.technicianId() != null) {
                result.data().stream().filter(t -> t.userId().equals(existing.technicianId())).findFirst()
                        .ifPresent(technicianCombo::setValue);
            }
        }, "Technicians could not be loaded.");
    }

    private void submit() {
        final CustomerSummaryResponse customer = customerCombo.getValue();
        final ServiceResponse service = serviceCombo.getValue();
        if (existing == null && customer == null) {
            UiSupport.alert("Select a customer.");
            return;
        }
        if (service == null) {
            UiSupport.alert("Select a service.");
            return;
        }
        final LocalDate date = datePicker.getValue();
        final LocalTime time = parseTime(timeField.getText());
        if (date == null || time == null) {
            UiSupport.alert("Enter a valid date and time (HH:mm).");
            return;
        }
        final var startAt = date.atTime(time).atZone(BUSINESS_ZONE).toInstant();
        final UUID technicianId = technicianCombo.getValue() == null ? null : technicianCombo.getValue().userId();

        if (existing == null) {
            final var request = new CreateAppointmentRequest(customer.customerId(), service.serviceId(), technicianId,
                    startAt, blankToNull(notesField.getText()), walkInBox.isSelected());
            UiSupport.onFx(appointmentApiClient.create(request), this::saved, "Appointment could not be created.");
        } else {
            final var request = new RescheduleAppointmentRequest(service.serviceId(), technicianId, startAt,
                    blankToNull(notesField.getText()), existing.version());
            UiSupport.onFx(appointmentApiClient.reschedule(existing.appointmentId(), request), this::saved,
                    "Appointment could not be rescheduled.");
        }
    }

    private void saved(final AppointmentResponse response) {
        stage.close();
        onSaved.accept(response);
    }

    private LocalTime parseTime(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim());
        } catch (final DateTimeParseException exception) {
            return null;
        }
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> StringConverter<T> converter(final java.util.function.Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(final T value) {
                return toString.apply(value);
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
}

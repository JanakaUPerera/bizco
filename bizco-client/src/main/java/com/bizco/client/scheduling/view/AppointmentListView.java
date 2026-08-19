package com.bizco.client.scheduling.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.scheduling.service.AppointmentApiClient;
import com.bizco.client.scheduling.service.TechnicianApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.AppointmentStatusRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.RescheduleAppointmentRequest;
import com.bizco.common.dto.scheduling.AppointmentDtos.TechnicianResponse;
import com.bizco.common.dto.scheduling.JobCardDtos.ConvertAppointmentRequest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Appointment calendar screen (DevelopmentPlan.md Week 9 task 9.7, Week 10 tasks 10.1-10.9): a
 * day-count grid (week row or month grid, MVP.md &sect;16.4) for browsing, plus a schedule table
 * for the selected day that create/reschedule/state-transition appointments. Drag a table row onto
 * a day cell to reschedule its date (UI-SCH-002); the drop always reloads server state afterward,
 * whether it succeeded or was rejected by the {@code ex_appointments_technician_overlap} exclusion
 * constraint, so the view never shows a stale or false-success state.
 */
public class AppointmentListView {

    private enum ViewMode { DAY, WEEK, MONTH }

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(BUSINESS_ZONE);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter SHORT_LABEL = DateTimeFormatter.ofPattern("d MMM");

    private final AppointmentApiClient appointmentApiClient;
    private final CustomerApiClient customerApiClient;
    private final CatalogApiClient catalogApiClient;
    private final TechnicianApiClient technicianApiClient;
    private final boolean canCreate;
    private final boolean canUpdate;
    private final boolean canCancel;
    private final boolean canConvertToJob;

    private ViewMode viewMode = ViewMode.WEEK;
    private LocalDate selectedDate = LocalDate.now();
    private List<AppointmentResponse> loaded = List.of();

    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<TechnicianResponse> technicianFilter = new ComboBox<>();
    private final ToggleGroup viewModeGroup = new ToggleGroup();
    private final ToggleButton dayModeButton = new ToggleButton("Day");
    private final ToggleButton weekModeButton = new ToggleButton("Week");
    private final ToggleButton monthModeButton = new ToggleButton("Month");
    private final Label dateRangeLabel = new Label();
    private final GridPane calendarGrid = new GridPane();
    private final TableView<AppointmentResponse> table = new TableView<>();
    private final Label stateLabel = new Label("Loading appointments...");
    private final Button newButton = Icons.button("New Appointment", FontAwesomeSolid.CALENDAR_PLUS);
    private final Button walkInButton = Icons.button("Walk-in", FontAwesomeSolid.WALKING);
    private final Button rescheduleButton = Icons.button("Reschedule", FontAwesomeSolid.CLOCK);
    private final Button convertToJobButton = Icons.button("Convert to Job", FontAwesomeSolid.TOOLS);
    private final Button confirmButton = Icons.button("Confirm", FontAwesomeSolid.CHECK);
    private final Button startButton = Icons.button("Start", FontAwesomeSolid.PLAY);
    private final Button completeButton = Icons.button("Complete", FontAwesomeSolid.FLAG_CHECKERED);
    private final Button noShowButton = Icons.button("No-Show", FontAwesomeSolid.USER_SLASH);
    private final Button cancelButton = Icons.button("Cancel", FontAwesomeSolid.BAN);

    public AppointmentListView(final AppointmentApiClient appointmentApiClient,
                               final CustomerApiClient customerApiClient, final CatalogApiClient catalogApiClient,
                               final TechnicianApiClient technicianApiClient, final boolean canCreate,
                               final boolean canUpdate, final boolean canCancel, final boolean canConvertToJob) {
        this.appointmentApiClient = appointmentApiClient;
        this.customerApiClient = customerApiClient;
        this.catalogApiClient = catalogApiClient;
        this.technicianApiClient = technicianApiClient;
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canCancel = canCancel;
        this.canConvertToJob = canConvertToJob;
    }

    public Parent createView() {
        configureTable();
        configureCalendarGrid();

        statusFilter.getItems().setAll("", "SCHEDULED", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "NO_SHOW", "CANCELLED");
        statusFilter.setValue("");
        statusFilter.setOnAction(event -> load());
        technicianFilter.setConverter(converter(t -> t == null ? "All Technicians" : t.fullName()));
        technicianFilter.setOnAction(event -> load());
        loadTechnicianFilter();

        newButton.setDisable(!canCreate);
        newButton.setOnAction(event -> new AppointmentFormDialog(appointmentApiClient, customerApiClient,
                catalogApiClient, technicianApiClient, null, selectedDate, false, response -> load()).show());
        walkInButton.setDisable(!canCreate);
        walkInButton.setOnAction(event -> new AppointmentFormDialog(appointmentApiClient, customerApiClient,
                catalogApiClient, technicianApiClient, null, selectedDate, true, response -> load()).show());
        final Button refreshButton = Icons.button("Refresh", FontAwesomeSolid.SYNC);
        refreshButton.setOnAction(event -> load());

        final HBox header = new HBox(10, UiSupport.label("Scheduling", "screen-title"), spacer(), statusFilter,
                technicianFilter, refreshButton, newButton, walkInButton);
        header.getStyleClass().add("screen-header");

        final HBox toolbar = calendarToolbar();

        configureActions();
        final HBox actions = new HBox(8, rescheduleButton, convertToJobButton, confirmButton, startButton,
                completeButton, noShowButton, cancelButton);
        actions.setPadding(new Insets(0, 12, 8, 12));

        final VBox center = new VBox(8, toolbar, calendarGrid, stateLabel, table, actions);
        center.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(table, Priority.ALWAYS);

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header);
        root.setCenter(center);
        load();
        return root;
    }

    private HBox calendarToolbar() {
        dayModeButton.setToggleGroup(viewModeGroup);
        weekModeButton.setToggleGroup(viewModeGroup);
        monthModeButton.setToggleGroup(viewModeGroup);
        weekModeButton.setSelected(true);
        viewModeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                viewModeGroup.selectToggle(oldToggle);
                return;
            }
            viewMode = newToggle == dayModeButton ? ViewMode.DAY : newToggle == weekModeButton ? ViewMode.WEEK
                    : ViewMode.MONTH;
            load();
        });

        final Button prevButton = Icons.button("", FontAwesomeSolid.CHEVRON_LEFT);
        prevButton.setOnAction(event -> {
            selectedDate = shift(selectedDate, -1);
            load();
        });
        final Button todayButton = new Button("Today");
        todayButton.setOnAction(event -> {
            selectedDate = LocalDate.now();
            load();
        });
        final Button nextButton = Icons.button("", FontAwesomeSolid.CHEVRON_RIGHT);
        nextButton.setOnAction(event -> {
            selectedDate = shift(selectedDate, 1);
            load();
        });

        final HBox toolbar = new HBox(8, dayModeButton, weekModeButton, monthModeButton, spacerFixed(16), prevButton,
                todayButton, nextButton, dateRangeLabel);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return toolbar;
    }

    private LocalDate shift(final LocalDate date, final int direction) {
        return switch (viewMode) {
            case DAY -> date.plusDays(direction);
            case WEEK -> date.plusWeeks(direction);
            case MONTH -> date.plusMonths(direction);
        };
    }

    private void configureCalendarGrid() {
        calendarGrid.setHgap(4);
        calendarGrid.setVgap(4);
        for (int i = 0; i < 7; i++) {
            final ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 7);
            calendarGrid.getColumnConstraints().add(constraints);
        }
    }

    private void renderCalendar() {
        calendarGrid.getChildren().clear();
        if (viewMode == ViewMode.DAY) {
            calendarGrid.setVisible(false);
            calendarGrid.setManaged(false);
            return;
        }
        calendarGrid.setVisible(true);
        calendarGrid.setManaged(true);
        final List<LocalDate> days = gridDays();
        int column = 0;
        int row = 0;
        for (final LocalDate day : days) {
            calendarGrid.add(buildDayCell(day), column, row);
            column++;
            if (column == 7) {
                column = 0;
                row++;
            }
        }
    }

    private List<LocalDate> gridDays() {
        if (viewMode == ViewMode.WEEK) {
            final LocalDate monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            final List<LocalDate> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                days.add(monday.plusDays(i));
            }
            return days;
        }
        final LocalDate firstOfMonth = selectedDate.withDayOfMonth(1);
        final LocalDate gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        final LocalDate lastOfMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth());
        final LocalDate gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        final List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = gridStart; !day.isAfter(gridEnd); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    private Node buildDayCell(final LocalDate day) {
        final List<AppointmentResponse> dayAppointments = loaded.stream()
                .filter(a -> localDate(a.startAt()).equals(day)).toList();

        final Label dayNumber = new Label(String.valueOf(day.getDayOfMonth()));
        final VBox cell = new VBox(4, dayNumber);
        cell.getStyleClass().add("calendar-day-cell");
        if (day.equals(selectedDate)) {
            cell.getStyleClass().add("calendar-day-cell-selected");
        }
        if (viewMode == ViewMode.MONTH && day.getMonth() != selectedDate.getMonth()) {
            cell.getStyleClass().add("calendar-day-cell-outside");
        }
        if (!dayAppointments.isEmpty()) {
            final Label countBadge = new Label(dayAppointments.size() + (dayAppointments.size() == 1 ? " apt" : " apts"));
            countBadge.getStyleClass().addAll("appt-status-badge",
                    "appt-status-" + dayAppointments.get(0).status().toLowerCase());
            cell.getChildren().add(countBadge);
        }
        cell.setOnMouseClicked(event -> {
            selectedDate = day;
            load();
        });
        cell.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        cell.setOnDragDropped(event -> {
            final boolean accepted = event.getDragboard().hasString();
            if (accepted) {
                final UUID appointmentId = UUID.fromString(event.getDragboard().getString());
                loaded.stream().filter(a -> a.appointmentId().equals(appointmentId)).findFirst()
                        .ifPresent(appointment -> rescheduleToDay(appointment, day));
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
        return cell;
    }

    private void rescheduleToDay(final AppointmentResponse appointment, final LocalDate newDay) {
        if (newDay.equals(localDate(appointment.startAt())) || !canUpdate || isTerminal(appointment.status())) {
            return;
        }
        final ZonedDateTime oldStart = appointment.startAt().atZone(BUSINESS_ZONE);
        final Instant newStart = newDay.atTime(oldStart.toLocalTime()).atZone(BUSINESS_ZONE).toInstant();
        final RescheduleAppointmentRequest request = new RescheduleAppointmentRequest(appointment.serviceId(),
                appointment.technicianId(), newStart, appointment.notes(), appointment.version());
        final var future = appointmentApiClient.reschedule(appointment.appointmentId(), request);
        // UI-SCH-002: reload server state whether the drag succeeded or was rejected by the
        // exclusion constraint, so the calendar never shows a stale or false-success result.
        future.whenComplete((response, throwable) -> Platform.runLater(this::load));
        UiSupport.onFx(future, response -> { }, "Appointment could not be moved to that day.");
    }

    private void configureTable() {
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Number", AppointmentResponse::appointmentNumber),
                column("Customer", AppointmentResponse::customerName),
                column("Service", AppointmentResponse::serviceName),
                column("Technician", a -> a.technicianName() == null ? "Unassigned" : a.technicianName()),
                column("Time", a -> TIME_FORMAT.format(a.startAt())),
                column("Status", AppointmentResponse::status));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, appointment) ->
                updateActionState(appointment));
        table.setRowFactory(view -> {
            final TableRow<AppointmentResponse> row = new TableRow<>();
            row.setOnDragDetected(event -> {
                if (row.isEmpty() || !canUpdate || isTerminal(row.getItem().status())) {
                    return;
                }
                final var dragboard = row.startDragAndDrop(TransferMode.MOVE);
                final ClipboardContent content = new ClipboardContent();
                content.putString(row.getItem().appointmentId().toString());
                dragboard.setContent(content);
                event.consume();
            });
            return row;
        });
        updateActionState(null);
    }

    private void configureActions() {
        rescheduleButton.setOnAction(event -> selected().ifPresent(appointment ->
                new AppointmentFormDialog(appointmentApiClient, customerApiClient, catalogApiClient,
                        technicianApiClient, appointment, response -> load()).show()));
        convertToJobButton.setOnAction(event -> selected().ifPresent(this::convertToJob));
        confirmButton.setOnAction(event -> changeStatus("CONFIRMED"));
        startButton.setOnAction(event -> changeStatus("IN_PROGRESS"));
        completeButton.setOnAction(event -> changeStatus("COMPLETED"));
        noShowButton.setOnAction(event -> changeStatus("NO_SHOW"));
        cancelButton.setOnAction(event -> selected().ifPresent(appointment ->
                UiSupport.onFx(appointmentApiClient.cancel(appointment.appointmentId(), "Cancelled from schedule"),
                        response -> load(), "Appointment could not be cancelled.")));
    }

    /** ApiContracts.md &sect;27.7: quick device-intake prompt, then hands off to the new job card. */
    private void convertToJob(final AppointmentResponse appointment) {
        final javafx.scene.control.Dialog<ConvertAppointmentRequest> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Convert " + appointment.appointmentNumber() + " to Job");
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        final javafx.scene.control.TextField deviceType = new javafx.scene.control.TextField();
        final javafx.scene.control.TextField brand = new javafx.scene.control.TextField();
        final javafx.scene.control.TextField model = new javafx.scene.control.TextField();
        final javafx.scene.control.TextField serialNumber = new javafx.scene.control.TextField();
        final javafx.scene.control.TextArea reportedIssue = new javafx.scene.control.TextArea();
        reportedIssue.setPrefRowCount(2);
        final javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Device Type"), deviceType);
        grid.addRow(1, new Label("Brand"), brand);
        grid.addRow(2, new Label("Model"), model);
        grid.addRow(3, new Label("Serial Number"), serialNumber);
        grid.addRow(4, new Label("Reported Issue"), reportedIssue);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button != javafx.scene.control.ButtonType.OK ? null
                : new ConvertAppointmentRequest(blankToNull(deviceType.getText()), blankToNull(brand.getText()),
                        blankToNull(model.getText()), blankToNull(serialNumber.getText()),
                        blankToNull(reportedIssue.getText()), null, null));
        dialog.showAndWait().ifPresent(request -> UiSupport.onFx(
                appointmentApiClient.convertToJob(appointment.appointmentId(), request),
                job -> UiSupport.alert("Job card " + job.jobNumber() + " created."),
                "Appointment could not be converted to a job."));
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void changeStatus(final String targetStatus) {
        selected().ifPresent(appointment -> UiSupport.onFx(
                appointmentApiClient.changeStatus(appointment.appointmentId(),
                        new AppointmentStatusRequest(targetStatus, null, appointment.version())),
                response -> load(), "Appointment status could not be changed."));
    }

    private void updateActionState(final AppointmentResponse appointment) {
        final boolean terminal = appointment != null && isTerminal(appointment.status());
        final boolean has = appointment != null;
        rescheduleButton.setDisable(!has || !canUpdate || terminal);
        convertToJobButton.setDisable(!has || !canConvertToJob || terminal);
        confirmButton.setDisable(!has || !canUpdate || !"SCHEDULED".equals(appointment == null ? "" : appointment.status()));
        startButton.setDisable(!has || !canUpdate || terminal);
        completeButton.setDisable(!has || !canUpdate || appointment == null
                || !"IN_PROGRESS".equals(appointment.status()));
        noShowButton.setDisable(!has || !canUpdate || terminal);
        cancelButton.setDisable(!has || !canCancel || terminal);
    }

    private boolean isTerminal(final String status) {
        return "COMPLETED".equals(status) || "NO_SHOW".equals(status) || "CANCELLED".equals(status);
    }

    private Optional<AppointmentResponse> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void loadTechnicianFilter() {
        UiSupport.onFx(technicianApiClient.listActive(), result -> {
            final var items = FXCollections.<TechnicianResponse>observableArrayList();
            items.add(null);
            items.addAll(result.data());
            technicianFilter.setItems(items);
            technicianFilter.setValue(null);
        }, "Technicians could not be loaded.");
    }

    private void load() {
        stateLabel.setText("Loading appointments...");
        updateDateRangeLabel();
        final List<LocalDate> range = viewMode == ViewMode.DAY ? List.of(selectedDate, selectedDate) : gridDays();
        final LocalDate rangeStart = range.get(0);
        final LocalDate rangeEnd = range.get(range.size() - 1).plusDays(1);
        final Instant from = rangeStart.atStartOfDay(BUSINESS_ZONE).toInstant();
        final Instant to = rangeEnd.atStartOfDay(BUSINESS_ZONE).toInstant();
        final UUID technicianId = technicianFilter.getValue() == null ? null : technicianFilter.getValue().userId();
        final String status = statusFilter.getValue();
        UiSupport.onFx(appointmentApiClient.search(from, to, technicianId, status == null || status.isBlank() ? null : status),
                result -> {
                    loaded = result.data();
                    renderCalendar();
                    final List<AppointmentResponse> dayRows = loaded.stream()
                            .filter(a -> localDate(a.startAt()).equals(selectedDate)).toList();
                    table.setItems(FXCollections.observableArrayList(dayRows));
                    stateLabel.setText(dayRows.isEmpty() ? "No appointments for " + DAY_LABEL.format(selectedDate.atStartOfDay())
                            + "." : dayRows.size() + " appointments for " + DAY_LABEL.format(selectedDate.atStartOfDay()));
                    updateActionState(null);
                }, "Appointments could not be loaded.");
    }

    private void updateDateRangeLabel() {
        switch (viewMode) {
            case DAY -> dateRangeLabel.setText(DAY_LABEL.format(selectedDate.atStartOfDay()));
            case WEEK -> {
                final List<LocalDate> days = gridDays();
                dateRangeLabel.setText(SHORT_LABEL.format(days.get(0).atStartOfDay()) + " - "
                        + SHORT_LABEL.format(days.get(days.size() - 1).atStartOfDay()) + " " + selectedDate.getYear());
            }
            case MONTH -> dateRangeLabel.setText(MONTH_LABEL.format(selectedDate.atStartOfDay()));
        }
    }

    private LocalDate localDate(final Instant instant) {
        return instant.atZone(BUSINESS_ZONE).toLocalDate();
    }

    private <T> TableColumn<T, String> column(final String title, final Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(120);
        return column;
    }

    private <T> javafx.util.StringConverter<T> converter(final Function<T, String> toString) {
        return new javafx.util.StringConverter<>() {
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

    private HBox spacerFixed(final double width) {
        final HBox spacer = new HBox();
        spacer.setMinWidth(width);
        return spacer;
    }
}

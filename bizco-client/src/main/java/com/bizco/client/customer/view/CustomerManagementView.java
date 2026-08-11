package com.bizco.client.customer.view;

import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerUpdateRequest;
import java.math.BigDecimal;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CustomerManagementView {

    private static final int PAGE_SIZE = 20;

    private final CustomerApiClient apiClient;
    private final boolean canCreate;
    private final boolean canUpdate;
    private final boolean canAnonymize;
    private final boolean canReadCredit;
    private final TableView<CustomerSummaryResponse> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Pagination pagination = new Pagination(1, 0);
    private final Label stateLabel = new Label("Loading customers...");
    private final TextField nameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField address1Field = new TextField();
    private final TextField address2Field = new TextField();
    private final TextField cityField = new TextField();
    private final TextField nicField = new TextField();
    private final TextField brField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField creditLimitField = new TextField("0.00");
    private final CheckBox marketingBox = new CheckBox("Marketing");
    private final CheckBox sharingBox = new CheckBox("Data sharing");
    private final Label detailLabel = new Label("Select a customer");
    private final Label creditLabel = new Label("Credit summary unavailable");
    private final Button newButton = new Button("New");
    private final Button saveButton = new Button("Save");
    private final Button blockButton = new Button("Block");
    private final Button activateButton = new Button("Activate");
    private final Button anonymizeButton = new Button("Anonymize");
    private CustomerDetailResponse selected;

    public CustomerManagementView(final CustomerApiClient apiClient, final boolean canCreate,
                                  final boolean canUpdate, final boolean canAnonymize,
                                  final boolean canReadCredit) {
        this.apiClient = apiClient;
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canAnonymize = canAnonymize;
        this.canReadCredit = canReadCredit;
    }

    public Parent createView() {
        configureTable();
        categoryFilter.getItems().setAll("", "RETAIL", "WHOLESALE", "CORPORATE");
        statusFilter.getItems().setAll("", "ACTIVE", "BLOCKED");
        categoryBox.getItems().setAll("RETAIL", "WHOLESALE", "CORPORATE");
        categoryBox.setValue("RETAIL");
        searchField.setPromptText("Search code, name, phone");
        newButton.setDisable(!canCreate);
        saveButton.setDisable(true);
        blockButton.setDisable(true);
        activateButton.setDisable(true);
        anonymizeButton.setDisable(true);
        newButton.setOnAction(event -> clearForm());
        saveButton.setOnAction(event -> save());
        blockButton.setOnAction(event -> action("Customer could not be blocked.", () -> apiClient.block(selected.customerId())));
        activateButton.setOnAction(event -> action("Customer could not be activated.", () -> apiClient.activate(selected.customerId())));
        anonymizeButton.setOnAction(event -> action("Customer could not be anonymized.", () -> apiClient.anonymize(selected.customerId())));
        pagination.currentPageIndexProperty().addListener((observable, oldValue, newValue) -> load(newValue.intValue()));
        searchField.setOnAction(event -> load(0));
        categoryFilter.setOnAction(event -> load(0));
        statusFilter.setOnAction(event -> load(0));

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header());
        root.setCenter(center());
        root.setRight(sidePanel());
        load(0);
        return root;
    }

    private HBox header() {
        final Button searchButton = new Button("Search");
        searchButton.setOnAction(event -> load(0));
        final HBox header = new HBox(10, UiSupport.label("Customers", "screen-title"), spacer(),
                searchField, categoryFilter, statusFilter, searchButton, newButton);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private VBox center() {
        final VBox center = new VBox(8, stateLabel, table, pagination);
        center.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(table, Priority.ALWAYS);
        return center;
    }

    private VBox sidePanel() {
        final GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.setHgap(10);
        form.setVgap(8);
        addRow(form, 0, "Name", nameField);
        addRow(form, 1, "Phone", phoneField);
        addRow(form, 2, "Email", emailField);
        addRow(form, 3, "Address 1", address1Field);
        addRow(form, 4, "Address 2", address2Field);
        addRow(form, 5, "City", cityField);
        addRow(form, 6, "NIC", nicField);
        addRow(form, 7, "BR", brField);
        addRow(form, 8, "Category", categoryBox);
        addRow(form, 9, "Credit Limit", creditLimitField);
        final HBox consent = new HBox(10, marketingBox, sharingBox);
        form.add(new Label("Consent"), 0, 10);
        form.add(consent, 1, 10);

        final HBox actions = new HBox(8, saveButton, blockButton, activateButton, anonymizeButton);
        final TabPane tabs = new TabPane(new Tab("Profile", form), new Tab("Credit", creditLabel));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        final VBox panel = new VBox(12, UiSupport.label("Customer Detail", "panel-title"), detailLabel, tabs, actions);
        panel.getStyleClass().add("side-panel");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(420);
        return panel;
    }

    private void configureTable() {
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Code", CustomerSummaryResponse::customerCode),
                column("Name", CustomerSummaryResponse::name),
                column("Phone", CustomerSummaryResponse::phone),
                column("Category", CustomerSummaryResponse::category),
                column("Status", CustomerSummaryResponse::status),
                column("Limit", customer -> customer.creditLimit().toPlainString()));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, customer) -> {
            if (customer != null) {
                UiSupport.onFx(apiClient.get(customer.customerId()), this::select,
                        "Customer detail could not be loaded.");
            }
        });
    }

    private void load(final int page) {
        stateLabel.setText("Loading customers...");
        UiSupport.onFx(apiClient.search(searchField.getText(), categoryFilter.getValue(), statusFilter.getValue(),
                page, PAGE_SIZE), result -> {
            table.setItems(FXCollections.observableArrayList(result.data()));
            pagination.setPageCount(Math.max(result.totalPages(), 1));
            pagination.setCurrentPageIndex(result.page());
            stateLabel.setText(result.totalElements() == 0 ? "No customers found." :
                    result.totalElements() + " customers");
        }, "Customers could not be loaded.");
    }

    private void select(final CustomerDetailResponse customer) {
        selected = customer;
        detailLabel.setText(customer.customerCode() + " - " + customer.status()
                + (customer.piiRevealed() ? " - PII visible" : " - PII masked"));
        nameField.setText(customer.name());
        phoneField.setText(customer.phone());
        emailField.setText(nullToBlank(customer.email()));
        address1Field.setText(nullToBlank(customer.addressLine1()));
        address2Field.setText(nullToBlank(customer.addressLine2()));
        cityField.setText(nullToBlank(customer.city()));
        nicField.setText(nullToBlank(customer.nicNumber()));
        brField.setText(nullToBlank(customer.brNumber()));
        categoryBox.setValue(customer.category());
        creditLimitField.setText(customer.creditLimit().toPlainString());
        marketingBox.setSelected(customer.consentMarketing());
        sharingBox.setSelected(customer.consentDataSharing());
        saveButton.setDisable(!canUpdate);
        blockButton.setDisable(!canUpdate || "BLOCKED".equals(customer.status()) || customer.anonymized());
        activateButton.setDisable(!canUpdate || "ACTIVE".equals(customer.status()) || customer.anonymized());
        anonymizeButton.setDisable(!canAnonymize || customer.anonymized());
        loadCredit(customer);
    }

    private void loadCredit(final CustomerDetailResponse customer) {
        if (!canReadCredit) {
            creditLabel.setText("Permission denied.");
            return;
        }
        UiSupport.onFx(apiClient.creditSummary(customer.customerId()), credit -> creditLabel.setText("""
                Limit: %s
                Outstanding: %s
                Available: %s
                Oldest days: %d
                Eligibility: %s
                """.formatted(credit.creditLimit(), credit.outstandingReceivable(), credit.availableCredit(),
                    credit.oldestOutstandingDays(), credit.eligibility())), "Credit summary could not be loaded.");
    }

    private void clearForm() {
        selected = null;
        detailLabel.setText("New customer");
        nameField.clear();
        phoneField.clear();
        emailField.clear();
        address1Field.clear();
        address2Field.clear();
        cityField.clear();
        nicField.clear();
        brField.clear();
        categoryBox.setValue("RETAIL");
        creditLimitField.setText("0.00");
        marketingBox.setSelected(false);
        sharingBox.setSelected(false);
        saveButton.setDisable(!canCreate);
        blockButton.setDisable(true);
        activateButton.setDisable(true);
        anonymizeButton.setDisable(true);
    }

    private void save() {
        final BigDecimal creditLimit;
        try {
            creditLimit = new BigDecimal(creditLimitField.getText().trim());
        } catch (final NumberFormatException ex) {
            UiSupport.alert("Credit limit must be a number.");
            return;
        }
        if (selected == null) {
            UiSupport.onFx(apiClient.create(new CustomerCreateRequest(nameField.getText(), phoneField.getText(),
                    emailField.getText(), address1Field.getText(), address2Field.getText(), cityField.getText(),
                    nicField.getText(), brField.getText(), categoryBox.getValue(), creditLimit,
                    marketingBox.isSelected(), sharingBox.isSelected())), customer -> {
                select(customer);
                load(0);
            }, "Customer could not be created.");
            return;
        }
        UiSupport.onFx(apiClient.update(selected.customerId(), new CustomerUpdateRequest(nameField.getText(),
                phoneField.getText(), emailField.getText(), address1Field.getText(), address2Field.getText(),
                cityField.getText(), rawPii(nicField.getText()), rawPii(brField.getText()), categoryBox.getValue(),
                creditLimit, marketingBox.isSelected(), sharingBox.isSelected(), selected.version())), customer -> {
            select(customer);
            load(pagination.getCurrentPageIndex());
        }, "Customer could not be saved.");
    }

    private void action(final String failure, final java.util.function.Supplier<java.util.concurrent.CompletableFuture<CustomerDetailResponse>> action) {
        if (selected == null) {
            return;
        }
        UiSupport.onFx(action.get(), customer -> {
            select(customer);
            load(pagination.getCurrentPageIndex());
        }, failure);
    }

    private String rawPii(final String value) {
        return value != null && value.startsWith("***") ? null : value;
    }

    private String nullToBlank(final String value) {
        return value == null ? "" : value;
    }

    private <T> TableColumn<T, String> column(final String title, final java.util.function.Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(120);
        return column;
    }

    private void addRow(final GridPane grid, final int row, final String label, final javafx.scene.Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}


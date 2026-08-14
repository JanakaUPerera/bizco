package com.bizco.client.identity.view;

import com.bizco.client.identity.service.IdentityApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.identity.PermissionResponses.PermissionResponse;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class RoleManagementView {

    public static final List<String> AVAILABLE_PERMISSIONS = List.of(
            "user.create", "user.read", "user.update", "user.lock", "user.unlock",
            "user.grant_role", "user.revoke_role", "user.login_history.read",
            "role.create", "role.read", "role.update", "role.delete",
            "customer.read", "customer.create", "customer.update",
            "product.read", "product.create", "product.update", "product.delete",
            "invoice.read", "invoice.create", "invoice.void", "invoice.payment.create",
            "invoice.payment.refund", "invoice.credit_note.create", "invoice.hold_bill",
            "invoice.override_price", "invoice.sell_below_cost",
            "appointment.read", "appointment.create", "appointment.update", "appointment.cancel",
            "appointment.convert_to_job", "jobcard.read", "jobcard.create", "jobcard.update",
            "jobcard.status_change", "jobcard.parts.add", "jobcard.estimate.create",
            "jobcard.estimate.approve", "jobcard.complete",
            "inventory.read", "inventory.adjustment.create", "inventory.adjustment.approve",
            "purchasing.read", "purchasing.grn.create", "purchasing.return.create",
            "purchasing.payment.create", "finance.read", "finance.cashbook.create",
            "finance.cash_closing.create", "finance.cash_closing.approve",
            "audit.read", "system.config.read", "system.config", "system.backup.read",
            "system.backup.create", "system.backup.restore", "dashboard.read");

    private final IdentityApiClient apiClient;
    private final boolean canWrite;
    private final TableView<RoleResponse> roleTable = new TableView<>();
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final CheckBox activeBox = new CheckBox("Active");
    private final Map<String, CheckBox> permissionBoxes = new LinkedHashMap<>();
    private final Button saveButton = Icons.button("Save", FontAwesomeSolid.SAVE);
    private final Button newButton = Icons.button("New", FontAwesomeSolid.PLUS);
    private RoleResponse selectedRole;

    public RoleManagementView(final IdentityApiClient apiClient, final boolean canWrite) {
        this.apiClient = apiClient;
        this.canWrite = canWrite;
    }

    public Parent createView() {
        roleTable.getStyleClass().add("data-table");
        roleTable.getColumns().setAll(
                column("Code", role -> role.code()),
                column("Name", role -> role.name()),
                column("System", role -> role.system() ? "Yes" : "No"),
                column("Active", role -> role.active() ? "Yes" : "No"));
        roleTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> selectRole(newValue));

        newButton.setDisable(!canWrite);
        newButton.setOnAction(event -> clearForm());
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDisable(!canWrite);
        saveButton.setOnAction(event -> saveRole());
        activeBox.setSelected(true);

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header());
        root.setCenter(roleTable);
        final FlowPane permissions = permissionPane(AVAILABLE_PERMISSIONS);
        root.setRight(form(permissions));
        load();
        loadPermissions(permissions);
        return root;
    }

    private HBox header() {
        final Label title = UiSupport.label("Role Management", "screen-title");
        final HBox header = new HBox(title, spacer(), newButton);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private VBox form(final FlowPane permissions) {
        final GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Code"), 0, 0);
        grid.add(codeField, 1, 0);
        grid.add(new Label("Name"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Status"), 0, 2);
        grid.add(activeBox, 1, 2);

        final VBox form = new VBox(12, UiSupport.label("Role Details", "panel-title"), grid,
                UiSupport.label("Permissions", "panel-title"), permissions, saveButton);
        form.getStyleClass().add("side-panel");
        form.setPadding(new Insets(16));
        return form;
    }

    private FlowPane permissionPane(final List<String> permissions) {
        final FlowPane pane = new FlowPane(8, 8);
        pane.getStyleClass().add("permission-grid");
        permissions.forEach(permission -> addPermissionBox(pane, permission));
        return pane;
    }

    private void loadPermissions(final FlowPane permissions) {
        UiSupport.onFx(apiClient.listPermissions(), registry -> {
            permissions.getChildren().clear();
            permissionBoxes.clear();
            registry.stream()
                    .map(PermissionResponse::permissionCode)
                    .sorted()
                    .forEach(permission -> addPermissionBox(permissions, permission));
            if (selectedRole != null) {
                selectRole(selectedRole);
            }
        }, "Permissions could not be loaded.");
    }

    private void addPermissionBox(final FlowPane permissions, final String permission) {
        final CheckBox box = new CheckBox(permission);
        box.setDisable(!canWrite);
        permissionBoxes.put(permission, box);
        permissions.getChildren().add(box);
    }

    private void load() {
        UiSupport.onFx(apiClient.listRoles(), roles -> roleTable.setItems(FXCollections.observableArrayList(roles)),
                "Roles could not be loaded.");
    }

    private void selectRole(final RoleResponse role) {
        selectedRole = role;
        if (role == null) {
            clearForm();
            return;
        }
        codeField.setText(role.code());
        nameField.setText(role.name());
        activeBox.setSelected(role.active());
        permissionBoxes.forEach((permission, box) -> box.setSelected(Boolean.TRUE.equals(role.permissions().get(permission))));
    }

    private void clearForm() {
        selectedRole = null;
        codeField.clear();
        nameField.clear();
        activeBox.setSelected(true);
        permissionBoxes.values().forEach(box -> box.setSelected(false));
        roleTable.getSelectionModel().clearSelection();
    }

    private void saveRole() {
        if (codeField.getText().isBlank() || nameField.getText().isBlank()) {
            UiSupport.alert("Enter role code and name.");
            return;
        }
        final Map<String, Boolean> permissions = new LinkedHashMap<>();
        permissionBoxes.forEach((permission, box) -> permissions.put(permission, box.isSelected()));
        final RoleUpsertRequest request = new RoleUpsertRequest(codeField.getText().trim().toUpperCase(),
                nameField.getText().trim(), permissions, activeBox.isSelected(),
                selectedRole == null ? 0L : selectedRole.version());
        if (selectedRole == null) {
            UiSupport.onFx(apiClient.createRole(request), role -> load(), "Role could not be created.");
        } else {
            UiSupport.onFx(apiClient.updateRole(selectedRole.id(), request), role -> load(), "Role could not be updated.");
        }
    }

    private <T> TableColumn<T, String> column(final String title, final java.util.function.Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(130);
        return column;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

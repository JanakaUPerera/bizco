package com.bizco.client.identity.view;

import com.bizco.client.identity.service.IdentityApiClient;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.common.dto.identity.RoleResponses.SecondaryRoleResponse;
import com.bizco.common.dto.identity.UserRequests.UserCreateRequest;
import com.bizco.common.dto.identity.UserRequests.UserUpdateRequest;
import com.bizco.common.dto.identity.UserResponses.UserResponse;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class UserManagementView {

    private final IdentityApiClient apiClient;
    private final boolean canWrite;
    private final TableView<UserResponse> userTable = new TableView<>();
    private final TableView<SecondaryRoleResponse> secondaryRoleTable = new TableView<>();
    private final ComboBox<RoleResponse> primaryRoleBox = new ComboBox<>();
    private final ComboBox<RoleResponse> secondaryRoleBox = new ComboBox<>();
    private final TextField usernameField = new TextField();
    private final TextField displayNameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final CheckBox activeBox = new CheckBox("Active");
    private final DatePicker expiresAtPicker = new DatePicker();
    private final Button saveButton = new Button("Save");
    private final Button newButton = new Button("New");
    private final Button grantButton = new Button("Grant");
    private final Button revokeButton = new Button("Revoke");
    private UserResponse selectedUser;

    public UserManagementView(final IdentityApiClient apiClient, final boolean canWrite) {
        this.apiClient = apiClient;
        this.canWrite = canWrite;
    }

    public Parent createView() {
        userTable.getStyleClass().add("data-table");
        userTable.getColumns().setAll(
                column("Username", user -> user.username()),
                column("Display Name", user -> user.displayName()),
                column("Status", user -> user.status()),
                column("Active", user -> user.active() ? "Yes" : "No"));
        userTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> selectUser(newValue));

        saveButton.getStyleClass().add("primary-button");
        saveButton.setDisable(!canWrite);
        saveButton.setOnAction(event -> saveUser());
        newButton.setDisable(!canWrite);
        newButton.setOnAction(event -> clearForm());
        grantButton.setDisable(!canWrite);
        grantButton.setOnAction(event -> grantSecondaryRole());
        revokeButton.setDisable(!canWrite);
        revokeButton.setOnAction(event -> revokeSecondaryRole());

        activeBox.setSelected(true);
        primaryRoleBox.setConverter(roleConverter());
        secondaryRoleBox.setConverter(roleConverter());
        passwordField.setPromptText("Required for new users");

        final BorderPane root = new BorderPane();
        root.getStyleClass().add("content-surface");
        root.setTop(header());
        root.setCenter(userTable);
        root.setRight(form());
        load();
        return root;
    }

    private HBox header() {
        final Label title = UiSupport.label("User Management", "screen-title");
        final HBox header = new HBox(title, spacer(), newButton);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private VBox form() {
        final GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        addRow(grid, 0, "Username", usernameField);
        addRow(grid, 1, "Display Name", displayNameField);
        addRow(grid, 2, "Password", passwordField);
        addRow(grid, 3, "Primary Role", primaryRoleBox);
        addRow(grid, 4, "Status", activeBox);

        secondaryRoleTable.setPrefHeight(180);
        secondaryRoleTable.getColumns().setAll(
                column("Role", role -> role.roleCode()),
                column("Expires", role -> role.expiresAt() == null ? "Never" : role.expiresAt().toString()),
                column("Revoked", role -> role.revokedAt() == null ? "No" : "Yes"));

        final HBox roleActions = new HBox(8, secondaryRoleBox, expiresAtPicker, grantButton, revokeButton);
        HBox.setHgrow(secondaryRoleBox, Priority.ALWAYS);

        final VBox form = new VBox(12, UiSupport.label("User Details", "panel-title"), grid, saveButton,
                UiSupport.label("Secondary Roles", "panel-title"), secondaryRoleTable, roleActions);
        form.getStyleClass().add("side-panel");
        form.setPadding(new Insets(16));
        return form;
    }

    private void load() {
        UiSupport.onFx(apiClient.listRoles(), this::setRoles, "Roles could not be loaded.");
        UiSupport.onFx(apiClient.listUsers(), users -> userTable.setItems(FXCollections.observableArrayList(users)),
                "Users could not be loaded.");
    }

    private void setRoles(final List<RoleResponse> roles) {
        primaryRoleBox.setItems(FXCollections.observableArrayList(roles));
        secondaryRoleBox.setItems(FXCollections.observableArrayList(roles));
    }

    private void selectUser(final UserResponse user) {
        selectedUser = user;
        if (user == null) {
            clearForm();
            return;
        }
        usernameField.setText(user.username());
        usernameField.setDisable(true);
        displayNameField.setText(user.displayName());
        passwordField.clear();
        activeBox.setSelected(user.active());
        primaryRoleBox.getItems().stream()
                .filter(role -> role.id().equals(user.primaryRoleId()))
                .findFirst()
                .ifPresent(primaryRoleBox::setValue);
        UiSupport.onFx(apiClient.listSecondaryRoles(user.id()),
                roles -> secondaryRoleTable.setItems(FXCollections.observableArrayList(roles)),
                "Secondary roles could not be loaded.");
    }

    private void clearForm() {
        selectedUser = null;
        usernameField.clear();
        usernameField.setDisable(false);
        displayNameField.clear();
        passwordField.clear();
        primaryRoleBox.getSelectionModel().clearSelection();
        secondaryRoleTable.getItems().clear();
        activeBox.setSelected(true);
        userTable.getSelectionModel().clearSelection();
    }

    private void saveUser() {
        final RoleResponse primaryRole = primaryRoleBox.getValue();
        if (primaryRole == null || displayNameField.getText().isBlank()
                || (selectedUser == null && (usernameField.getText().isBlank() || passwordField.getText().isBlank()))) {
            UiSupport.alert("Enter username, display name, password, and primary role.");
            return;
        }
        if (selectedUser == null) {
            UiSupport.onFx(apiClient.createUser(new UserCreateRequest(usernameField.getText().trim(),
                    displayNameField.getText().trim(), passwordField.getText(), primaryRole.id())), user -> load(),
                    "User could not be created.");
        } else {
            UiSupport.onFx(apiClient.updateUser(selectedUser.id(), new UserUpdateRequest(
                    displayNameField.getText().trim(), primaryRole.id(), activeBox.isSelected())), user -> load(),
                    "User could not be updated.");
        }
    }

    private void grantSecondaryRole() {
        if (selectedUser == null || secondaryRoleBox.getValue() == null) {
            UiSupport.alert("Select a user and a secondary role.");
            return;
        }
        final var expiresAt = expiresAtPicker.getValue() == null ? null
                : expiresAtPicker.getValue().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
        UiSupport.onFx(apiClient.grantSecondaryRole(selectedUser.id(),
                new SecondaryRoleGrantRequest(secondaryRoleBox.getValue().id(), expiresAt)), grant -> selectUser(selectedUser),
                "Secondary role could not be granted.");
    }

    private void revokeSecondaryRole() {
        final SecondaryRoleResponse grant = secondaryRoleTable.getSelectionModel().getSelectedItem();
        if (grant == null) {
            UiSupport.alert("Select a secondary role grant to revoke.");
            return;
        }
        UiSupport.onFx(apiClient.revokeSecondaryRole(grant.id()), ignored -> selectUser(selectedUser),
                "Secondary role could not be revoked.");
    }

    private <T> TableColumn<T, String> column(final String title, final java.util.function.Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(130);
        return column;
    }

    private void addRow(final GridPane grid, final int row, final String label, final javafx.scene.Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
    }

    private StringConverter<RoleResponse> roleConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(final RoleResponse role) {
                return role == null ? "" : role.code() + " - " + role.name();
            }

            @Override
            public RoleResponse fromString(final String string) {
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

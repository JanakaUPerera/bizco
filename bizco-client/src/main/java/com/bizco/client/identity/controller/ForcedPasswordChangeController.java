package com.bizco.client.identity.controller;

import com.bizco.client.api.ApiClientException;
import com.bizco.client.api.ServerUnavailableException;
import com.bizco.client.api.SessionExpiredException;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.client.identity.service.AuthApiClient;
import com.bizco.client.ui.Icons;
import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Blocks entry into the app shell until a user with a temporary/reset password (server-flagged
 * {@code mustChangePassword}) sets a permanent one. Changing the password revokes the current
 * server-side session (see {@code AuthService.changePassword}), so this always ends by sending
 * the user back to the login screen rather than continuing into the shell with the same token.
 */
public class ForcedPasswordChangeController {

    private final AuthApiClient authApiClient;
    private final ClientSession session;
    private final Runnable passwordChanged;
    private PasswordField currentPasswordField;
    private PasswordField newPasswordField;
    private PasswordField confirmPasswordField;
    private Button submitButton;
    private ProgressIndicator progressIndicator;
    private Label errorLabel;

    public ForcedPasswordChangeController(final AuthApiClient authApiClient, final ClientSession session,
                                          final Runnable passwordChanged) {
        this.authApiClient = Objects.requireNonNull(authApiClient);
        this.session = Objects.requireNonNull(session);
        this.passwordChanged = Objects.requireNonNull(passwordChanged);
    }

    public Parent createView() {
        final HBox card = new HBox(createInfoPanel(), createFormPanel());
        card.getStyleClass().add("login-card");
        card.setMaxSize(680, 501);
        final StackPane root = new StackPane(card);
        root.getStyleClass().add("login-root");
        return root;
    }

    private VBox createInfoPanel() {
        final StackPane iconTile = new StackPane(Icons.of(FontAwesomeSolid.KEY, "login-icon-glyph"));
        iconTile.getStyleClass().add("login-icon-tile");
        final VBox brand = new VBox(6,
                iconTile,
                label("Set Your Password", "login-brand"),
                label("Your account was created with a temporary password. Choose a permanent one "
                        + "to continue.", "login-intro"));
        brand.setAlignment(Pos.TOP_LEFT);
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        final VBox panel = new VBox(brand, spacer);
        panel.getStyleClass().add("login-info-panel");
        panel.setPadding(new Insets(32, 28, 32, 28));
        return panel;
    }

    private VBox createFormPanel() {
        currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText("Temporary password");
        currentPasswordField.getStyleClass().add("login-input");
        currentPasswordField.setMinHeight(40);

        newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        newPasswordField.getStyleClass().add("login-input");
        newPasswordField.setMinHeight(40);

        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");
        confirmPasswordField.getStyleClass().add("login-input");
        confirmPasswordField.setMinHeight(40);
        confirmPasswordField.setOnAction(event -> submit());

        errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setMinHeight(22);
        errorLabel.setWrapText(true);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        submitButton = Icons.button("Set Password", FontAwesomeSolid.CHECK);
        submitButton.getStyleClass().add("login-submit-button");
        submitButton.setMinHeight(40);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(event -> submit());

        final HBox actionRow = new HBox(10, submitButton, progressIndicator);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(submitButton, Priority.ALWAYS);

        final VBox form = new VBox(
                label("Set Your Password", "login-title"),
                label("Password must be 8+ characters with upper, lower, a digit and a symbol.", "login-helper"),
                label("Temporary Password", "login-field-label"),
                currentPasswordField,
                label("New Password", "login-field-label"),
                newPasswordField,
                label("Confirm New Password", "login-field-label"),
                confirmPasswordField,
                errorLabel,
                actionRow);
        form.getStyleClass().add("login-form-panel");
        form.setPadding(new Insets(32, 28, 32, 28));
        form.setSpacing(8);
        return form;
    }

    private void submit() {
        final String current = text(currentPasswordField);
        final String updated = text(newPasswordField);
        final String confirm = text(confirmPasswordField);
        if (current.isBlank() || updated.isBlank() || confirm.isBlank()) {
            showError("Fill in all three fields.");
            return;
        }
        if (!updated.equals(confirm)) {
            showError("New password and confirmation do not match.");
            return;
        }
        setLoading(true);
        authApiClient.changePassword(session, current, updated)
                .whenComplete((ignored, throwable) -> Platform.runLater(() -> {
                    setLoading(false);
                    if (throwable != null) {
                        showError(messageFor(throwable));
                        return;
                    }
                    errorLabel.setText("");
                    passwordChanged.run();
                }));
    }

    private String text(final PasswordField field) {
        return field.getText() == null ? "" : field.getText();
    }

    private Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void setLoading(final boolean loading) {
        submitButton.setDisable(loading);
        currentPasswordField.setDisable(loading);
        newPasswordField.setDisable(loading);
        confirmPasswordField.setDisable(loading);
        progressIndicator.setVisible(loading);
        progressIndicator.setManaged(loading);
    }

    private void showError(final String message) {
        errorLabel.setText(message);
    }

    private String messageFor(final Throwable throwable) {
        final Throwable cause = throwable instanceof java.util.concurrent.CompletionException completionException
                && completionException.getCause() != null ? completionException.getCause() : throwable;
        if (cause instanceof ServerUnavailableException) {
            return "Server is unavailable. Check the connection and try again.";
        }
        if (cause instanceof SessionExpiredException) {
            return "Your session expired. Please sign in again.";
        }
        if (cause instanceof ApiClientException apiClientException && apiClientException.getApiError() != null) {
            return apiClientException.getApiError().message();
        }
        return "Password could not be changed. Check the temporary password and try again.";
    }
}

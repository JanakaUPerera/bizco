package com.bizco.client.identity.controller;

import com.bizco.client.api.ApiClientException;
import com.bizco.client.api.PermissionDeniedException;
import com.bizco.client.api.ServerUnavailableException;
import com.bizco.client.api.SessionExpiredException;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.client.identity.service.AuthApiClient;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LoginController {

    private final AuthApiClient authApiClient;
    private final Consumer<ClientSession> loginSucceeded;
    private final Runnable minimizeRequested;
    private final Runnable closeRequested;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginButton;
    private ProgressIndicator progressIndicator;
    private Label errorLabel;

    public LoginController(final AuthApiClient authApiClient, final Consumer<ClientSession> loginSucceeded,
            final Runnable minimizeRequested, final Runnable closeRequested) {
        this.authApiClient = Objects.requireNonNull(authApiClient);
        this.loginSucceeded = Objects.requireNonNull(loginSucceeded);
        this.minimizeRequested = Objects.requireNonNull(minimizeRequested);
        this.closeRequested = Objects.requireNonNull(closeRequested);
    }

    public Parent createView() {
        final HBox card = new HBox(createInfoPanel(), createFormPanel());
        card.getStyleClass().add("login-card");
        card.setMaxSize(680, 501);

        final HBox windowControls = new HBox(8, windowButton("-", minimizeRequested, "login-window-button"),
                windowButton("X", closeRequested, "login-window-button", "login-close-button"));
        windowControls.setAlignment(Pos.TOP_RIGHT);
        windowControls.setMaxWidth(Region.USE_PREF_SIZE);
        windowControls.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(windowControls, Pos.TOP_RIGHT);
        StackPane.setMargin(windowControls, new Insets(14));

        final StackPane cardShell = new StackPane(card, windowControls);
        cardShell.setMaxSize(680, 501);

        final StackPane root = new StackPane(cardShell);
        root.getStyleClass().add("login-root");
        return root;
    }

    private VBox createInfoPanel() {
        final Label iconGlyph = new Label("B");
        iconGlyph.getStyleClass().add("login-icon-glyph");
        final StackPane iconTile = new StackPane(iconGlyph);
        iconTile.getStyleClass().add("login-icon-tile");

        final VBox brand = new VBox(6,
                iconTile,
                label("Bizco", "login-brand"),
                label("Inventory, sales, purchasing and reporting for growing businesses.", "login-intro"));
        brand.setAlignment(Pos.TOP_LEFT);

        final Label copyright = label("\u00A9 2026 JanaTech", "login-copyright");
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        final VBox panel = new VBox(brand, spacer, copyright);
        panel.getStyleClass().add("login-info-panel");
        panel.setPadding(new Insets(32, 28, 32, 28));
        return panel;
    }

    private VBox createFormPanel() {
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("login-input");
        usernameField.setMinHeight(40);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("login-input");
        passwordField.setMinHeight(40);
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                submit();
            }
        });

        final CheckBox rememberMe = new CheckBox("Remember me");
        rememberMe.getStyleClass().add("login-check");
        final Label forgotPassword = label("Forgot password?", "login-link");
        final HBox options = new HBox(rememberMe, spacer(), forgotPassword);
        options.getStyleClass().add("login-options");
        options.setAlignment(Pos.CENTER_LEFT);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setMinHeight(22);
        errorLabel.setWrapText(true);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        loginButton = new Button("Sign in");
        loginButton.getStyleClass().add("login-submit-button");
        loginButton.setMinHeight(40);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> submit());

        final HBox actionRow = new HBox(10, loginButton, progressIndicator);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loginButton, Priority.ALWAYS);

        final VBox form = new VBox(
                label("Sign in", "login-title"),
                label("Enter your credentials to continue.", "login-helper"),
                label("Username", "login-field-label"),
                usernameField,
                label("Password", "login-field-label"),
                passwordField,
                options,
                errorLabel,
                actionRow);
        form.getStyleClass().add("login-form-panel");
        form.setPadding(new Insets(32, 28, 32, 28));
        form.setSpacing(8);
        return form;
    }

    private Button windowButton(final String text, final Runnable action, final String... styleClasses) {
        final Button button = new Button(text);
        button.getStyleClass().addAll(styleClasses);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void submit() {
        final String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        final String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            showError("Enter your username and password.");
            return;
        }
        setLoading(true);
        authApiClient.login(username, password)
                .whenComplete((session, throwable) -> Platform.runLater(() -> {
                    setLoading(false);
                    if (throwable != null) {
                        showError(messageFor(throwable));
                        return;
                    }
                    errorLabel.setText("");
                    loginSucceeded.accept(session);
                }));
    }

    private Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void setLoading(final boolean loading) {
        loginButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
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
            return "Your session expired. Sign in again.";
        }
        if (cause instanceof PermissionDeniedException) {
            return "You do not have permission to sign in here.";
        }
        if (cause instanceof ApiClientException apiClientException && apiClientException.getApiError() != null) {
            return apiClientException.getApiError().message();
        }
        return "Unable to sign in. Check the server and credentials.";
    }
}

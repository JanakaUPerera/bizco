package com.bizco.client.identity.controller;

import com.bizco.client.identity.dto.ClientSession;
import com.bizco.client.identity.service.AuthApiClient;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class LoginController {

    private final AuthApiClient authApiClient;
    private final Consumer<ClientSession> loginSucceeded;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginButton;
    private ProgressIndicator progressIndicator;
    private Label errorLabel;

    public LoginController(final AuthApiClient authApiClient, final Consumer<ClientSession> loginSucceeded) {
        this.authApiClient = Objects.requireNonNull(authApiClient);
        this.loginSucceeded = Objects.requireNonNull(loginSucceeded);
    }

    public Parent createView() {
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("login-input");
        usernameField.setMinHeight(48);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("login-input");
        passwordField.setMinHeight(48);
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                submit();
            }
        });

        errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setMinHeight(28);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(28, 28);
        progressIndicator.setVisible(false);

        loginButton = new Button("Sign in");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setMinHeight(48);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> submit());

        final VBox form = new VBox(
                label("Bizco", "login-brand"),
                label("Business Management System", "login-subtitle"),
                usernameField,
                passwordField,
                errorLabel,
                new HBox(loginButton, progressIndicator));
        form.getStyleClass().add("login-panel");
        form.setPadding(new Insets(32));
        form.setSpacing(14);
        VBox.setVgrow(loginButton, Priority.NEVER);

        final HBox actionRow = (HBox) form.getChildren().get(5);
        actionRow.setSpacing(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loginButton, Priority.ALWAYS);

        final Region leftBand = new Region();
        leftBand.getStyleClass().add("login-band");
        HBox.setHgrow(leftBand, Priority.ALWAYS);

        final HBox root = new HBox(leftBand, form);
        root.getStyleClass().add("login-root");
        root.setAlignment(Pos.CENTER);
        return root;
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
                        showError("Unable to sign in. Check the server and credentials.");
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

    private void setLoading(final boolean loading) {
        loginButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        progressIndicator.setVisible(loading);
    }

    private void showError(final String message) {
        errorLabel.setText(message);
    }
}

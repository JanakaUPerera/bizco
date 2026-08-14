package com.bizco.client.system.view;

import com.bizco.client.api.ApiExceptions;
import com.bizco.client.api.DatabaseUnavailableException;
import com.bizco.client.system.service.HealthApiClient;
import com.bizco.client.ui.Icons;
import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class SplashView {

    private static final Duration MIN_CHECK_DURATION = Duration.seconds(1.2);
    private static final Duration READY_HOLD_DURATION = Duration.seconds(0.7);

    private final HealthApiClient healthApiClient;
    private final Runnable checksSucceeded;
    private final Runnable minimizeRequested;
    private FontIcon statusIcon;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button retryButton;
    private HBox windowControls;
    private long checksStartedAtMillis;

    public SplashView(final HealthApiClient healthApiClient, final Runnable checksSucceeded,
            final Runnable minimizeRequested) {
        this.healthApiClient = Objects.requireNonNull(healthApiClient);
        this.checksSucceeded = Objects.requireNonNull(checksSucceeded);
        this.minimizeRequested = Objects.requireNonNull(minimizeRequested);
    }

    public Parent createView() {
        final StackPane iconTile = new StackPane(Icons.of(FontAwesomeSolid.STORE, "splash-icon-glyph"));
        iconTile.getStyleClass().add("splash-icon-tile");

        progressBar = new ProgressBar(0.62);
        progressBar.getStyleClass().add("splash-progress");
        progressBar.setMaxWidth(200);

        statusIcon = Icons.of(FontAwesomeSolid.CHECK_CIRCLE, "splash-status-icon");
        statusIcon.setVisible(false);
        statusIcon.setManaged(false);

        statusLabel = new Label("Connecting to server...");
        statusLabel.getStyleClass().add("splash-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(230);
        statusLabel.setAlignment(Pos.CENTER);

        final HBox statusRow = new HBox(6, statusIcon, statusLabel);
        statusRow.getStyleClass().add("splash-status-row");
        statusRow.setAlignment(Pos.CENTER);

        retryButton = Icons.button("Retry", FontAwesomeSolid.SYNC_ALT);
        retryButton.getStyleClass().add("splash-retry-button");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(event -> runChecks());

        final VBox progressArea = new VBox(8, progressBar, statusRow, retryButton);
        progressArea.getStyleClass().add("splash-progress-area");
        progressArea.setAlignment(Pos.CENTER);

        final Label license = new Label("v1.0.0 - Licensed to JanaTech");
        license.getStyleClass().add("splash-license");

        final VBox canvas = new VBox(
                iconTile,
                label("Bizco", "splash-brand"),
                label("Business management, simplified", "splash-subtitle"),
                progressArea,
                license);
        canvas.getStyleClass().add("splash-canvas");
        canvas.setAlignment(Pos.CENTER);

        final Button minimizeButton = Icons.iconOnlyButton(FontAwesomeSolid.MINUS, "splash-window-button");
        minimizeButton.setOnAction(event -> minimizeRequested.run());

        final Button closeButton = Icons.iconOnlyButton(FontAwesomeSolid.TIMES, "splash-window-button", "splash-close-button");
        closeButton.setOnAction(event -> Platform.exit());

        windowControls = new HBox(8, minimizeButton, closeButton);
        windowControls.setVisible(false);
        windowControls.setManaged(false);
        windowControls.setAlignment(Pos.TOP_RIGHT);
        windowControls.setMaxWidth(Region.USE_PREF_SIZE);
        windowControls.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(windowControls, Pos.TOP_RIGHT);
        StackPane.setMargin(windowControls, new Insets(14));

        final StackPane card = new StackPane(canvas, windowControls);
        card.setMaxSize(520, 450);

        final StackPane root = new StackPane(card);
        root.getStyleClass().add("splash-root");
        runChecks();
        return root;
    }

    private void runChecks() {
        setChecking("Connecting to server...");
        checksStartedAtMillis = System.currentTimeMillis();
        healthApiClient.checkHealth()
                .whenComplete((readyMessage, throwable) -> Platform.runLater(() -> afterMinimumDelay(() -> {
                    if (throwable != null) {
                        final Throwable cause = ApiExceptions.unwrap(throwable);
                        if (cause instanceof DatabaseUnavailableException) {
                            setFailed(cause.getMessage(), FontAwesomeSolid.DATABASE);
                        } else {
                            setFailed(serverFailureMessage(cause), FontAwesomeSolid.PLUG);
                        }
                        return;
                    }
                    setReady(readyMessage);
                })));
    }

    private void afterMinimumDelay(final Runnable action) {
        final long elapsedMillis = System.currentTimeMillis() - checksStartedAtMillis;
        final double remainingMillis = MIN_CHECK_DURATION.toMillis() - elapsedMillis;
        if (remainingMillis <= 0) {
            action.run();
            return;
        }
        final PauseTransition pause = new PauseTransition(Duration.millis(remainingMillis));
        pause.setOnFinished(event -> action.run());
        pause.play();
    }

    private void setChecking(final String status) {
        statusLabel.setText(status);
        statusLabel.getStyleClass().removeAll("splash-status-success", "splash-status-error");
        statusIcon.setVisible(false);
        statusIcon.setManaged(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setDisable(false);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        windowControls.setVisible(false);
        windowControls.setManaged(false);
    }

    private void setReady(final String readyMessage) {
        statusLabel.setText(readyMessage);
        statusLabel.getStyleClass().remove("splash-status-error");
        statusLabel.getStyleClass().add("splash-status-success");
        showStatusIcon(FontAwesomeSolid.CHECK_CIRCLE, "splash-status-icon-success", "splash-status-icon-error");
        progressBar.setProgress(1.0);
        final PauseTransition hold = new PauseTransition(READY_HOLD_DURATION);
        hold.setOnFinished(event -> checksSucceeded.run());
        hold.play();
    }

    private void setFailed(final String message, final Ikon icon) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().remove("splash-status-success");
        statusLabel.getStyleClass().add("splash-status-error");
        showStatusIcon(icon, "splash-status-icon-error", "splash-status-icon-success");
        progressBar.setProgress(0.18);
        progressBar.setDisable(true);
        retryButton.setVisible(true);
        retryButton.setManaged(true);
        windowControls.setVisible(true);
        windowControls.setManaged(true);
    }

    private void showStatusIcon(final Ikon icon, final String styleClass, final String otherStyleClass) {
        statusIcon.setIconCode(icon);
        statusIcon.getStyleClass().remove(otherStyleClass);
        if (!statusIcon.getStyleClass().contains(styleClass)) {
            statusIcon.getStyleClass().add(styleClass);
        }
        statusIcon.setVisible(true);
        statusIcon.setManaged(true);
    }

    private Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private String serverFailureMessage(final Throwable cause) {
        return cause.getMessage() == null ? "Cannot reach the Bizco server. Check that it is running and reachable." : cause.getMessage();
    }
}

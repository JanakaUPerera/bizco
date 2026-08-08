package com.bizco.client.system.view;

import com.bizco.client.system.service.HealthApiClient;
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

public class SplashView {

    private static final Duration MIN_CHECK_DURATION = Duration.seconds(1.2);
    private static final Duration READY_HOLD_DURATION = Duration.seconds(0.7);

    private final HealthApiClient healthApiClient;
    private final Runnable checksSucceeded;
    private final Runnable minimizeRequested;
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
        final Label iconGlyph = new Label("B");
        iconGlyph.getStyleClass().add("splash-icon-glyph");
        final StackPane iconTile = new StackPane(iconGlyph);
        iconTile.getStyleClass().add("splash-icon-tile");

        progressBar = new ProgressBar(0.62);
        progressBar.getStyleClass().add("splash-progress");
        progressBar.setMaxWidth(200);

        statusLabel = new Label("Connecting to database...");
        statusLabel.getStyleClass().add("splash-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(260);
        statusLabel.setAlignment(Pos.CENTER);

        retryButton = new Button("Retry");
        retryButton.getStyleClass().add("splash-retry-button");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(event -> runChecks());

        final VBox progressArea = new VBox(8, progressBar, statusLabel, retryButton);
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

        final Button minimizeButton = new Button("-");
        minimizeButton.getStyleClass().add("splash-window-button");
        minimizeButton.setOnAction(event -> minimizeRequested.run());

        final Button closeButton = new Button("X");
        closeButton.getStyleClass().addAll("splash-window-button", "splash-close-button");
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
        setChecking("Connecting to database...");
        checksStartedAtMillis = System.currentTimeMillis();
        healthApiClient.checkHealth()
                .whenComplete((readyMessage, throwable) -> Platform.runLater(() -> afterMinimumDelay(() -> {
                    if (throwable != null) {
                        setFailed(rootMessage(throwable));
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
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setDisable(false);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        windowControls.setVisible(false);
        windowControls.setManaged(false);
    }

    private void setReady(final String readyMessage) {
        statusLabel.setText(readyMessage);
        progressBar.setProgress(1.0);
        final PauseTransition hold = new PauseTransition(READY_HOLD_DURATION);
        hold.setOnFinished(event -> checksSucceeded.run());
        hold.play();
    }

    private void setFailed(final String message) {
        statusLabel.setText(message);
        progressBar.setProgress(0.18);
        progressBar.setDisable(true);
        retryButton.setVisible(true);
        retryButton.setManaged(true);
        windowControls.setVisible(true);
        windowControls.setManaged(true);
    }

    private Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private String rootMessage(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Server or database is not available." : current.getMessage();
    }
}

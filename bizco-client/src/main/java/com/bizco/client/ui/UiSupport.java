package com.bizco.client.ui;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

public final class UiSupport {

    private UiSupport() {
    }

    public static Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    public static <T> void onFx(final CompletableFuture<T> future, final Consumer<T> success, final String failureMessage) {
        future.whenComplete((value, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                alert(failureMessage + " " + rootMessage(throwable));
                return;
            }
            success.accept(value);
        }));
    }

    public static void alert(final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Bizco");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static String rootMessage(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}

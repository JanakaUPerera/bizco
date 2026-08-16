package com.bizco.client.ui;

import com.bizco.client.api.ApiExceptions;
import com.bizco.client.api.PermissionDeniedException;
import com.bizco.client.api.SessionExpiredException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

public final class UiSupport {

    private static volatile Runnable sessionExpiredHandler = () -> { };
    private static volatile Runnable permissionDeniedHandler = () -> { };

    private UiSupport() {
    }

    public static Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /**
     * Registers the app-shell callback that runs whenever any {@link #onFx} call observes an
     * expired/invalid session (HTTP 401), instead of that call falling through to a generic alert.
     * The shell uses this to force the user back to the login screen.
     */
    public static void onSessionExpired(final Runnable handler) {
        sessionExpiredHandler = handler == null ? () -> { } : handler;
    }

    /**
     * Registers the app-shell callback that runs (in addition to the normal error alert) whenever
     * any {@link #onFx} call is denied by permission (HTTP 403). The shell uses this to silently
     * refresh effective permissions in the background, so navigation catches up with a role change
     * without waiting for the next scheduled refresh or a fresh login.
     */
    public static void onPermissionDenied(final Runnable handler) {
        permissionDeniedHandler = handler == null ? () -> { } : handler;
    }

    public static <T> void onFx(final CompletableFuture<T> future, final Consumer<T> success, final String failureMessage) {
        future.whenComplete((value, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                final Throwable cause = ApiExceptions.unwrap(throwable);
                if (cause instanceof SessionExpiredException) {
                    sessionExpiredHandler.run();
                    return;
                }
                if (cause instanceof PermissionDeniedException) {
                    permissionDeniedHandler.run();
                }
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

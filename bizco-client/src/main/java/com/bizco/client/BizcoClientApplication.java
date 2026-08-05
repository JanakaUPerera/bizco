package com.bizco.client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BizcoClientApplication extends Application {

    private static final int MIN_WIDTH = 1024;
    private static final int MIN_HEIGHT = 720;

    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage stage) {
        final BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(createTopBar());
        root.setLeft(createSidebar());
        root.setCenter(createContentPlaceholder());

        final Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());

        stage.setTitle("Bizco");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    private HBox createTopBar() {
        final Label title = new Label("Bizco");
        title.getStyleClass().add("topbar-title");

        final Label environment = new Label("Foundation");
        environment.getStyleClass().add("topbar-status");

        final HBox topbar = new HBox(title, spacer(), environment);
        topbar.getStyleClass().add("topbar");
        topbar.setPadding(new Insets(12, 16, 12, 16));
        return topbar;
    }

    private VBox createSidebar() {
        final VBox sidebar = new VBox(
                navigationLabel("Dashboard"),
                navigationLabel("Customers"),
                navigationLabel("Products"),
                navigationLabel("POS"),
                navigationLabel("Scheduling"),
                navigationLabel("Reports"),
                navigationLabel("Administration"));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16));
        return sidebar;
    }

    private Label createContentPlaceholder() {
        final Label label = new Label("Application shell ready");
        label.getStyleClass().add("content-placeholder");
        return label;
    }

    private Label navigationLabel(final String text) {
        final Label label = new Label(text);
        label.getStyleClass().add("nav-item");
        label.setMinHeight(48);
        return label;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}

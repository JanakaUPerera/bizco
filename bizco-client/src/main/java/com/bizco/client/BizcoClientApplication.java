package com.bizco.client;

import com.bizco.client.identity.controller.LoginController;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.client.identity.service.AuthApiClient;
import com.bizco.client.identity.service.IdentityApiClient;
import com.bizco.client.identity.view.RoleManagementView;
import com.bizco.client.identity.view.UserManagementView;
import com.bizco.client.system.service.BusinessProfileApiClient;
import com.bizco.client.system.view.BusinessProfileView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BizcoClientApplication extends Application {

    private static final int MIN_WIDTH = 1180;
    private static final int MIN_HEIGHT = 760;

    private Stage stage;
    private ClientSession session;
    private BorderPane shell;
    private IdentityApiClient identityApiClient;
    private BusinessProfileApiClient businessProfileApiClient;

    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage stage) {
        this.stage = stage;
        stage.setTitle("Bizco");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        final LoginController loginController = new LoginController(new AuthApiClient(), this::showShell);
        setScene(new Scene(loginController.createView(), MIN_WIDTH, MIN_HEIGHT));
    }

    private void showShell(final ClientSession session) {
        this.session = session;
        this.identityApiClient = new IdentityApiClient(session);
        this.businessProfileApiClient = new BusinessProfileApiClient(session);
        shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(createTopBar());
        shell.setLeft(createSidebar());
        shell.setCenter(createDashboard());
        setScene(new Scene(shell, MIN_WIDTH, MIN_HEIGHT));
        checkFirstLaunchProfile();
    }

    private void setScene(final Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());
        stage.setScene(scene);
    }

    private HBox createTopBar() {
        final Label title = new Label("Bizco");
        title.getStyleClass().add("topbar-title");

        final Label user = new Label(session.displayName());
        user.getStyleClass().add("topbar-status");
        final Label date = new Label(LocalDate.now().toString());
        date.getStyleClass().add("topbar-status");

        final HBox topbar = new HBox(16, title, spacer(), date, user);
        topbar.getStyleClass().add("topbar");
        topbar.setPadding(new Insets(12, 16, 12, 16));
        return topbar;
    }

    private VBox createSidebar() {
        final VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16));
        navigation().forEach(module -> {
            if (module.visible(session)) {
                final Button button = new Button(module.title());
                button.getStyleClass().add("nav-item");
                button.setMinHeight(48);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(event -> shell.setCenter(module.viewFactory().get()));
                sidebar.getChildren().add(button);
            }
        });
        return sidebar;
    }

    private List<ModuleItem> navigation() {
        final List<ModuleItem> modules = new ArrayList<>();
        modules.add(new ModuleItem("Dashboard", null, this::createDashboard));
        modules.add(new ModuleItem("Customers", "customer.read", () -> placeholder("Customers")));
        modules.add(new ModuleItem("Products", "catalog.product.read", () -> placeholder("Products")));
        modules.add(new ModuleItem("POS", "sales.pos.open", () -> placeholder("POS")));
        modules.add(new ModuleItem("Scheduling", "scheduling.appointment.read", () -> placeholder("Scheduling")));
        modules.add(new ModuleItem("Reports", "report.sales.view", () -> placeholder("Reports")));
        modules.add(new ModuleItem("User Management", "identity.user.read",
                () -> new UserManagementView(identityApiClient, session.hasPermission("identity.user.write")).createView()));
        modules.add(new ModuleItem("Role Management", "identity.role.read",
                () -> new RoleManagementView(identityApiClient, session.hasPermission("identity.role.write")).createView()));
        modules.add(new ModuleItem("Business Profile", "identity.role.read",
                () -> new BusinessProfileView(businessProfileApiClient,
                        session.hasPermission("identity.role.write"), () -> shell.setCenter(createDashboard()))
                        .createView(false)));
        return modules;
    }

    private Parent createDashboard() {
        final GridPane grid = new GridPane();
        grid.getStyleClass().add("dashboard-grid");
        grid.setPadding(new Insets(24));
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(kpi("Today Sales", "Rs. 0.00"), 0, 0);
        grid.add(kpi("Open Invoices", "0"), 1, 0);
        grid.add(kpi("Appointments", "0"), 2, 0);
        grid.add(kpi("Low Stock", "0"), 3, 0);
        grid.add(placeholder("Recent Activity"), 0, 1, 4, 1);
        return grid;
    }

    private VBox kpi(final String title, final String value) {
        final Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-title");
        final Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("kpi-value");
        final VBox box = new VBox(8, titleLabel, valueLabel);
        box.getStyleClass().add("kpi-card");
        return box;
    }

    private Label placeholder(final String name) {
        final Label label = new Label(name + " module foundation ready");
        label.getStyleClass().add("content-placeholder");
        return label;
    }

    private void checkFirstLaunchProfile() {
        if (!session.hasPermission("identity.role.read") || !session.hasPermission("identity.role.write")) {
            return;
        }
        businessProfileApiClient.getProfile().whenComplete((profile, throwable) -> Platform.runLater(() -> {
            if (throwable == null && profile.isEmpty()) {
                shell.setCenter(new BusinessProfileView(businessProfileApiClient, true,
                        () -> shell.setCenter(createDashboard())).createView(true));
            }
        }));
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record ModuleItem(String title, String requiredPermission, ModuleViewFactory viewFactory) {
        boolean visible(final ClientSession session) {
            return requiredPermission == null || session.hasPermission(requiredPermission);
        }
    }

    @FunctionalInterface
    private interface ModuleViewFactory {
        Parent get();
    }
}

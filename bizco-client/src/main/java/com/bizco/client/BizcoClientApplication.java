package com.bizco.client;

import com.bizco.client.identity.controller.LoginController;
import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.catalog.view.MasterDataManagementView;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.customer.view.CustomerManagementView;
import com.bizco.client.identity.dto.ClientSession;
import com.bizco.client.identity.service.AuthApiClient;
import com.bizco.client.identity.service.IdentityApiClient;
import com.bizco.client.identity.view.RoleManagementView;
import com.bizco.client.identity.view.UserManagementView;
import com.bizco.client.purchasing.service.SupplierApiClient;
import com.bizco.client.system.service.BusinessProfileApiClient;
import com.bizco.client.system.service.HealthApiClient;
import com.bizco.client.system.view.BusinessProfileView;
import com.bizco.client.system.view.SplashView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class BizcoClientApplication extends Application {

    private static final int MIN_WIDTH = 1180;
    private static final int MIN_HEIGHT = 760;
    private static final int LOGIN_WIDTH = 680;
    private static final int LOGIN_HEIGHT = 501;
    private static final DateTimeFormatter FOOTER_CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm:ss a  •  MM/dd/yyyy");
    private static final Duration CONNECTION_CHECK_INTERVAL = Duration.seconds(30);

    private Stage stage;
    private Stage splashStage;
    private ClientSession session;
    private BorderPane shell;
    private IdentityApiClient identityApiClient;
    private CustomerApiClient customerApiClient;
    private CatalogApiClient catalogApiClient;
    private SupplierApiClient supplierApiClient;
    private BusinessProfileApiClient businessProfileApiClient;

    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Bizco");
        stage.setMinWidth(LOGIN_WIDTH);
        stage.setMinHeight(LOGIN_HEIGHT);
        showSplash();
    }

    private void showSplash() {
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        splashStage = new Stage(StageStyle.TRANSPARENT);
        splashStage.setTitle("Bizco");
        splashStage.setAlwaysOnTop(true);
        splashStage.setX(bounds.getMinX());
        splashStage.setY(bounds.getMinY());
        splashStage.setWidth(bounds.getWidth());
        splashStage.setHeight(bounds.getHeight());

        final SplashView splashView = new SplashView(new HealthApiClient(), this::showLoginAfterSplash,
                () -> splashStage.setIconified(true));
        final Scene scene = new Scene(splashView.createView(), bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());
        splashStage.setScene(scene);
        splashStage.show();
    }

    private void showLoginAfterSplash() {
        if (splashStage != null) {
            splashStage.close();
            splashStage = null;
        }
        showLogin();
        stage.show();
    }

    private void showLogin() {
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        final LoginController loginController = new LoginController(new AuthApiClient(), this::showShell,
                () -> stage.setIconified(true), Platform::exit);
        stage.setMinWidth(LOGIN_WIDTH);
        stage.setMinHeight(LOGIN_HEIGHT);
        final Scene scene = new Scene(loginController.createView(), bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);
        setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    private void showShell(final ClientSession session) {
        this.session = session;
        this.identityApiClient = new IdentityApiClient(session);
        this.customerApiClient = new CustomerApiClient(session);
        this.catalogApiClient = new CatalogApiClient(session);
        this.supplierApiClient = new SupplierApiClient(session);
        this.businessProfileApiClient = new BusinessProfileApiClient(session);
        shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(createHeader());
        shell.setLeft(createSidebar());
        shell.setCenter(createDashboard());
        shell.setBottom(createFooter());
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        setScene(new Scene(shell, MIN_WIDTH, MIN_HEIGHT));
        stage.sizeToScene();
        stage.centerOnScreen();
        checkFirstLaunchProfile();
    }

    private void setScene(final Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());
        stage.setScene(scene);
    }

    private HBox createHeader() {
        final Label logoGlyph = new Label("B");
        logoGlyph.getStyleClass().add("app-header-logo-glyph");
        final StackPane logoTile = new StackPane(logoGlyph);
        logoTile.getStyleClass().add("app-header-logo-tile");

        final Label brand = new Label("Bizco");
        brand.getStyleClass().add("app-header-brand");

        final HBox brandGroup = new HBox(10, logoTile, brand);
        brandGroup.setAlignment(Pos.CENTER_LEFT);

        final Label avatarGlyph = new Label(initials(session.displayName()));
        avatarGlyph.getStyleClass().add("app-header-avatar-glyph");
        final StackPane avatarTile = new StackPane(avatarGlyph);
        avatarTile.getStyleClass().add("app-header-avatar-tile");

        final Label userName = new Label(session.displayName());
        userName.getStyleClass().add("app-header-user-name");
        final Label userLabel = new Label(session.username());
        userLabel.getStyleClass().add("app-header-user-role");
        final VBox userText = new VBox(2, userName, userLabel);
        userText.setAlignment(Pos.CENTER_RIGHT);

        final HBox userGroup = new HBox(10, userText, avatarTile);
        userGroup.setAlignment(Pos.CENTER_RIGHT);

        final HBox windowControls = new HBox(6,
                windowButton("-", "app-header-window-button", () -> stage.setIconified(true)),
                windowButton("[ ]", "app-header-window-button", () -> stage.setMaximized(!stage.isMaximized())),
                windowButton("X", "app-header-window-button app-header-close-button", Platform::exit));
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        final HBox header = new HBox(20, brandGroup, spacer(), userGroup, windowControls);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        return header;
    }

    private HBox createFooter() {
        final Label dateTimeLabel = new Label();
        dateTimeLabel.getStyleClass().add("app-footer-datetime");
        updateClock(dateTimeLabel);
        final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateClock(dateTimeLabel)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        final Region statusDot = new Region();
        statusDot.getStyleClass().addAll("app-footer-status-dot", "app-footer-status-checking");
        statusDot.setMinSize(8, 8);
        statusDot.setMaxSize(8, 8);

        final Label statusLabel = new Label("Checking connection...");
        statusLabel.getStyleClass().add("app-footer-text");

        final HBox statusGroup = new HBox(6, statusDot, statusLabel);
        statusGroup.setAlignment(Pos.CENTER_LEFT);

        final Label versionLabel = new Label("v1.0.0");
        versionLabel.getStyleClass().add("app-footer-text");
        final Label copyrightLabel = new Label("© 2026 JanaTech");
        copyrightLabel.getStyleClass().add("app-footer-text");
        final HBox aboutGroup = new HBox(10, versionLabel, copyrightLabel);
        aboutGroup.setAlignment(Pos.CENTER_RIGHT);

        final HBox footer = new HBox(16, statusGroup, spacer(), dateTimeLabel, spacer(), aboutGroup);
        footer.getStyleClass().add("app-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 16, 8, 16));

        startConnectionMonitor(statusDot, statusLabel);
        return footer;
    }

    private void updateClock(final Label label) {
        label.setText(LocalDateTime.now().format(FOOTER_CLOCK_FORMAT));
    }

    private void startConnectionMonitor(final Region statusDot, final Label statusLabel) {
        final HealthApiClient client = new HealthApiClient();
        final Runnable check = () -> client.checkHealth().whenComplete((message, throwable) -> Platform.runLater(() -> {
            statusDot.getStyleClass().removeAll(
                    "app-footer-status-connected", "app-footer-status-disconnected", "app-footer-status-checking");
            if (throwable != null) {
                statusDot.getStyleClass().add("app-footer-status-disconnected");
                statusLabel.setText("Server or database disconnected");
            } else {
                statusDot.getStyleClass().add("app-footer-status-connected");
                statusLabel.setText("Server and database connected");
            }
        }));
        check.run();
        final Timeline monitor = new Timeline(new KeyFrame(CONNECTION_CHECK_INTERVAL, event -> check.run()));
        monitor.setCycleCount(Timeline.INDEFINITE);
        monitor.play();
    }

    private Button windowButton(final String text, final String styleClasses, final Runnable action) {
        final Button button = new Button(text);
        button.getStyleClass().addAll(styleClasses.split(" "));
        button.setOnAction(event -> action.run());
        return button;
    }

    private String initials(final String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        final StringBuilder initials = new StringBuilder();
        for (final String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.toString();
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
        modules.add(new ModuleItem("Customers", "customer.read", () -> new CustomerManagementView(customerApiClient,
                session.hasPermission("customer.create"), session.hasPermission("customer.update"),
                session.hasPermission("customer.anonymize"), session.hasPermission("customer.credit.read")).createView()));
        modules.add(new ModuleItem("Master Data", "product.read", () -> new MasterDataManagementView(catalogApiClient,
                supplierApiClient, session.hasPermission("product.create"), session.hasPermission("product.update"),
                session.hasPermission("product.delete"), session.hasPermission("product.view_cost"),
                session.hasPermission("product.category.create") || session.hasPermission("product.category.update"),
                session.hasPermission("service.create") || session.hasPermission("service.update"),
                session.hasPermission("supplier.create"), session.hasPermission("supplier.update"),
                session.hasPermission("supplier.deactivate")).createView()));
        modules.add(new ModuleItem("POS", "invoice.create", () -> placeholder("POS")));
        modules.add(new ModuleItem("Scheduling", "appointment.read", () -> placeholder("Scheduling")));
        modules.add(new ModuleItem("Reports", "audit.read", () -> placeholder("Reports")));
        modules.add(new ModuleItem("User Management", "user.read",
                () -> new UserManagementView(identityApiClient, session.hasPermission("user.update")).createView()));
        modules.add(new ModuleItem("Role Management", "role.read",
                () -> new RoleManagementView(identityApiClient, session.hasPermission("role.update")).createView()));
        modules.add(new ModuleItem("Business Profile", "system.config.read",
                () -> new BusinessProfileView(businessProfileApiClient,
                        session.hasPermission("system.config"), () -> shell.setCenter(createDashboard()))
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
        if (!session.hasPermission("system.config.read") || !session.hasPermission("system.config")) {
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

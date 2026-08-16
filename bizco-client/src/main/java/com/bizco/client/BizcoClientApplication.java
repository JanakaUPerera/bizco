package com.bizco.client;

import com.bizco.client.api.ApiExceptions;
import com.bizco.client.api.DatabaseUnavailableException;
import com.bizco.client.api.SessionExpiredException;
import com.bizco.client.identity.controller.ForcedPasswordChangeController;
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
import com.bizco.client.system.service.TaxConfigurationApiClient;
import com.bizco.client.system.view.BusinessProfileView;
import com.bizco.client.system.view.OnboardingWizardView;
import com.bizco.client.system.view.SplashView;
import com.bizco.client.system.view.TaxConfigurationView;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.ThemeManager;
import com.bizco.client.ui.UiSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class BizcoClientApplication extends Application {

    private static final int MIN_WIDTH = 1180;
    private static final int MIN_HEIGHT = 760;
    private static final int LOGIN_WIDTH = 680;
    private static final int LOGIN_HEIGHT = 501;
    private static final double SIDEBAR_EXPANDED_WIDTH = 220;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 68;
    private static final Duration SIDEBAR_TOGGLE_DURATION = Duration.millis(160);
    private static final DateTimeFormatter FOOTER_CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm a  •  MM/dd/yyyy");
    private static final Duration CONNECTION_CHECK_INTERVAL = Duration.seconds(30);
    private static final Duration PERMISSION_REFRESH_INTERVAL = Duration.seconds(60);

    private final ThemeManager themeManager = new ThemeManager();
    private final List<Button> navButtons = new ArrayList<>();
    private final PauseTransition navFlyoutHideDelay = new PauseTransition(Duration.millis(150));

    private Stage stage;
    private Stage splashStage;
    private ClientSession session;
    private BorderPane shell;
    private VBox sidebar;
    private Button sidebarToggleButton;
    private boolean sidebarCollapsed;
    private Popup navFlyout;
    private Button navFlyoutAnchor;
    private Button themeToggleButton;
    private Button maximizeToggleButton;
    private IdentityApiClient identityApiClient;
    private CustomerApiClient customerApiClient;
    private CatalogApiClient catalogApiClient;
    private SupplierApiClient supplierApiClient;
    private BusinessProfileApiClient businessProfileApiClient;
    private TaxConfigurationApiClient taxConfigurationApiClient;
    private final AuthApiClient authApiClient = new AuthApiClient();
    private Timeline permissionRefreshMonitor;
    private boolean loggingOut;

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
        loggingOut = false;
        final LoginController loginController = new LoginController(authApiClient, this::showShell,
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

    /** Blocks the shell behind a mandatory password change for a temporary/reset password. */
    private void showForcedPasswordChange(final ClientSession session) {
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        final ForcedPasswordChangeController controller = new ForcedPasswordChangeController(authApiClient, session,
                () -> {
                    UiSupport.alert("Password updated. Please sign in with your new password.");
                    showLogin();
                });
        stage.setMinWidth(LOGIN_WIDTH);
        stage.setMinHeight(LOGIN_HEIGHT);
        final Scene scene = new Scene(controller.createView(), bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);
        setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    private void showShell(final ClientSession session) {
        if (session.mustChangePassword()) {
            showForcedPasswordChange(session);
            return;
        }
        this.session = session;
        this.identityApiClient = new IdentityApiClient(session);
        this.customerApiClient = new CustomerApiClient(session);
        this.catalogApiClient = new CatalogApiClient(session);
        this.supplierApiClient = new SupplierApiClient(session);
        this.businessProfileApiClient = new BusinessProfileApiClient(session);
        this.taxConfigurationApiClient = new TaxConfigurationApiClient(session);
        UiSupport.onSessionExpired(() -> forceLogout("Your session has expired. Please sign in again."));
        UiSupport.onPermissionDenied(this::refreshPermissions);
        startPermissionRefreshMonitor();
        shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        themeManager.apply(shell, themeManager.loadSavedTheme());
        shell.setTop(createHeader());
        shell.setLeft(createSidebar());
        shell.setCenter(createDashboard());
        shell.setBottom(createFooter());
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        setScene(new Scene(shell, bounds.getWidth(), bounds.getHeight()));
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        checkFirstLaunchProfile();
    }

    private void setScene(final Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());
        stage.setScene(scene);
    }

    private HBox createHeader() {
        final StackPane logoTile = new StackPane(Icons.of(FontAwesomeSolid.STORE, "app-header-logo-glyph"));
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

        themeToggleButton = Icons.iconOnlyButton(themeToggleIcon(), "app-header-window-button");
        themeToggleButton.setTooltip(new Tooltip(themeToggleTooltip()));
        themeToggleButton.setOnAction(event -> toggleTheme());

        maximizeToggleButton = Icons.iconOnlyButton(maximizeToggleIcon(), "app-header-window-button");
        maximizeToggleButton.setTooltip(new Tooltip(maximizeToggleTooltip()));
        maximizeToggleButton.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        stage.maximizedProperty().addListener((observable, wasMaximized, isMaximized) -> updateMaximizeToggleButton());

        final HBox windowControls = new HBox(6,
                windowButton(FontAwesomeSolid.MINUS, "app-header-window-button", () -> stage.setIconified(true)),
                maximizeToggleButton,
                windowButton(FontAwesomeSolid.TIMES, "app-header-window-button app-header-close-button", Platform::exit));
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        final HBox header = new HBox(20, brandGroup, spacer(), userGroup, themeToggleButton, windowControls);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        enableWindowDrag(header);
        return header;
    }

    /** Lets the user reposition the undecorated window by dragging the header background. */
    private void enableWindowDrag(final Node dragHandle) {
        final double[] dragAnchor = new double[2];
        dragHandle.setOnMousePressed(event -> {
            dragAnchor[0] = event.getScreenX() - stage.getX();
            dragAnchor[1] = event.getScreenY() - stage.getY();
        });
        dragHandle.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragAnchor[0]);
            stage.setY(event.getScreenY() - dragAnchor[1]);
        });
    }

    private HBox createFooter() {
        final Label dateTimeLabel = new Label();
        dateTimeLabel.getStyleClass().add("app-footer-datetime");
        updateClock(dateTimeLabel);
        final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateClock(dateTimeLabel)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        final FontIcon serverStatusIcon = Icons.of(FontAwesomeSolid.SERVER, "app-footer-status-icon");
        serverStatusIcon.getStyleClass().add("app-footer-status-icon-checking");
        final Label serverStatusText = new Label("Server");
        serverStatusText.getStyleClass().add("app-footer-text");
        final HBox serverStatusGroup = new HBox(4, serverStatusIcon, serverStatusText);
        serverStatusGroup.getStyleClass().add("app-footer-status-group");
        serverStatusGroup.setAlignment(Pos.CENTER_LEFT);

        final FontIcon databaseStatusIcon = Icons.of(FontAwesomeSolid.DATABASE, "app-footer-status-icon");
        databaseStatusIcon.getStyleClass().add("app-footer-status-icon-checking");
        final Label databaseStatusText = new Label("Database");
        databaseStatusText.getStyleClass().add("app-footer-text");
        final HBox databaseStatusGroup = new HBox(4, databaseStatusIcon, databaseStatusText);
        databaseStatusGroup.getStyleClass().add("app-footer-status-group");
        databaseStatusGroup.setAlignment(Pos.CENTER_LEFT);

        final HBox statusGroup = new HBox(16, serverStatusGroup, databaseStatusGroup);
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

        startConnectionMonitor(serverStatusIcon, databaseStatusIcon);
        return footer;
    }

    private void updateClock(final Label label) {
        label.setText(LocalDateTime.now().format(FOOTER_CLOCK_FORMAT));
    }

    private void startConnectionMonitor(final FontIcon serverStatusIcon, final FontIcon databaseStatusIcon) {
        final HealthApiClient client = new HealthApiClient();
        final Runnable check = () -> client.checkHealth().whenComplete((message, throwable) -> Platform.runLater(() -> {
            final boolean databaseIssue = throwable != null
                    && ApiExceptions.unwrap(throwable) instanceof DatabaseUnavailableException;
            final boolean databaseUp = throwable == null;
            final boolean serverUp = throwable == null || databaseIssue;
            applyConnectionStatus(serverStatusIcon, serverUp);
            applyConnectionStatus(databaseStatusIcon, databaseUp);
        }));
        check.run();
        final Timeline monitor = new Timeline(new KeyFrame(CONNECTION_CHECK_INTERVAL, event -> check.run()));
        monitor.setCycleCount(Timeline.INDEFINITE);
        monitor.play();
    }

    /**
     * Periodically re-fetches effective permissions so a secondary role granted, revoked, or
     * expired on the server is reflected in the sidebar without the user needing to log out and
     * back in. Also runs on-demand whenever any API call comes back permission-denied.
     */
    private void startPermissionRefreshMonitor() {
        permissionRefreshMonitor = new Timeline(new KeyFrame(PERMISSION_REFRESH_INTERVAL, event -> refreshPermissions()));
        permissionRefreshMonitor.setCycleCount(Timeline.INDEFINITE);
        permissionRefreshMonitor.play();
    }

    private void refreshPermissions() {
        if (session == null || loggingOut) {
            return;
        }
        authApiClient.me(session).whenComplete((refreshed, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                if (ApiExceptions.unwrap(throwable) instanceof SessionExpiredException) {
                    forceLogout("Your session has expired. Please sign in again.");
                }
                return;
            }
            final boolean permissionsChanged = !refreshed.permissions().equals(session.permissions());
            this.session = refreshed;
            if (permissionsChanged && shell != null) {
                shell.setLeft(createSidebar());
            }
        }));
    }

    /** Returns the user to the login screen, e.g. after the server reports the session expired or was revoked. */
    private void forceLogout(final String message) {
        if (loggingOut) {
            return;
        }
        loggingOut = true;
        if (permissionRefreshMonitor != null) {
            permissionRefreshMonitor.stop();
        }
        UiSupport.alert(message);
        session = null;
        showLogin();
    }

    private void applyConnectionStatus(final FontIcon icon, final boolean up) {
        icon.getStyleClass().removeAll(
                "app-footer-status-icon-checking", "app-footer-status-icon-success", "app-footer-status-icon-error");
        icon.getStyleClass().add(up ? "app-footer-status-icon-success" : "app-footer-status-icon-error");
    }

    private Button windowButton(final Ikon icon, final String styleClasses, final Runnable action) {
        final Button button = Icons.iconOnlyButton(icon, styleClasses.split(" "));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void toggleTheme() {
        themeManager.toggle(shell);
        themeToggleButton.setGraphic(Icons.of(themeToggleIcon()));
        themeToggleButton.setTooltip(new Tooltip(themeToggleTooltip()));
    }

    private Ikon themeToggleIcon() {
        return themeManager.current() == ThemeManager.Theme.DARK ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON;
    }

    private String themeToggleTooltip() {
        return themeManager.current() == ThemeManager.Theme.DARK ? "Switch to light mode" : "Switch to dark mode";
    }

    private void updateMaximizeToggleButton() {
        maximizeToggleButton.setGraphic(Icons.of(maximizeToggleIcon()));
        maximizeToggleButton.setTooltip(new Tooltip(maximizeToggleTooltip()));
    }

    private Ikon maximizeToggleIcon() {
        return stage.isMaximized() ? FontAwesomeSolid.WINDOW_RESTORE : FontAwesomeSolid.WINDOW_MAXIMIZE;
    }

    private String maximizeToggleTooltip() {
        return stage.isMaximized() ? "Restore down" : "Maximize";
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
        sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16));
        sidebar.setPrefWidth(sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

        navFlyoutHideDelay.setOnFinished(event -> hideNavFlyout());

        sidebarToggleButton = Icons.iconOnlyButton(FontAwesomeSolid.CHEVRON_LEFT, "sidebar-toggle-button");
        sidebarToggleButton.setOnAction(event -> toggleSidebar());
        final HBox toggleRow = new HBox(sidebarToggleButton);
        toggleRow.getStyleClass().add("sidebar-toggle-row");

        final VBox navItems = new VBox();
        navItems.getStyleClass().add("sidebar-nav-items");

        navButtons.clear();
        navigation().forEach(module -> {
            if (module.visible(session)) {
                final Button button = new Button(module.title(), Icons.of(module.icon(), "nav-item-icon"));
                button.setGraphicTextGap(10);
                button.getStyleClass().add("nav-item");
                button.setMinHeight(48);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setUserData(module.title());
                button.setOnAction(event -> {
                    shell.setCenter(module.viewFactory().get());
                    setActiveNavButton(navButtons, button);
                });
                installNavFlyout(button, module);
                navButtons.add(button);
                navItems.getChildren().add(button);
            }
        });
        if (!navButtons.isEmpty()) {
            setActiveNavButton(navButtons, navButtons.get(0));
        }
        applySidebarCollapsedState();

        sidebar.getChildren().addAll(toggleRow, navItems);
        return sidebar;
    }

    /** Collapses the sidebar to icon-only width or restores it. */
    private void toggleSidebar() {
        hideNavFlyout();
        sidebarCollapsed = !sidebarCollapsed;
        final Timeline resize = new Timeline(new KeyFrame(SIDEBAR_TOGGLE_DURATION,
                new KeyValue(sidebar.prefWidthProperty(),
                        sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH)));
        resize.play();
        applySidebarCollapsedState();
    }

    /**
     * While the sidebar is collapsed, hovering a nav button pops up a full-size clone of that
     * button (icon + label, same styling and click behavior) anchored over it, so the hovered
     * item reads exactly as it does expanded while every other item stays icon-only.
     */
    private void installNavFlyout(final Button button, final ModuleItem module) {
        button.setOnMouseEntered(event -> {
            if (sidebarCollapsed) {
                showNavFlyout(button, module);
            }
        });
        button.setOnMouseExited(event -> scheduleFlyoutHide(event.getScreenX(), event.getScreenY(), button));
    }

    private void showNavFlyout(final Button anchor, final ModuleItem module) {
        navFlyoutHideDelay.stop();
        if (navFlyout != null && navFlyout.isShowing() && anchor == navFlyoutAnchor) {
            return;
        }
        hideNavFlyout();

        final Button flyoutButton = new Button(module.title(), Icons.of(module.icon(), "nav-item-icon"));
        flyoutButton.setGraphicTextGap(10);
        flyoutButton.getStyleClass().add("nav-item");
        if (anchor.getStyleClass().contains("nav-item-active")) {
            flyoutButton.getStyleClass().add("nav-item-active");
        }
        flyoutButton.setMinHeight(48);
        flyoutButton.setPrefWidth(SIDEBAR_EXPANDED_WIDTH - 32);
        flyoutButton.setOnAction(event -> {
            hideNavFlyout();
            anchor.fire();
        });
        flyoutButton.setOnMouseEntered(event -> navFlyoutHideDelay.stop());
        flyoutButton.setOnMouseExited(event -> scheduleFlyoutHide(event.getScreenX(), event.getScreenY(), anchor));

        final StackPane flyoutHost = new StackPane(flyoutButton);
        flyoutHost.getStyleClass().addAll("app-shell", "sidebar", "nav-item-flyout-host");
        if (themeManager.current() == ThemeManager.Theme.DARK) {
            flyoutHost.getStyleClass().add("theme-dark");
        }
        flyoutHost.getStylesheets().add(getClass().getResource("/com/bizco/client/application.css").toExternalForm());

        navFlyout = new Popup();
        navFlyoutAnchor = anchor;
        navFlyout.setAutoFix(false);
        navFlyout.getContent().add(flyoutHost);
        final Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        navFlyout.show(anchor, anchorBounds.getMinX(), anchorBounds.getMinY());
    }

    /**
     * The popup is anchored directly over the real button, so showing it makes the OS route the
     * still-stationary cursor to the popup's window - which fires a MOUSE_EXITED on the anchor
     * with no actual pointer movement involved, and the popup's own content will not reliably
     * have received a matching MOUSE_ENTERED yet to cancel the hide. Deciding purely from "which
     * node last fired an exit" therefore closes the popup, which uncovers the anchor and
     * re-triggers hover - a blink loop. Checking the exit event's own screen coordinates against
     * both regions sidesteps that: only actually leaving both areas schedules a hide.
     */
    private void scheduleFlyoutHide(final double screenX, final double screenY, final Node anchor) {
        if (isPointerOverNavHoverArea(screenX, screenY, anchor)) {
            return;
        }
        navFlyoutHideDelay.playFromStart();
    }

    private boolean isPointerOverNavHoverArea(final double screenX, final double screenY, final Node anchor) {
        if (navFlyout != null && navFlyout.isShowing()
                && screenX >= navFlyout.getX() && screenX <= navFlyout.getX() + navFlyout.getWidth()
                && screenY >= navFlyout.getY() && screenY <= navFlyout.getY() + navFlyout.getHeight()) {
            return true;
        }
        final Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        return anchorBounds != null && anchorBounds.contains(screenX, screenY);
    }

    private void hideNavFlyout() {
        if (navFlyout != null) {
            navFlyout.hide();
            navFlyout = null;
            navFlyoutAnchor = null;
        }
    }

    private void applySidebarCollapsedState() {
        sidebarToggleButton.setGraphic(Icons.of(sidebarCollapsed ? FontAwesomeSolid.CHEVRON_RIGHT : FontAwesomeSolid.CHEVRON_LEFT));
        sidebarToggleButton.setTooltip(new Tooltip(sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"));
        sidebar.getStyleClass().remove("sidebar-collapsed");
        if (sidebarCollapsed) {
            sidebar.getStyleClass().add("sidebar-collapsed");
        }
        navButtons.forEach(button -> {
            if (sidebarCollapsed) {
                button.setText(null);
                button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            } else {
                button.setText((String) button.getUserData());
                button.setContentDisplay(ContentDisplay.LEFT);
            }
        });
    }

    private void setActiveNavButton(final List<Button> navButtons, final Button active) {
        navButtons.forEach(button -> button.getStyleClass().remove("nav-item-active"));
        active.getStyleClass().add("nav-item-active");
    }

    private List<ModuleItem> navigation() {
        final List<ModuleItem> modules = new ArrayList<>();
        modules.add(new ModuleItem("Dashboard", FontAwesomeSolid.TACHOMETER_ALT, null, this::createDashboard));
        modules.add(new ModuleItem("Customers", FontAwesomeSolid.USERS, "customer.read",
                () -> new CustomerManagementView(customerApiClient,
                session.hasPermission("customer.create"), session.hasPermission("customer.update"),
                session.hasPermission("customer.anonymize"), session.hasPermission("customer.credit.read")).createView()));
        modules.add(new ModuleItem("Master Data", FontAwesomeSolid.BOXES, "product.read",
                () -> new MasterDataManagementView(catalogApiClient,
                supplierApiClient, session.hasPermission("product.create"), session.hasPermission("product.update"),
                session.hasPermission("product.delete"), session.hasPermission("product.view_cost"),
                session.hasPermission("product.category.create") || session.hasPermission("product.category.update"),
                session.hasPermission("service.create") || session.hasPermission("service.update"),
                session.hasPermission("supplier.create"), session.hasPermission("supplier.update"),
                session.hasPermission("supplier.deactivate")).createView()));
        modules.add(new ModuleItem("POS", FontAwesomeSolid.CASH_REGISTER, "invoice.create", () -> placeholder("POS")));
        modules.add(new ModuleItem("Scheduling", FontAwesomeSolid.CALENDAR_ALT, "appointment.read", () -> placeholder("Scheduling")));
        modules.add(new ModuleItem("Reports", FontAwesomeSolid.CHART_LINE, "audit.read", () -> placeholder("Reports")));
        modules.add(new ModuleItem("User Management", FontAwesomeSolid.USER_COG, "user.read",
                () -> new UserManagementView(identityApiClient, session.hasPermission("user.update")).createView()));
        modules.add(new ModuleItem("Role Management", FontAwesomeSolid.USER_SHIELD, "role.read",
                () -> new RoleManagementView(identityApiClient, session.hasPermission("role.update")).createView()));
        modules.add(new ModuleItem("Business Profile", FontAwesomeSolid.BUILDING, "system.config.read",
                () -> new BusinessProfileView(businessProfileApiClient,
                        session.hasPermission("system.config"), () -> shell.setCenter(createDashboard()))
                        .createView(false)));
        modules.add(new ModuleItem("Tax Configuration", FontAwesomeSolid.PERCENT, "system.config.read",
                () -> new TaxConfigurationView(taxConfigurationApiClient,
                        session.hasPermission("system.config")).createView()));
        return modules;
    }

    private Parent createDashboard() {
        final GridPane grid = new GridPane();
        grid.getStyleClass().add("dashboard-grid");
        grid.setPadding(new Insets(24));
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(kpi(FontAwesomeSolid.MONEY_BILL_WAVE, "Today Sales", "Rs. 0.00"), 0, 0);
        grid.add(kpi(FontAwesomeSolid.FILE_INVOICE, "Open Invoices", "0"), 1, 0);
        grid.add(kpi(FontAwesomeSolid.CALENDAR_CHECK, "Appointments", "0"), 2, 0);
        grid.add(kpi(FontAwesomeSolid.WAREHOUSE, "Low Stock", "0"), 3, 0);
        grid.add(placeholder("Recent Activity"), 0, 1, 4, 1);
        return grid;
    }

    private VBox kpi(final Ikon icon, final String title, final String value) {
        final Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-title");
        final HBox titleRow = new HBox(8, Icons.of(icon, "kpi-icon"), titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        final Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("kpi-value");
        final VBox box = new VBox(8, titleRow, valueLabel);
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
                shell.setCenter(new OnboardingWizardView(businessProfileApiClient, taxConfigurationApiClient,
                        () -> shell.setCenter(createDashboard())).createView());
            }
        }));
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record ModuleItem(String title, Ikon icon, String requiredPermission, ModuleViewFactory viewFactory) {
        boolean visible(final ClientSession session) {
            return requiredPermission == null || session.hasPermission(requiredPermission);
        }
    }

    @FunctionalInterface
    private interface ModuleViewFactory {
        Parent get();
    }
}

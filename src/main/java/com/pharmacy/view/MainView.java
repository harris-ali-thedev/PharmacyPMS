package com.pharmacy.view;

import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class MainView {

    private static MainView instance;

    private static final double SIDEBAR_WIDTH = 240;
    private static final DateTimeFormatter TOPBAR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy  HH:mm");

    private BorderPane root;
    private Stage stage;
    private VBox sidebar;
    private boolean sidebarVisible = true;
    private Button activeNavButton;
    private String currentViewKey = "dashboard";

    /** Opens the navigation sidebar if it is currently hidden. */
    public static void ensureSidebarOpen() {
        if (instance != null) {
            instance.openSidebarIfClosed();
        }
    }

    public void show(Stage stage) {
        instance = this;
        this.stage = stage;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setTitle("MediCare PMS — " + SessionManager.getInstance().getCurrentUser().getFullName());
        stage.setMinWidth(1024);
        stage.setMinHeight(650);

        root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F4F6;");
        root.setLeft(buildSidebar());
        root.setTop(buildTopBar());

        ViewManager.getInstance().initialize(root, stage);
        navigate("dashboard", null);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setResizable(true);
        stage.setX(screenBounds.getMinX());
        stage.setY(screenBounds.getMinY());
        stage.setWidth(screenBounds.getWidth());
        stage.setHeight(screenBounds.getHeight());
        stage.show();
    }

    private ScrollPane buildSidebar() {
        sidebar = new VBox(0);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMinWidth(SIDEBAR_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        sidebar.setStyle("-fx-background-color: #0F172A;");

        VBox content = new VBox(0);
        content.setFillWidth(true);
        content.getChildren().add(buildSidebarHeader());

        addSection(content, "MAIN");
        addNavItem(content, "Dashboard", "dashboard");
        addNavItem(content, "Point of Sale / Billing", "pos");

        addSection(content, "INVENTORY");
        addNavItem(content, "Inventory view", "inventory");
        addNavItem(content, "Add Stock", "addstock");
        addNavItem(content, "Expiry Alerts", "expiry");
        addNavItem(content, "Reorder Queue", "reorder");

        if (!SessionManager.getInstance().isCashier()) {
            addSection(content, "OPERATIONS");
            addNavItem(content, "Patients", "patients");
            addNavItem(content, "Prescriptions", "prescriptions");
//            addNavItem(content, "Billing", "billing");
            addNavItem(content, "Insurance Claims", "insurance");

            addSection(content, "SUPPLY");
            addNavItem(content, "Suppliers", "suppliers");
            addNavItem(content, "Purchase Orders", "purchaseorders");
        }

        if (SessionManager.getInstance().isAdmin()) {
            addSection(content, "REPORTS");
            addNavItem(content, "Reports Dashboard", "reports");
//            addNavItem(content, "Sales Report", "salesreport");
//            addNavItem(content, "Profit Margin", "profit");
//            addNavItem(content, "Expiry Report", "expiryreport");

            addSection(content, "ADMIN");
            addNavItem(content, "User Management", "users");
            addNavItem(content, "Audit Log", "auditlog");
            addNavItem(content, "Backup & Restore", "backup");
        }

        addSection(content, "PREFERENCES");
        addNavItem(content, "Settings", "settings");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        content.getChildren().add(spacer);
        content.getChildren().add(buildSidebarFooter());

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefWidth(SIDEBAR_WIDTH);
        scrollPane.setMinWidth(SIDEBAR_WIDTH);
        scrollPane.setMaxWidth(SIDEBAR_WIDTH);
        scrollPane.setStyle("-fx-background: #000000; -fx-background-color: #000000;");
        return scrollPane;
    }

    private VBox buildSidebarHeader() {
        VBox header = new VBox(4);
        header.setPadding(new Insets(20, 16, 18, 16));
        header.setStyle("-fx-border-color: transparent transparent #1E293B transparent; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("MediCare PMS");
        logo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label user = new Label(SessionManager.getInstance().getCurrentUser().getFullName());
        user.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1;");

        Label role = new Label(SessionManager.getInstance().getCurrentUser().getRole());
        role.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        header.getChildren().addAll(logo, user, role);
        return header;
    }

    private HBox buildSidebarFooter() {
        Button logoutBtn = new Button("Logout");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #FCA5A5; "
                + "-fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 12 16 12 16; -fx-alignment: CENTER_LEFT;");
        logoutBtn.setOnAction(e -> handleLogout());

        HBox footer = new HBox(logoutBtn);
        footer.setPadding(new Insets(8, 0, 12, 0));
        HBox.setHgrow(logoutBtn, Priority.ALWAYS);
        return footer;
    }

    private void addSection(VBox parent, String title) {
        Label section = new Label(title);
        section.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B; "
                + "-fx-padding: 14 16 6 16; -fx-font-weight: bold;");
        parent.getChildren().add(section);
    }

    private void addNavItem(VBox parent, String text, String viewKey) {
        Button button = createNavButton(text);
        if (viewKey.equalsIgnoreCase(currentViewKey)) {
            activeNavButton = button;
            button.setStyle(navButtonStyle(true));
        }
        button.setOnAction(e -> navigate(viewKey, button));
        parent.getChildren().add(button);
    }

    private Button createNavButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(navButtonStyle(false));

        button.setOnMouseEntered(e -> {
            if (button != activeNavButton) {
                button.setStyle(navHoverStyle());
            }
        });
        button.setOnMouseExited(e -> {
            if (button != activeNavButton) {
                button.setStyle(navButtonStyle(false));
            }
        });
        return button;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 20, 10, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; "
                + "-fx-border-color: transparent transparent #E5E7EB transparent; -fx-border-width: 0 0 1 0;");

        Button toggleBtn = new Button("☰");
        toggleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #374151; "
                + "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 2 8 2 8; -fx-background-radius: 6;");
        toggleBtn.setOnMouseEntered(e -> toggleBtn.setStyle("-fx-background-color: #F3F4F6; -fx-text-fill: #111827; "
                + "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 2 8 2 8; -fx-background-radius: 6;"));
        toggleBtn.setOnMouseExited(e -> toggleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #374151; "
                + "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 2 8 2 8; -fx-background-radius: 6;"));
        toggleBtn.setOnAction(e -> toggleSidebar());

        VBox titleBox = new VBox(2);
        Label greeting = new Label("Pharmacy Management Workspace");
        greeting.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label sessionInfo = new Label("Session active for " + SessionManager.getInstance().getCurrentUser().getRole());
        sessionInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");
        titleBox.getChildren().addAll(greeting, sessionInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label time = new Label(LocalDateTime.now().format(TOPBAR_TIME_FORMAT));
        time.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        bar.getChildren().addAll(toggleBtn, titleBox, spacer, time);
        return bar;
    }

    private void openSidebarIfClosed() {
        if (!sidebarVisible) {
            toggleSidebar();
        }
    }

    private void toggleSidebar() {
        if (sidebarVisible) {
            TranslateTransition hide = new TranslateTransition(Duration.millis(200), sidebar);
            hide.setFromX(0);
            hide.setToX(-SIDEBAR_WIDTH);
            hide.setOnFinished(e -> {
                root.setLeft(null);
                sidebar.setTranslateX(0);
            });
            hide.play();
        } else {
            root.setLeft(buildSidebar());
            sidebar.setTranslateX(-SIDEBAR_WIDTH);
            TranslateTransition show = new TranslateTransition(Duration.millis(200), sidebar);
            show.setFromX(-SIDEBAR_WIDTH);
            show.setToX(0);
            show.play();
        }
        sidebarVisible = !sidebarVisible;
    }

    private void navigate(String viewKey, Button button) {
        currentViewKey = viewKey;
        if (button != null) {
            setActiveNavButton(button);
        }
        ViewManager.getInstance().navigate(viewKey);
    }

    private void setActiveNavButton(Button button) {
        if (activeNavButton != null) {
            activeNavButton.setStyle(navButtonStyle(false));
        }
        activeNavButton = button;
        activeNavButton.setStyle(navButtonStyle(true));
    }

    private String navButtonStyle(boolean active) {
        if (active) {
            return "-fx-background-color: #525252; -fx-text-fill: white; "
                    + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; "
                    + "-fx-padding: 10 16 10 16; -fx-alignment: CENTER_LEFT; "
                    + "-fx-background-radius: 8; -fx-translate-x: 0;";
        }
        return "-fx-background-color: transparent; -fx-text-fill: #CBD5E1; "
                + "-fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 10 16 10 16; "
                + "-fx-alignment: CENTER_LEFT; -fx-background-radius: 8;";
    }

    private String navHoverStyle() {
        return "-fx-background-color: #1E293B; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 10 16 10 16; "
                + "-fx-alignment: CENTER_LEFT; -fx-background-radius: 8;";
    }

    private void handleLogout() {
        AuditLogger.log("LOGOUT", "users",
                SessionManager.getInstance().getCurrentUser().getUserId(), "User logged out");
        SessionManager.getInstance().clearSession();
        instance = null;
        new LoginView().show(stage);
    }
}

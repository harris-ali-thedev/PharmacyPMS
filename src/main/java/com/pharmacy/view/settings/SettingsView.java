package com.pharmacy.view.settings;

import com.pharmacy.util.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class SettingsView {

    public Node getView() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #F3F4F6;");

        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Configure pharmacy preferences, session defaults, and security options.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        VBox headerText = new VBox(4, title, subtitle);
        header.getChildren().add(headerText);

        GridPane overview = new GridPane();
        overview.setHgap(14);
        overview.setVgap(14);
        overview.add(buildStatCard("Signed in as",
                SessionManager.getInstance().getCurrentUser().getFullName(),
                SessionManager.getInstance().getCurrentUser().getRole(),
                "#185FA5"), 0, 0);
        overview.add(buildStatCard("Session status", "Active", "Auto-refresh enabled", "#059669"), 1, 0);
        overview.add(buildStatCard("Operational mode", "Retail Pharmacy", "Counter and inventory workflow", "#7C3AED"), 2, 0);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(33.33);
            overview.getColumnConstraints().add(column);
        }

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(16);
        settingsGrid.setVgap(16);
        settingsGrid.add(buildPharmacyPreferencesCard(), 0, 0);
        settingsGrid.add(buildSecurityCard(), 1, 0);
        settingsGrid.add(buildNotificationsCard(), 0, 1);
        settingsGrid.add(buildSystemInfoCard(), 1, 1);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setHgrow(Priority.ALWAYS);
        settingsGrid.getColumnConstraints().addAll(left, right);

        content.getChildren().addAll(header, overview, settingsGrid);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #F3F4F6;");
        return scrollPane;
    }

    private VBox buildStatCard(String label, String value, String detail, String accentColor) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10;");

        Label heading = new Label(label);
        heading.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        Label mainValue = new Label(value);
        mainValue.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        Label supporting = new Label(detail);
        supporting.setStyle("-fx-font-size: 12px; -fx-text-fill: #4B5563;");
        supporting.setWrapText(true);

        card.getChildren().addAll(heading, mainValue, supporting);
        return card;
    }

    private VBox buildPharmacyPreferencesCard() {
        VBox card = createCard("Pharmacy Preferences", "Store-wide defaults for billing and inventory.");

        TextField pharmacyName = styledTextField("MediCare PMS");
        TextField currency = styledTextField("PKR");
        ComboBox<String> taxMode = styledComboBox("VAT Inclusive", "VAT Exclusive", "No Tax");
        taxMode.setValue("VAT Inclusive");
        ComboBox<String> receiptSize = styledComboBox("Thermal 80mm", "A4", "Thermal 58mm");
        receiptSize.setValue("Thermal 80mm");

        GridPane form = createFormGrid();
        form.add(formLabel("Pharmacy Name"), 0, 0);
        form.add(pharmacyName, 1, 0);
        form.add(formLabel("Currency"), 0, 1);
        form.add(currency, 1, 1);
        form.add(formLabel("Tax Mode"), 0, 2);
        form.add(taxMode, 1, 2);
        form.add(formLabel("Receipt Layout"), 0, 3);
        form.add(receiptSize, 1, 3);

        CheckBox lowStockWarnings = styledCheckBox("Show low-stock alerts on dashboard", true);
        CheckBox batchTracking = styledCheckBox("Require batch and expiry tracking", true);

        HBox actions = actionBar("Save Preferences", "Defaults staged locally for future backend integration.");
        card.getChildren().addAll(form, lowStockWarnings, batchTracking, new Separator(), actions);
        return card;
    }

    private VBox buildSecurityCard() {
        VBox card = createCard("Security", "Session and password-related controls.");

        PasswordField currentPassword = styledPasswordField("Current password");
        PasswordField newPassword = styledPasswordField("New password");
        PasswordField confirmPassword = styledPasswordField("Confirm new password");

        ComboBox<String> sessionTimeout = styledComboBox("15 minutes", "30 minutes", "45 minutes", "60 minutes");
        sessionTimeout.setValue("30 minutes");

        GridPane form = createFormGrid();
        form.add(formLabel("Current Password"), 0, 0);
        form.add(currentPassword, 1, 0);
        form.add(formLabel("New Password"), 0, 1);
        form.add(newPassword, 1, 1);
        form.add(formLabel("Confirm Password"), 0, 2);
        form.add(confirmPassword, 1, 2);
        form.add(formLabel("Session Timeout"), 0, 3);
        form.add(sessionTimeout, 1, 3);

        CheckBox requireManagerOverride = styledCheckBox("Require manager override for voided sales", true);
        CheckBox autoLock = styledCheckBox("Lock screen after inactivity", true);

        HBox actions = actionBar("Update Security", "Password actions are UI-only until service wiring is added.");
        card.getChildren().addAll(form, requireManagerOverride, autoLock, new Separator(), actions);
        return card;
    }

    private VBox buildNotificationsCard() {
        VBox card = createCard("Notifications", "Operational reminders for pharmacy staff.");

        CheckBox expiryAlerts = styledCheckBox("Notify about medicines nearing expiry", true);
        CheckBox reorderAlerts = styledCheckBox("Notify when items reach reorder level", true);
        CheckBox backupAlerts = styledCheckBox("Remind admin about scheduled backups", true);
        CheckBox shiftSummary = styledCheckBox("Send end-of-shift summary prompt", false);

        ComboBox<String> alertChannel = styledComboBox("In-app alerts", "Email summary", "In-app + Email");
        alertChannel.setValue("In-app alerts");

        BorderPane row = new BorderPane();
        row.setLeft(formLabel("Alert Delivery"));
        row.setCenter(alertChannel);
        BorderPane.setMargin(alertChannel, new Insets(0, 0, 0, 12));

        HBox actions = actionBar("Save Notifications", "Notification preferences updated for this session view.");
        card.getChildren().addAll(expiryAlerts, reorderAlerts, backupAlerts, shiftSummary, row, new Separator(), actions);
        return card;
    }

    private VBox buildSystemInfoCard() {
        VBox card = createCard("System Info", "Current environment and navigation guidance.");

        VBox infoList = new VBox(10,
                infoRow("Application", "PharmacyPMS desktop client"),
                infoRow("View Routing", "Managed by ViewManager singleton"),
                infoRow("Current Module", "Settings"),
                infoRow("User Role", SessionManager.getInstance().getCurrentUser().getRole()),
                infoRow("Backend Status", "Settings persistence not implemented yet"));

        Label note = new Label("This screen is fully navigable and safe to load. Persisting these values should be connected to a DAO or configuration service next.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 12px; -fx-text-fill: #4B5563;");

        card.getChildren().addAll(infoList, new Separator(), note);
        return card;
    }

    private VBox createCard(String title, String subtitle) {
        VBox card = new VBox(14);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10;");

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label description = new Label(subtitle);
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        card.getChildren().addAll(heading, description);
        return card;
    }

    private GridPane createFormGrid() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setPercentWidth(34);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setPercentWidth(66);
        fieldColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelColumn, fieldColumn);
        return form;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        return label;
    }

    private TextField styledTextField(String value) {
        TextField field = new TextField(value);
        field.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: #D1D5DB; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 10;");
        return field;
    }

    private PasswordField styledPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: #D1D5DB; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 10;");
        return field;
    }

    private ComboBox<String> styledComboBox(String... values) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(values);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: #D1D5DB; "
                + "-fx-border-radius: 8; -fx-background-radius: 8;");
        return comboBox;
    }

    private CheckBox styledCheckBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        return checkBox;
    }

    private HBox actionBar(String buttonText, String helperText) {
        Button actionButton = new Button(buttonText);
        actionButton.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 14;");

        Label helper = new Label(helperText);
        helper.setWrapText(true);
        helper.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, helper, spacer, actionButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }

    private HBox infoRow(String label, String value) {
        Label left = new Label(label);
        left.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        left.setMinWidth(110);

        Label right = new Label(value);
        right.setWrapText(true);
        right.setStyle("-fx-font-size: 12px; -fx-text-fill: #111827; -fx-font-weight: bold;");

        HBox row = new HBox(10, left, right);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

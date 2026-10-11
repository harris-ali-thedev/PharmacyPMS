package com.pharmacy.view;

import com.pharmacy.controller.LoginController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.scene.paint.Color;

import java.awt.*;

public class LoginView {

    // ── Design tokens ────────────────────────────────────────
    private static final String FONT_STACK    = "-fx-font-family: 'DM Sans','SF Pro Text','Segoe UI Variable','Segoe UI',sans-serif;";

    private static final String C_PRIMARY     = "#1A6DB5";
    private static final String C_PRIMARY_DK  = "#1558A0";
    private static final String C_BG          = "#F0F4F8";
    private static final String C_WHITE       = "#FFFFFF";
    private static final String C_BORDER      = "#DDE3EC";
    private static final String C_TEXT_DARK   = "#0F172A";
    private static final String C_TEXT_MED    = "#475569";
    private static final String C_TEXT_LIGHT  = "#94A3B8";
    private static final String C_BRAND_BG    = "linear-gradient(from 0% 0% to 100% 100%, #112240 0%, #0D1829 50%, #0A3660 100%)";
    private static final String C_BTN_GRAD    = "linear-gradient(from 0% 0% to 0% 100%, #2271B8, #1558A0)";
    private static final String C_BTN_HOVER   = "linear-gradient(from 0% 0% to 0% 100%, #1A69AA, #134E94)";

    // ── Entry point ──────────────────────────────────────────

    public void show(Stage stage) {
        stage.setTitle("My Pharmacy System — Login");
        stage.setMinWidth(880);
        stage.setMinHeight(520);
        stage.setWidth(960);
        stage.setHeight(580);
        stage.setResizable(true);

        VBox brandPanel = buildBrandPanel();
        brandPanel.setPrefWidth(420);
        brandPanel.setMinWidth(340);
        HBox.setHgrow(brandPanel, Priority.SOMETIMES);

        TextField     usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label         errorLabel    = buildErrorLabel();
        Button        loginBtn      = buildSignInButton();

        usernameField.setPromptText("Enter your username");
        passwordField.setPromptText("Enter your password");
        usernameField.setPrefHeight(40);
        passwordField.setPrefHeight(40);

        VBox loginCard = buildLoginCard(usernameField, passwordField, errorLabel, loginBtn);

        VBox formPanel = new VBox(loginCard);
        formPanel.setAlignment(Pos.CENTER);
        formPanel.setStyle(
                FONT_STACK +
                        "-fx-background-color: " + C_BG + ";" +
                        "-fx-padding: 40 44;"
        );
        HBox.setHgrow(formPanel, Priority.ALWAYS);

        HBox root = new HBox(brandPanel, formPanel);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: " + C_BG + ";");

        LoginController controller = new LoginController(stage, usernameField, passwordField, errorLabel);
        loginBtn.setOnAction(e -> controller.handleLogin());
        passwordField.setOnAction(e -> controller.handleLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
        usernameField.requestFocus();
    }

    // ── Brand panel (left) ───────────────────────────────────

    private VBox buildBrandPanel() {
        // Logo circle
        StackPane logoCircle = new StackPane();
        logoCircle.setStyle("-fx-background-color: rgba(255,255,255,0.85);" +
                        "-fx-background-radius: 50;" +
                        "-fx-border-color: rgba(255,255,255,0.16);" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 50;" +
                        "-fx-min-width: 68; -fx-min-height: 68;" +
                        "-fx-max-width: 68; -fx-max-height: 68;");
        Label logoIcon = new Label("💊");
        logoIcon.setStyle("-fx-font-size: 34px;");
//        logoIcon.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        logoCircle.getChildren().add(logoIcon);

        // Brand name
        Label brandTitle = new Label("My Pharmacy PMS");
        brandTitle.setStyle(FONT_STACK + "-fx-font-size: 28px;" +
                        "-fx-font-weight: bold;" + "-fx-text-fill: white;");

        // Tagline
        Label tagline = new Label("Complete pharmacy management — inventory, sales, prescriptions, and reports in one place.");
        tagline.setStyle(FONT_STACK + "-fx-font-size: 13px;" +
                        "-fx-text-fill: rgba(255,255,255,0.80);" + "-fx-line-spacing: 2;");
        tagline.setWrapText(true);
        tagline.setMaxWidth(340);
        tagline.setTextAlignment(TextAlignment.LEFT);

        // Feature cards
        VBox features = new VBox(10,
                featureRow("📦", "Inventory",    "Track stock, expiry dates, and reorder levels"),
                featureRow("🛒", "Point of Sale","Fast checkout with prescriptions and discounts"),
                featureRow("📊", "Reports",      "Sales, profit margins, and audit-ready exports")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label caption = new Label("© " + java.time.Year.now().getValue() + " · Secure staff access");
        caption.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: rgba(255,255,255,0.50);"
        );

        VBox panel = new VBox(20, logoCircle, brandTitle, tagline, features, spacer, caption);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setStyle(
                FONT_STACK +
                        "-fx-background-color: " + C_BRAND_BG + ";" +
                        "-fx-padding: 44 40 44 44;"
        );
        return panel;
    }

    private HBox featureRow(String icon, String title, String description) {
        Label iconLbl = new Label(icon);
//        iconLbl.setStyle("-fx-font-size: 22px;");
        iconLbl.setStyle("-fx-font-size: 22px; -fx-text-fill: rgba(255,255,255,0.8);");

        Label titleLbl = new Label(title);
        titleLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;"
        );

        Label descLbl = new Label(description);
        descLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: rgba(255,255,255,0.65);"
        );
        descLbl.setWrapText(true);

        VBox text = new VBox(2, titleLbl, descLbl);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(12, iconLbl, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05);" +
                        "-fx-background-radius: 9;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 9;" +
                        "-fx-padding: 12;"
        );
        return row;
    }

    // ── Login card (right) ───────────────────────────────────

    private VBox buildLoginCard(TextField usernameField, PasswordField passwordField,
                                Label errorLabel, Button loginBtn) {
        Label welcome = new Label("Welcome back");
        welcome.setStyle(
                FONT_STACK +
                        "-fx-font-size: 22px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_DARK + ";"
        );

        Label subtitle = new Label("Sign in to continue to your dashboard");
        subtitle.setStyle(
                FONT_STACK +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );

        VBox usernameGroup = labeledInput("Username", "👤", usernameField);
        VBox passwordGroup = labeledInput("Password", "🔒", passwordField);

        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);

        Label footer = new Label("Pharmacy Management System · v1.0");
        footer.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );
        footer.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, welcome, subtitle, usernameGroup, passwordGroup, errorLabel, loginBtn, footer);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                FONT_STACK +
                        "-fx-background-color: " + C_WHITE + ";" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: " + C_BORDER + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.09), 28, 0, 0, 8);" +
                        "-fx-padding: 36;" +
                        "-fx-max-width: 400;"
        );
        return card;
    }

    private VBox labeledInput(String labelText, String icon, Control field) {
        Label label = new Label(labelText);
        label.setStyle(
                FONT_STACK +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_MED + ";"
        );

        Label iconLbl = new Label(icon);
        iconLbl.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-min-width: 28;" +
                        "-fx-alignment: center;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );

        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        stripFieldChrome(field);

        HBox inputRow = new HBox(6, iconLbl, field);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        // Normal state style
        String normalStyle =
                "-fx-background-color: #F8FAFC;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + C_BORDER + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-padding: 0 10 0 6;";

        String focusedStyle =
                "-fx-background-color: #FAFCFF;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + C_PRIMARY + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-padding: 0 10 0 6;" +
                        "-fx-effect: dropshadow(gaussian, rgba(26,109,181,0.12), 6, 0, 0, 0);";

        inputRow.setStyle(normalStyle);

        field.focusedProperty().addListener((obs, wasFocused, focused) ->
                inputRow.setStyle(focused ? focusedStyle : normalStyle));

        VBox group = new VBox(6, label, inputRow);
        return group;
    }

    /** Fields sit inside a styled row; strip native chrome so the row provides all chrome. */
    private void stripFieldChrome(Control field) {
        String inner =
                FONT_STACK +
                        "-fx-background-color: transparent;" +
                        "-fx-border-width: 0;" +
                        "-fx-background-radius: 0;" +
                        "-fx-border-radius: 0;" +
                        "-fx-padding: 8 4 8 4;" +
                        "-fx-font-size: 13px;";
        field.setStyle(inner);
        if (field instanceof TextField tf) {
            tf.focusedProperty().addListener((obs, old, focused) -> tf.setStyle(inner));
        } else if (field instanceof PasswordField pf) {
            pf.focusedProperty().addListener((obs, old, focused) -> pf.setStyle(inner));
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private Label buildErrorLabel() {
        Label lbl = new Label();
        lbl.setStyle(
                FONT_STACK +
                        "-fx-text-fill: #E53E3E;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;"
        );
        lbl.setWrapText(true);
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private Button buildSignInButton() {
        Button btn = new Button("Sign In  →");
        String normalStyle =
                FONT_STACK +
                        "-fx-background-color: " + C_BTN_GRAD + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 20;" +
                        "-fx-cursor: hand;";
        String hoverStyle =
                FONT_STACK +
                        "-fx-background-color: " + C_BTN_HOVER + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 20;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(26,109,181,0.28), 10, 0, 0, 4);";
        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }
}
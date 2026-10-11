package com.pharmacy.view;

import com.pharmacy.util.AppConstants;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.logging.Logger;

/**
 * AppUIManager — centralised theming, styling constants, and reusable UI factories.
 *
 * All colours, fonts, and spacing are defined here so that a theme change only
 * requires editing this one class.
 */
public class AppUIManager {

    private static final Logger LOG = Logger.getLogger(AppUIManager.class.getName());
    private static volatile AppUIManager instance;

    // ── Colour Palette ────────────────────────────────────────
    public static final String COLOR_PRIMARY      = "#1E3A5F";   // Navy blue
    public static final String COLOR_PRIMARY_LIGHT = "#2A5298";
    public static final String COLOR_ACCENT       = "#0EA5E9";   // Sky blue
    public static final String COLOR_SUCCESS      = "#16A34A";
    public static final String COLOR_WARNING      = "#D97706";
    public static final String COLOR_DANGER       = "#DC2626";
    public static final String COLOR_INFO         = "#0284C7";
    public static final String COLOR_BG           = "#F1F5F9";
    public static final String COLOR_CARD_BG      = "#FFFFFF";
    public static final String COLOR_BORDER       = "#E2E8F0";
    public static final String COLOR_TEXT_DARK    = "#1E293B";
    public static final String COLOR_TEXT_MEDIUM  = "#475569";
    public static final String COLOR_TEXT_LIGHT   = "#94A3B8";
    public static final String COLOR_SIDEBAR_BG   = "#0F172A";
    public static final String COLOR_SIDEBAR_HOVER = "#1E293B";
    public static final String COLOR_SIDEBAR_ACTIVE = "#1E3A5F";

    // ── Typography ────────────────────────────────────────────
    public static final String FONT_NORMAL = "-fx-font-family: 'Segoe UI', Arial, sans-serif;";

    private AppUIManager() {}

    public static AppUIManager getInstance() {
        if (instance == null) {
            synchronized (AppUIManager.class) {
                if (instance == null) instance = new AppUIManager();
            }
        }
        return instance;
    }

    // ── Button Factories ─────────────────────────────────────

    public static Button primaryButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(btnStyle(COLOR_PRIMARY, "white", "#1A3356"));
        applyHoverEffect(btn, COLOR_PRIMARY, COLOR_PRIMARY_LIGHT, "white");
        return btn;
    }

    public static Button successButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(btnStyle(COLOR_SUCCESS, "white", "#15803D"));
        applyHoverEffect(btn, COLOR_SUCCESS, "#15803D", "white");
        return btn;
    }

    public static Button dangerButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(btnStyle(COLOR_DANGER, "white", "#B91C1C"));
        applyHoverEffect(btn, COLOR_DANGER, "#B91C1C", "white");
        return btn;
    }

    public static Button warningButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(btnStyle(COLOR_WARNING, "white", "#B45309"));
        applyHoverEffect(btn, COLOR_WARNING, "#B45309", "white");
        return btn;
    }

    public static Button outlineButton(String text) {
        Button btn = new Button(text);
        String base = "-fx-background-color: transparent; -fx-text-fill: " + COLOR_PRIMARY +
                "; -fx-border-color: " + COLOR_PRIMARY + "; -fx-border-width: 1; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 16 8 16; " +
                "-fx-font-size: 13px; -fx-cursor: hand;";
        String hover = "-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: white; " +
                "-fx-border-color: " + COLOR_PRIMARY + "; -fx-border-width: 1; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 16 8 16; " +
                "-fx-font-size: 13px; -fx-cursor: hand;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    private static String btnStyle(String bg, String fg, String hover) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-background-radius: 6; -fx-padding: 8 16 8 16; " +
                "-fx-font-size: 13px; -fx-cursor: hand; -fx-font-weight: bold;";
    }

    private static void applyHoverEffect(Button btn, String normalBg, String hoverBg, String fg) {
        String normal = btnStyle(normalBg, fg, hoverBg);
        String hover  = btnStyle(hoverBg,  fg, normalBg);
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(normal));
    }

    // ── Field Factories ──────────────────────────────────────

    public static TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(36);
        tf.setStyle(fieldStyle());
        tf.focusedProperty().addListener((obs, old, focused) ->
                tf.setStyle(focused ? fieldStyleFocused() : fieldStyle()));
        return tf;
    }

    public static PasswordField styledPasswordField(String prompt) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(prompt);
        pf.setPrefHeight(36);
        pf.setStyle(fieldStyle());
        pf.focusedProperty().addListener((obs, old, focused) ->
                pf.setStyle(focused ? fieldStyleFocused() : fieldStyle()));
        return pf;
    }

    public static TextArea styledTextArea(String prompt, int rows) {
        TextArea ta = new TextArea();
        ta.setPromptText(prompt);
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        ta.setStyle(fieldStyle());
        return ta;
    }

    public static ComboBox<String> styledComboBox(String... items) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(items);
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setStyle(fieldStyle());
        return cb;
    }

    public static DatePicker styledDatePicker() {
        DatePicker dp = new DatePicker();
        dp.setMaxWidth(Double.MAX_VALUE);
        dp.setStyle(fieldStyle());
        return dp;
    }

    private static String fieldStyle() {
        return "-fx-background-radius: 6; -fx-border-radius: 6; " +
                "-fx-border-color: " + COLOR_BORDER + "; -fx-border-width: 1; " +
                "-fx-font-size: 13px; -fx-padding: 4 8 4 8;";
    }

    private static String fieldStyleFocused() {
        return "-fx-background-radius: 6; -fx-border-radius: 6; " +
                "-fx-border-color: " + COLOR_ACCENT + "; -fx-border-width: 2; " +
                "-fx-font-size: 13px; -fx-padding: 4 8 4 8;";
    }

    // ── Label Factories ──────────────────────────────────────

    public static Label pageTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEXT_DARK + ";");
        return l;
    }

    public static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEXT_DARK + ";");
        return l;
    }

    public static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEXT_MEDIUM + ";");
        return l;
    }

    public static Label errorLabel() {
        Label l = new Label();
        l.setStyle("-fx-text-fill: " + COLOR_DANGER + "; -fx-font-size: 11px;");
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    public static Label badge(String text, String bgColor) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: white; " +
                "-fx-background-radius: 20; -fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    // ── Card Factory ──────────────────────────────────────────

    public static VBox card() {
        VBox c = new VBox(12);
        c.setPadding(new Insets(20));
        c.setStyle("-fx-background-color: " + COLOR_CARD_BG + "; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: " + COLOR_BORDER + "; -fx-border-width: 1; " +
                "-fx-border-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);");
        return c;
    }

    /** Stat card for dashboard: icon + value + label */
    public static VBox statCard(String icon, String value, String label, String accent) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle("-fx-background-color: " + COLOR_CARD_BG + "; -fx-background-radius: 12; " +
                "-fx-border-color: " + COLOR_BORDER + "; -fx-border-width: 1; " +
                "-fx-border-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 3);");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 28px;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");

        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_TEXT_MEDIUM + ";");

        card.getChildren().addAll(iconLabel, valueLabel, nameLabel);
        return card;
    }

    // ── Page wrapper ─────────────────────────────────────────

    public static ScrollPane scrollWrap(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: #C3C3C3; -fx-background: " + COLOR_BG + ";");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    public static VBox pageContainer(String title, String subtitle) {
        VBox container = new VBox(0);
        container.setStyle("-fx-background-color: " + COLOR_BG + ";");

        // Header
        VBox header = new VBox(4);
        header.setPadding(new Insets(24, 28, 16, 28));
        header.setStyle("-fx-background-color: " + COLOR_CARD_BG + "; " +
                "-fx-border-color: transparent transparent " + COLOR_BORDER + " transparent; " +
                "-fx-border-width: 0 0 1 0;");

        Label titleLabel = pageTitle(title);
        header.getChildren().add(titleLabel);

        if (subtitle != null && !subtitle.isEmpty()) {
            Label sub = new Label(subtitle);
            sub.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_TEXT_MEDIUM + ";");
            header.getChildren().add(sub);
        }

        container.getChildren().add(header);
        return container;
    }

    // ── Table styling ─────────────────────────────────────────

    public static void styleTable(javafx.scene.control.TableView<?> table) {
        table.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; " +
                "-fx-border-color: " + COLOR_BORDER + "; -fx-border-width: 1;");
        table.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // ── Separator ────────────────────────────────────────────

    public static Region hDivider() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color: " + COLOR_BORDER + ";");
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.NEVER);
        return r;
    }
}
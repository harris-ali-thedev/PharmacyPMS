package com.pharmacy.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

public final class PharmacyDesignSystem {

    public static final String FONT =
            "-fx-font-family: 'DM Sans','Segoe UI Variable','Segoe UI',Arial,sans-serif;";

    // ── Colors ───────────────────────────────────────────────
    public static final String BG = "#F0F4F8";
    public static final String SURFACE = "#FFFFFF";
    public static final String SURFACE_SOFT = "#F7FAFC";
    public static final String BORDER = "#DDE5EE";
    public static final String BORDER_STRONG = "#C5D3E0";
    public static final String TEXT = "#0F1C2E";
    public static final String MUTED = "#5B6B7C";
    public static final String FAINT = "#8A9AAB";
    public static final String PRIMARY = "#1A6DB5";
    public static final String PRIMARY_SOFT = "#E8F2FB";
    public static final String SIDEBAR = "#0B1929";
    public static final String SIDEBAR_HOVER = "#132337";
    public static final String SUCCESS = "#059669";
    public static final String SUCCESS_BG = "#ECFDF5";
    public static final String WARNING = "#D97706";
    public static final String WARNING_BG = "#FFFBEB";
    public static final String DANGER = "#DC2626";
    public static final String DANGER_BG = "#FEF2F2";
    public static final String INFO = "#0284C7";
    public static final String INFO_BG = "#EFF6FF";

    public static final double SIDEBAR_WIDTH = 248;

    private PharmacyDesignSystem() {
    }

    // ── Page chrome ──────────────────────────────────────────

    public static ScrollPane workspace(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");
        return scroll;
    }


    public static HBox pageHeader(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(FONT + "-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: " + TEXT + ";");

        Label subLabel = new Label(subtitle);
        subLabel.setWrapText(true);
        subLabel.setStyle(FONT + "-fx-font-size: 13px; -fx-text-fill: " + MUTED + ";");

        VBox copy = new VBox(4, titleLabel, subLabel);
        copy.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(copy);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    public static HBox pageHeaderWithActions(String title, String subtitle, Node... actions) {
        HBox header = pageHeader(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.getChildren().addAll(actions);
        header.getChildren().addAll(spacer, actionRow);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ── Surfaces ─────────────────────────────────────────────

    public static VBox card() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        return card;
    }

    public static String cardStyle() {
        return FONT
                + "-fx-background-color: " + SURFACE + ";"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: " + BORDER + ";"
                + "-fx-border-radius: 12;"
                + "-fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,28,46,0.06), 14, 0, 0, 3);";
    }

    public static VBox panel(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(FONT + "-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + TEXT + ";");
        Label subLabel = new Label(subtitle);
        subLabel.setWrapText(true);
        subLabel.setStyle(FONT + "-fx-font-size: 12px; -fx-text-fill: " + FAINT + ";");
        VBox card = card();
        card.getChildren().add(new VBox(3, titleLabel, subLabel));
        return card;
    }

    public static VBox metricCard(String label, String value, String detail, String accent, String iconBg, String iconGlyph) {
        StackPane icon = new StackPane();
        icon.setMinSize(44, 44);
        icon.setMaxSize(44, 44);
        icon.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 11;");
        Label glyph = new Label(iconGlyph);
        glyph.setStyle(FONT + "-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: " + accent + ";");
        icon.getChildren().add(glyph);

        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle(FONT + "-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + FAINT + ";");
        Label valueNode = new Label(value);
        valueNode.setStyle(FONT + "-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: " + accent + ";");
        Label detailNode = new Label(detail);
        detailNode.setStyle(FONT + "-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";");

        VBox copy = new VBox(2, labelNode, valueNode, detailNode);
        HBox row = new HBox(14, icon, copy);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = card();
        card.setPadding(new Insets(16, 18, 16, 18));
        card.getChildren().setAll(row);
        return card;
    }

    // ── Controls ─────────────────────────────────────────────

    public static TextField searchField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(38);
        field.getStyleClass().add("search-field");
        field.setStyle(FONT
                + "-fx-background-color: " + SURFACE + ";"
                + "-fx-background-radius: 8;"
                + "-fx-border-color: " + BORDER_STRONG + ";"
                + "-fx-border-radius: 8;"
                + "-fx-border-width: 1;"
                + "-fx-padding: 7 12;"
                + "-fx-font-size: 13px;");
        field.focusedProperty().addListener((o, was, focused) -> field.setStyle(FONT
                + "-fx-background-color: " + (focused ? "#FAFCFF" : SURFACE) + ";"
                + "-fx-background-radius: 8;"
                + "-fx-border-color: " + (focused ? PRIMARY : BORDER_STRONG) + ";"
                + "-fx-border-radius: 8;"
                + "-fx-border-width: " + (focused ? "1.5;" : "1;")
                + "-fx-padding: 7 12;"
                + "-fx-font-size: 13px;"
                + (focused ? "-fx-effect: dropshadow(gaussian, rgba(26,109,181,0.18), 8, 0, 0, 0);" : "")));
        return field;
    }

    public static Button primaryButton(String text) {
        return button(text, PRIMARY, "white", true);
    }

    public static Button dangerButton(String text) {
        return button(text, DANGER, "white", true);
    }

    public static Button secondaryButton(String text) {
        Button button = button(text, SURFACE, TEXT, false);
        button.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        return button;
    }
    public static Button greenButton(String text) {
        Button button = button(text, SURFACE, TEXT, false);
        button.setStyle("-fx-background-color: #a82222; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        return button;
    }

    public static Button ghostButton(String text) {
        Button button = button(text, "transparent", PRIMARY, false);
        button.setStyle(button.getStyle() + "-fx-border-color: transparent;");
        return button;
    }

    public static Button tableActionButton(String text, String bg) {
        Button button = new Button(text);
        button.setStyle(FONT
                + "-fx-background-color: " + bg + ";"
                + "-fx-text-fill: white;"
                + "-fx-background-radius: 6;"
                + "-fx-font-size: 11px;"
                + "-fx-font-weight: 700;"
                + "-fx-padding: 4 10;"
                + "-fx-cursor: hand;");
        return button;
    }

    private static Button button(String text, String bg, String fg, boolean strong) {
        Button button = new Button(text);
        button.setMinHeight(36);
        button.setStyle(FONT
                + "-fx-background-color: " + bg + ";"
                + "-fx-text-fill: " + fg + ";"
                + "-fx-background-radius: 8;"
                + "-fx-border-radius: 8;"
                + "-fx-font-size: 12px;"
                + "-fx-font-weight: " + (strong ? "800" : "700") + ";"
                + "-fx-padding: 8 14;"
                + "-fx-cursor: hand;");
        return button;
    }

    public static Label badge(String text, String bg, String fg) {
        Label label = new Label(text);
        label.setStyle(FONT
                + "-fx-background-color: " + bg + ";"
                + "-fx-text-fill: " + fg + ";"
                + "-fx-background-radius: 20;"
                + "-fx-padding: 3 9;"
                + "-fx-font-size: 11px;"
                + "-fx-font-weight: 800;");
        return label;
    }

    public static Label successBadge(String text) {
        return badge(text, SUCCESS_BG, SUCCESS);
    }

    public static Label warningBadge(String text) {
        return badge(text, WARNING_BG, WARNING);
    }

    public static Label dangerBadge(String text) {
        return badge(text, DANGER_BG, DANGER);
    }

    public static Label infoBadge(String text) {
        return badge(text, INFO_BG, INFO);
    }

    public static HBox toolbar(Node left, Node... actions) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().add(left);
        bar.getChildren().add(spacer);
        bar.getChildren().addAll(actions);
        return bar;
    }

    public static void styleTable(TableView<?> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("pharmacy-table");
        table.setStyle(FONT
                + "-fx-background-color: " + SURFACE + ";"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: " + BORDER + ";"
                + "-fx-border-radius: 12;"
                + "-fx-border-width: 1;");
    }

    // ── States ───────────────────────────────────────────────

    public static VBox emptyState(String title, String message) {
        Label icon = new Label("∅");
        icon.setStyle(FONT + "-fx-font-size: 28px; -fx-text-fill: " + FAINT + "; -fx-font-weight: 800;");
        Label titleLbl = new Label(title);
        titleLbl.setStyle(FONT + "-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + TEXT + ";");
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setStyle(FONT + "-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";");
        VBox box = new VBox(8, icon, titleLbl, msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36, 20, 36, 20));
        return box;
    }


    public static VBox errorState(String message) {
        Label icon = new Label("!");
        icon.setMinSize(36, 36);
        icon.setMaxSize(36, 36);
        icon.setAlignment(Pos.CENTER);
        icon.setStyle(FONT
                + "-fx-background-color: " + DANGER_BG + ";"
                + "-fx-text-fill: " + DANGER + ";"
                + "-fx-background-radius: 50%;"
                + "-fx-font-size: 16px; -fx-font-weight: 800;");
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setStyle(FONT + "-fx-font-size: 13px; -fx-text-fill: " + DANGER + "; -fx-font-weight: 700;");
        VBox box = new VBox(10, icon, msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(32, 20, 32, 20));
        box.setStyle("-fx-background-color: " + DANGER_BG + "; -fx-background-radius: 10; -fx-border-color: #FECACA; -fx-border-radius: 10;");
        return box;
    }

    public static Label sectionEyebrow(String text) {
        Label label = new Label(text.toUpperCase());
        label.setStyle(FONT
                + "-fx-font-size: 10px;"
                + "-fx-font-weight: 800;"
                + "-fx-text-fill: " + FAINT + ";");
        return label;
    }
}

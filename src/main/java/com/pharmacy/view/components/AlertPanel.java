package com.pharmacy.view.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AlertPanel — Reusable notification panel.
 *
 * Displays a scrollable list of alert messages colour-coded by severity.
 * Can be added to any layout pane.
 */
public class AlertPanel extends VBox {

    public enum Severity { INFO, SUCCESS, WARNING, ERROR }

    private final VBox alertList = new VBox(6);
    private final List<AlertItem> alerts = new ArrayList<>();
    private int maxAlerts = 20;

    public AlertPanel(String title) {
        super(10);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 10; " +
                "-fx-border-color: #E2E8F0; -fx-border-width: 1; -fx-border-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #E2E8F0;");

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(alertList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, divider, scrollPane);
    }

    public void addAlert(String message, Severity severity) {
        addAlert(message, severity, null);
    }

    public void addAlert(String message, Severity severity, String timestamp) {
        AlertItem item = new AlertItem(message, severity, timestamp);
        alerts.add(0, item); // Newest first
        if (alerts.size() > maxAlerts) alerts.remove(alerts.size() - 1);
        refreshList();
    }

    public void clearAlerts() {
        alerts.clear();
        refreshList();
    }

    public void setMaxAlerts(int max) { this.maxAlerts = max; }

    private void refreshList() {
        alertList.getChildren().clear();
        for (AlertItem a : alerts) {
            alertList.getChildren().add(buildAlertRow(a));
        }
        if (alerts.isEmpty()) {
            Label empty = new Label("No notifications");
            empty.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
            alertList.getChildren().add(empty);
        }
    }

    private HBox buildAlertRow(AlertItem item) {
        HBox row = new HBox(10);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.setAlignment(Pos.CENTER_LEFT);

        String[] style = colorForSeverity(item.severity());
        row.setStyle("-fx-background-color: " + style[0] + "; -fx-background-radius: 6;");

        Label icon = new Label(iconForSeverity(item.severity()));
        icon.setStyle("-fx-font-size: 14px;");

        VBox content = new VBox(2);
        Label msg = new Label(item.message());
        msg.setStyle("-fx-font-size: 12px; -fx-text-fill: " + style[1] + "; -fx-wrap-text: true;");
        content.getChildren().add(msg);

        if (item.timestamp() != null) {
            Label ts = new Label(item.timestamp());
            ts.setStyle("-fx-font-size: 10px; -fx-text-fill: " + style[2] + ";");
            content.getChildren().add(ts);
        }

        HBox.setHgrow(content, Priority.ALWAYS);
        row.getChildren().addAll(icon, content);
        return row;
    }

    private String[] colorForSeverity(Severity s) {
        return switch (s) {
            case INFO    -> new String[]{"#EFF6FF", "#1E40AF", "#3B82F6"};
            case SUCCESS -> new String[]{"#F0FDF4", "#166534", "#22C55E"};
            case WARNING -> new String[]{"#FFFBEB", "#92400E", "#F59E0B"};
            case ERROR   -> new String[]{"#FEF2F2", "#991B1B", "#EF4444"};
        };
    }

    private String iconForSeverity(Severity s) {
        return switch (s) {
            case INFO    -> "ℹ";
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR   -> "✕";
        };
    }

    // ── Inner record ─────────────────────────────────────────

    private record AlertItem(String message, Severity severity, String timestamp) {}
}
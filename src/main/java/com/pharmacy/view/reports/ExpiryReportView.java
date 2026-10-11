package com.pharmacy.view.reports;

import com.pharmacy.service.InventoryService;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * ExpiryReportView (Reports module) — Expired and near-expiry stock value at risk.
 */
public class ExpiryReportView {

    private final InventoryService invSvc = new InventoryService();
    private TableView<InventoryService.ExpiryAlert> table;
    private Label totalValueAtRisk, expiredCount, criticalCount;

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        HBox header = new HBox(12);
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("📋  Expiry Report"),
                lbl("Stock value at risk from expired or near-expiry items.", AppUIManager.COLOR_TEXT_MEDIUM)
        );
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button refreshBtn = AppUIManager.outlineButton("↻  Refresh");
        refreshBtn.setOnAction(e -> load());
        header.getChildren().addAll(tb, sp, refreshBtn);
        page.getChildren().add(header);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 24, 20, 24));

        // Summary row
        HBox summaryRow = new HBox(16);
        totalValueAtRisk = new Label("Rs 0.00");
        totalValueAtRisk.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_DANGER + ";");
        expiredCount = new Label("0");
        expiredCount.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_DANGER + ";");
        criticalCount = new Label("0");
        criticalCount.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_WARNING + ";");

        summaryRow.getChildren().addAll(
                summCard("💸", totalValueAtRisk, "Value at Risk"),
                summCard("☠", expiredCount,     "Expired Batches"),
                summCard("⚠", criticalCount,    "Critical (≤7 days)")
        );

        // Table
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setPlaceholder(new Label("No expiry issues found"));
        table.setFixedCellSize(36);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                strCol("Urgency",      90, a -> a.urgency().toString()),
                strCol("Medicine",    200, a -> a.batch().getBatchNumber() == null ? "ID " + a.batch().getMedId() : a.batch().getBatchNumber()),
                strCol("Batch #",     130, a -> a.batch().getBatchNumber()),
                strCol("Qty Remaining", 110, a -> String.valueOf(a.batch().getQtyRemaining())),
                strCol("Expiry Date", 120, a -> a.batch().getExpiryDate() != null ? a.batch().getExpiryDate().toString() : "—"),
                strCol("Days Left",    90, a -> a.daysUntilExpiry() < 0 ? "EXPIRED" : String.valueOf(a.daysUntilExpiry())),
                strCol("Est. Value",  120, a -> {
                    double val = a.batch().getQtyRemaining() * a.batch().getUnitCost();
                    return String.format("Rs %.2f", val);
                })
        );

        content.getChildren().addAll(summaryRow, table);
        page.getChildren().add(content);

        load();

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true); scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return scrollPane;
    }

    private void load() {
        table.getItems().clear();
        try {
            List<InventoryService.ExpiryAlert> alerts = invSvc.getExpiryAlerts();
            table.getItems().addAll(alerts);
            long expired  = alerts.stream().filter(a -> a.urgency() == InventoryService.ExpiryAlert.Urgency.EXPIRED).count();
            long critical = alerts.stream().filter(a -> a.urgency() == InventoryService.ExpiryAlert.Urgency.CRITICAL).count();
            double risk   = alerts.stream().mapToDouble(a -> a.batch().getQtyRemaining() * a.batch().getUnitCost()).sum();
            expiredCount.setText(String.valueOf(expired));
            criticalCount.setText(String.valueOf(critical));
            totalValueAtRisk.setText(String.format("Rs %.2f", risk));
        } catch (Exception ex) { /* DB not connected */ }
    }

    private VBox summCard(String icon, Label valueLabel, String name) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setStyle("-fx-background-color:white; -fx-background-radius:10; -fx-border-color:" + AppUIManager.COLOR_BORDER + "; -fx-border-width:1; -fx-border-radius:10;");
        HBox.setHgrow(card, Priority.ALWAYS);
        Label iconLbl = new Label(icon); iconLbl.setStyle("-fx-font-size:24px;");
        Label nameLbl = new Label(name); nameLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        card.getChildren().addAll(iconLbl, valueLabel, nameLbl);
        return card;
    }

    private TableColumn<InventoryService.ExpiryAlert, String> strCol(String title, double w, java.util.function.Function<InventoryService.ExpiryAlert, String> fn) {
        TableColumn<InventoryService.ExpiryAlert, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return c;
    }
    private Label lbl(String t, String c) { Label l = new Label(t); l.setStyle("-fx-font-size:13px; -fx-text-fill:"+c+";"); return l; }
}

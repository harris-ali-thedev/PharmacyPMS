package com.pharmacy.view.reports;

import com.pharmacy.dao.ProductDAO;
import com.pharmacy.service.InventoryService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ExpiryReportView (Reports module) — Expired and near-expiry stock value at risk.
 */
public class ExpiryReportView {

    private final InventoryService invSvc = new InventoryService();
    private final ProductDAO productDAO = new ProductDAO();
    private TableView<InventoryService.ExpiryAlert> table;
    private TableView<ProductDAO.ExpiringProduct> retailTable;
    private Label totalValueAtRisk, expiredCount, criticalCount;
    private Label windowCountTitle;
    private ComboBox<String> criticalWindowFilter;

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
        Button refreshBtn = AppUIManager.primaryButton("Refresh");
        refreshBtn.setOnAction(e -> load());
        header.getChildren().addAll(tb, sp, refreshBtn);
        page.getChildren().add(header);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 24, 20, 24));

        criticalWindowFilter = new ComboBox<>();
        criticalWindowFilter.getItems().setAll("7 days", "30 days", "3 months");
        criticalWindowFilter.setValue("7 days");
        criticalWindowFilter.setPrefWidth(145);
        criticalWindowFilter.setOnAction(e -> load());
        HBox filterRow = new HBox(10, lbl("Critical expiry window:", AppUIManager.COLOR_TEXT_DARK),
                criticalWindowFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // Summary row
        HBox summaryRow = new HBox(16);
        totalValueAtRisk = new Label("Rs 0.00");
        totalValueAtRisk.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_DANGER + ";");
        expiredCount = new Label("0");
        expiredCount.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_DANGER + ";");
        criticalCount = new Label("0");
        criticalCount.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_WARNING + ";");
        windowCountTitle = new Label("Expiring within 7 days");
        windowCountTitle.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");

        summaryRow.getChildren().addAll(
                summCard("💸", totalValueAtRisk, "Value at Risk"),
                summCard("☠", expiredCount,     "Expired Batches"),
                summCard("⚠", criticalCount,    windowCountTitle)
        );

        // Table
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setPlaceholder(new Label("No expiry issues found"));
        table.setFixedCellSize(34);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                strCol("Urgency",      90, a -> a.urgency().toString()),
                strCol("Medicine",    200, a -> a.batch().getMedName() == null
                        ? "Medicine #" + a.batch().getMedId()
                        : a.batch().getMedName()),
                strCol("Batch #",     130, a -> a.batch().getBatchNumber() == null ? "ID " + a.batch().getMedId() : a.batch().getBatchNumber()),
                strCol("Qty Remaining", 110, a -> String.valueOf(a.batch().getQtyRemaining())),
                strCol("Expiry Date", 120, a -> a.batch().getExpiryDate() != null ? a.batch().getExpiryDate().toString() : "—"),
                strCol("Days Left",    90, a -> a.daysUntilExpiry() < 0 ? "EXPIRED" : String.valueOf(a.daysUntilExpiry())),
                strCol("Est. Value",  120, a -> {
                    double val = a.batch().getQtyRemaining() * a.batch().getUnitCost();
                    return String.format("Rs %.2f", val);
                })
        );

        content.getChildren().addAll(filterRow, summaryRow, table);
        Label retailTitle = AppUIManager.sectionTitle("Perishable Store Products");
        retailTable = new TableView<>();
        AppUIManager.styleTable(retailTable);
        retailTable.setPlaceholder(new Label("No retail products are expired or within the selected window."));
        retailTable.getColumns().addAll(
                retailCol("Product", 190, ProductDAO.ExpiringProduct::name),
                retailCol("Category", 120, ProductDAO.ExpiringProduct::category),
                retailCol("Batch #", 120, row -> row.batchNumber() == null ? "—" : row.batchNumber()),
                retailCol("Qty", 70, row -> String.valueOf(row.quantity())),
                retailCol("Expiry", 100, row -> row.expiryDate().toString()),
                retailCol("Days Left", 90, row -> String.valueOf(java.time.temporal.ChronoUnit.DAYS
                        .between(LocalDate.now(), row.expiryDate()))),
                retailCol("Est. Value", 110, row -> String.format("Rs %.2f", row.quantity() * row.unitCost()))
        );
        retailTable.setPrefHeight(220);
        content.getChildren().addAll(retailTitle, retailTable);
        page.getChildren().add(content);

        load();

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true); scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return scrollPane;
    }

    private void load() {
        if (table == null || criticalWindowFilter == null) {
            return;
        }
        table.getItems().clear();
        retailTable.getItems().clear();
        try {
            String selectedWindow = criticalWindowFilter.getValue();
            LocalDate today = LocalDate.now();
            LocalDate throughDate = switch (selectedWindow) {
                case "30 days" -> today.plusDays(30);
                case "3 months" -> today.plusMonths(3);
                default -> today.plusDays(7);
            };
            windowCountTitle.setText("Expiring within " + selectedWindow);
            List<InventoryService.ExpiryAlert> alerts = invSvc.getExpiryAlerts(throughDate);
            table.getItems().addAll(alerts);
            List<ProductDAO.ExpiringProduct> retailAlerts = productDAO.getExpiringRetailProducts(throughDate);
            retailTable.getItems().setAll(retailAlerts);
            long expired  = alerts.stream().filter(a -> a.urgency() == InventoryService.ExpiryAlert.Urgency.EXPIRED).count();
            expired += retailAlerts.stream().filter(row -> row.expiryDate().isBefore(today)).count();
            long critical = alerts.stream().filter(a -> a.daysUntilExpiry() >= 0).count();
            critical += retailAlerts.stream().filter(row -> !row.expiryDate().isBefore(today)).count();
            double risk = alerts.stream().mapToDouble(a -> a.batch().getQtyRemaining() * a.batch().getUnitCost()).sum()
                    + retailAlerts.stream().mapToDouble(row -> row.quantity() * row.unitCost()).sum();
            expiredCount.setText(String.valueOf(expired));
            criticalCount.setText(String.valueOf(critical));
            totalValueAtRisk.setText(String.format("Rs %.2f", risk));
        } catch (Exception ex) {
            table.getItems().clear();
            retailTable.getItems().clear();
            AlertUtil.showError("Expiry Report", "Could not load expiry data: " + ex.getMessage());
        }
    }

    private VBox summCard(String icon, Label valueLabel, String name) {
        return summCard(icon, valueLabel, new Label(name));
    }

    private VBox summCard(String icon, Label valueLabel, Label nameLbl) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setStyle("-fx-background-color:white; -fx-background-radius:10; -fx-border-color:" + AppUIManager.COLOR_BORDER + "; -fx-border-width:1; -fx-border-radius:10;");
        HBox.setHgrow(card, Priority.ALWAYS);
        Label iconLbl = new Label(icon); iconLbl.setStyle("-fx-font-size:24px;");
        nameLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        card.getChildren().addAll(iconLbl, valueLabel, nameLbl);
        return card;
    }

    private TableColumn<InventoryService.ExpiryAlert, String> strCol(String title, double w, java.util.function.Function<InventoryService.ExpiryAlert, String> fn) {
        TableColumn<InventoryService.ExpiryAlert, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return c;
    }
    private TableColumn<ProductDAO.ExpiringProduct, String> retailCol(
            String title, double width, java.util.function.Function<ProductDAO.ExpiringProduct, String> getter) {
        TableColumn<ProductDAO.ExpiringProduct, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }
    private Label lbl(String t, String c) { Label l = new Label(t); l.setStyle("-fx-font-size:13px; -fx-text-fill:"+c+";"); return l; }
}

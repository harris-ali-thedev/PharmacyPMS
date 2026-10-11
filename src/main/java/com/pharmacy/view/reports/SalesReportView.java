package com.pharmacy.view.reports;

import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.Sale;
import com.pharmacy.service.ReportExportService;
import com.pharmacy.util.AlertUtil;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SalesReportView {

    private final SaleDAO saleDAO = new SaleDAO();
    private TableView<Sale> recentTable;

    public BorderPane getView() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        Label title = new Label("Sales Reports");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        // ── Metric cards ─────────────────────────────────────
        HBox cards = new HBox(14);
        try {
            Map<String, Object> daily = saleDAO.getDailyStats(LocalDate.now());
            Map<String, Object> monthly = saleDAO.getMonthlyStats(LocalDate.now().getYear(), LocalDate.now().getMonthValue());

            LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
            List<Map<String, Object>> weekly = saleDAO.getWeeklyBreakdown(weekStart);
            double weekRevenue = weekly.stream().mapToDouble(m -> (double) m.get("revenue")).sum();

            cards.getChildren().addAll(
                    metricCard("Daily Revenue",   String.format("Rs %.0f", daily.getOrDefault("revenue", 0.0)),  "#B80A0A"),
                    metricCard("Daily Transactions",      String.valueOf(daily.getOrDefault("txn_count", 0)),           "#059669"),
                    metricCard("Weekly Revenue",  String.format("Rs %.0f", weekRevenue),                        "#0A83B8"),
                    metricCard("Monthly Revenue", String.format("Rs %.0f", monthly.getOrDefault("revenue", 0.0)),"#1F8D8D"),
                    metricCard("Yearly Revenue",  String.format("Rs %.0f", monthly.getOrDefault("revenue", 0.0)),"#0AB841")
            );
        } catch (Exception e) {
            cards.getChildren().add(new Label("Could not load stats: " + e.getMessage()));
        }

        // ── Weekly breakdown ─────────────────────────────────
        VBox weekCard = buildWeeklyCard();

        // ── Recent sales table + export ──────────────────────
        VBox tableCard = new VBox(10);
        tableCard.setPadding(new Insets(16));
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                           "-fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10;");

        HBox tableHeader = new HBox(10);
        Label tableTitle = new Label("Recent Sales");
        tableTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportXlsx = new Button("Export Excel");
        exportXlsx.setStyle("-fx-background-color: #0ED118; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");
        Button exportPdf = new Button("Export PDF");
        exportPdf.setStyle("-fx-background-color: #F21000; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");
        tableHeader.getChildren().addAll(tableTitle, sp, exportXlsx, exportPdf);

        recentTable = buildSaleTable();
        loadRecentSales();

        tableCard.getChildren().addAll(tableHeader, recentTable);
        VBox.setVgrow(recentTable, Priority.ALWAYS);

        exportXlsx.setOnAction(e -> exportExcel());
        exportPdf.setOnAction(e -> exportPdf());

        content.getChildren().addAll(title, cards, weekCard, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        pane.setCenter(scroll);
        return pane;
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.setPrefWidth(180);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                      "-fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        card.getChildren().addAll(lbl, val);
        return card;
    }

    private VBox buildWeeklyCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                      "-fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10;");
        Label t = new Label("This Week — Daily Breakdown");
        t.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        card.getChildren().add(t);

        try {
            LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
            List<Map<String, Object>> rows = saleDAO.getWeeklyBreakdown(weekStart);

            double maxRevenue = rows.stream().mapToDouble(m -> (double) m.get("revenue")).max().orElse(1);

            HBox bars = new HBox(10);
            bars.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
            bars.setPrefHeight(120);

            for (Map<String, Object> row : rows) {
                LocalDate day = (LocalDate) row.get("day");
                double rev = (double) row.get("revenue");
                double pct = rev / maxRevenue;

                VBox barBox = new VBox(4);
                barBox.setAlignment(javafx.geometry.Pos.BOTTOM_CENTER);
                barBox.setPrefWidth(60);

                Label revLbl = new Label(String.format("Rs %.0f", rev));
                revLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");

                Region bar = new Region();
                bar.setPrefWidth(40);
                bar.setPrefHeight((int)(100 * pct));
                bar.setStyle("-fx-background-color: #185FA5; -fx-background-radius: 3 3 0 0;");

                Label dayLbl = new Label(day.format(DateTimeFormatter.ofPattern("EEE")));
                dayLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");

                barBox.getChildren().addAll(revLbl, bar, dayLbl);
                bars.getChildren().add(barBox);
            }

            if (rows.isEmpty()) card.getChildren().add(new Label("No data this week yet."));
            else card.getChildren().add(bars);

        } catch (Exception e) {
            card.getChildren().add(new Label("Error: " + e.getMessage()));
        }
        return card;
    }

    private TableView<Sale> buildSaleTable() {
        TableView<Sale> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label("No recent sales."));

        TableColumn<Sale, Integer> idCol = new TableColumn<>("Sale ID");
        idCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getSaleId()).asObject());

        TableColumn<Sale, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSaleDate() != null ?
                c.getValue().getSaleDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : ""));

        TableColumn<Sale, String> cashierCol = new TableColumn<>("Cashier");
        cashierCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCashierName()));

        TableColumn<Sale, Double> totalCol = new TableColumn<>("Grand Total");
        totalCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getGrandTotal()).asObject());
        totalCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Double v, boolean e) {
                super.updateItem(v, e);
                setText(e || v == null ? null : String.format("Rs %.2f", v));
            }
        });

        TableColumn<Sale, String> pmCol = new TableColumn<>("Payment");
        pmCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentMethod()));

        TableColumn<Sale, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(String v, boolean e) {
                super.updateItem(v, e);
                if (e || v == null) { setText(null); return; }
                setText(v);
                setStyle("COMPLETED".equals(v) ? "-fx-text-fill: #059669;" : "-fx-text-fill: #DC2626;");
            }
        });

        t.getColumns().addAll(idCol, dateCol, cashierCol, totalCol, pmCol, statusCol);
        return t;
    }

    private void loadRecentSales() {
        try {
            List<Sale> sales = saleDAO.getRecentSales(100);
            recentTable.setItems(FXCollections.observableArrayList(sales));
        } catch (Exception e) {
            AlertUtil.showError("Error", "Could not load sales: " + e.getMessage());
        }
    }

    private void exportExcel() {
        try {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Save Excel Report To...");
            File dir = dc.showDialog(recentTable.getScene().getWindow());
            if (dir == null) return;
            ReportExportService svc = new ReportExportService();
            String path = svc.exportSalesToExcel(recentTable.getItems(), dir.getAbsolutePath());
            AlertUtil.showInfo("Export Complete", "Saved to:\n" + path);
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }

    private void exportPdf() {
        try {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Save PDF Report To...");
            File dir = dc.showDialog(recentTable.getScene().getWindow());
            if (dir == null) return;
            ReportExportService svc = new ReportExportService();
            String path = svc.exportSalesToPdf(recentTable.getItems(), "Sales Report", dir.getAbsolutePath());
            AlertUtil.showInfo("Export Complete", "Saved to:\n" + path);
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }
}

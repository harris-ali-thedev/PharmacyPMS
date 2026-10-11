package com.pharmacy.view.reports;

import com.pharmacy.service.ReportingService;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ProfitMarginView — Gross profit and margin per product / period.
 */
public class ProfitMarginView {

    private final ReportingService reportSvc = new ReportingService();
    private DatePicker fromPicker, toPicker;
    private TableView<Map<String,Object>> table;

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");
        page.getChildren().addAll(buildHeader(), buildFilters(), buildContent());

        fromPicker.setValue(LocalDate.now().minusDays(30));
        toPicker.setValue(LocalDate.now());
        loadReport();

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true); sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return sp;
    }

    private HBox buildHeader() {
        HBox h = new HBox();
        h.setPadding(new Insets(22, 24, 16, 24));
        h.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("📉  Profit & Margin Analysis"),
                lbl("Gross profit, cost of goods sold, and margin per day.", AppUIManager.COLOR_TEXT_MEDIUM)
        );
        h.getChildren().add(tb);
        return h;
    }

    private HBox buildFilters() {
        HBox tb = new HBox(12);
        tb.setPadding(new Insets(14, 24, 12, 24));
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        fromPicker = AppUIManager.styledDatePicker(); fromPicker.setPrefWidth(140);
        toPicker   = AppUIManager.styledDatePicker(); toPicker.setPrefWidth(140);
        Button runBtn = AppUIManager.primaryButton("▶  Run");
        runBtn.setOnAction(e -> loadReport());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        tb.getChildren().addAll(lbl("From:", AppUIManager.COLOR_TEXT_DARK), fromPicker,
                lbl("To:", AppUIManager.COLOR_TEXT_DARK), toPicker, sp, runBtn);
        return tb;
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));

        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setPlaceholder(new Label("Run report to see profit data"));
        table.setFixedCellSize(36);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                col("Date",           120, "sale_date", false),
                col("Revenue",        130, "revenue",   true),
                col("COGS",           130, "cogs",      true),
                col("Gross Profit",   130, "gross_profit", true),
                col("Margin %",        90, "margin_percent", false),
                col("Tax Collected",  130, "tax_collected", true),
                col("Discounts",      120, "total_discounts", true)
        );

        content.getChildren().add(table);
        return content;
    }

    private TableColumn<Map<String,Object>, String> col(String title, double w, String key, boolean currency) {
        TableColumn<Map<String,Object>, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> {
            Object v = d.getValue().get(key);
            if (v == null) return new SimpleStringProperty("—");
            String txt = currency ? String.format("Rs %.2f", ((Number)v).doubleValue()) : v.toString();
            return new SimpleStringProperty(txt);
        });
        return c;
    }

    private void loadReport() {
        if (fromPicker.getValue() == null || toPicker.getValue() == null) return;
        table.getItems().clear();
        try {
            table.getItems().addAll(reportSvc.getProfitReport(fromPicker.getValue(), toPicker.getValue()));
        } catch (Exception ex) {
            MessageDialog.showError("Error", ex.getMessage());
        }
    }
    private Label lbl(String t, String c) { Label l = new Label(t); l.setStyle("-fx-font-size:13px; -fx-text-fill:"+c+";"); return l; }
}
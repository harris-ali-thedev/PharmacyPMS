package com.pharmacy.view.reports;

import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.ViewManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * ReportsDashboardView — Hub page with navigation cards to all report sub-views.
 */
public class ReportsDashboardView {

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        HBox header = new HBox();
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("📈  Reports & Analytics"),
                lbl("Business intelligence, sales analytics, and operational reports.", AppUIManager.COLOR_TEXT_MEDIUM)
        );
        header.getChildren().add(tb);
        page.getChildren().add(header);

        VBox content = new VBox(24);
        content.setPadding(new Insets(24));

        // Financial reports
        content.getChildren().add(section("Financial Reports",
                reportCard("💰", "Sales Report",         "Daily, weekly, monthly sales analysis with charts",    "salesreport"),
                reportCard("📉", "Profit & Margin",      "Gross profit, COGS, and margin percentage analysis",   "profit"),
                reportCard("🧾", "Invoice History",       "Browse and reprint past invoices",                     "invoicehistory")
        ));

        // Inventory reports
        content.getChildren().add(section("Inventory Reports",
                reportCard("⏰", "Expiry Report",         "Expired and near-expiry stock with value at risk",     "expiryreport"),
                reportCard("🔄", "Reorder Report",        "Medicines below reorder level requiring restocking",   "reorder"),
                reportCard("📦", "Stock Valuation",       "Current inventory value using FIFO costing",           "inventory")
        ));

        // Clinical reports
        content.getChildren().add(section("Clinical Reports",
                reportCard("💊", "Prescription Analysis", "Prescription volume, dispensing rates, top drugs",     "prescriptions"),
                reportCard("👤", "Patient Activity",      "Patient visit frequency and purchase patterns",         "patients"),
                reportCard("🔍", "Audit Log",             "Full system activity audit trail",                     "auditlog")
        ));

        page.getChildren().add(content);

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return sp;
    }

    private VBox section(String title, Node... cards) {
        VBox sec = new VBox(14);
        sec.getChildren().add(AppUIManager.sectionTitle(title));
        HBox row = new HBox(16);
        row.getChildren().addAll(cards);
        sec.getChildren().add(row);
        return sec;
    }

    private VBox reportCard(String icon, String name, String desc, String viewKey) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setPrefWidth(240);
        card.setStyle("-fx-background-color:white; -fx-background-radius:10; " +
                "-fx-border-color:" + AppUIManager.COLOR_BORDER + "; -fx-border-width:1; -fx-border-radius:10; " +
                "-fx-cursor:hand; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),8,0,0,2);");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size:28px;");

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_TEXT_DARK + ";");

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + "; -fx-wrap-text:true;");
        descLbl.setMaxWidth(210);

        Button openBtn = AppUIManager.outlineButton("Open →");
        openBtn.setOnAction(e -> ViewManager.getInstance().navigate(viewKey));

        card.getChildren().addAll(iconLbl, nameLbl, descLbl, openBtn);

        String base = card.getStyle();
        String hover = base.replace("rgba(0,0,0,0.05)", "rgba(30,58,95,0.12)") +
                "-fx-border-color:" + AppUIManager.COLOR_PRIMARY + ";";
        card.setOnMouseEntered(e -> card.setStyle(hover));
        card.setOnMouseExited(e -> card.setStyle(base));
        card.setOnMouseClicked(e -> ViewManager.getInstance().navigate(viewKey));
        return card;
    }

    private Label lbl(String text, String color) {
        Label l = new Label(text); l.setStyle("-fx-font-size:13px; -fx-text-fill:" + color + ";"); return l;
    }
}
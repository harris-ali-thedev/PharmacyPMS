package com.pharmacy.view.inventory;

import com.pharmacy.service.StockService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.PharmacyDesignSystem;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** Admin screen: proves products.stock_qty, the medicine mirror and the lots all agree. */
public class StockReconciliationView {

    private static final String F = PharmacyDesignSystem.FONT;
    private final StockService stockService = new StockService();
    private final TableView<StockService.StockCheck> table = new TableView<>();
    private final Label checked = new Label("—");
    private final Label healthy = new Label("—");
    private final Label problems = new Label("—");
    private final Label status = new Label();
    private CheckBox onlyProblems;

    public Node getView() {
        if (!SessionManager.getInstance().isAdmin()) {
            return PharmacyDesignSystem.workspace(PharmacyDesignSystem.errorState("Administrators only."));
        }
        Button refresh = PharmacyDesignSystem.primaryButton("Run Check");
        refresh.setOnAction(e -> load());
        onlyProblems = new CheckBox("Show mismatches only");
        onlyProblems.setSelected(true);
        onlyProblems.setOnAction(e -> load());

        VBox page = new VBox(18,
                PharmacyDesignSystem.pageHeaderWithActions("Stock Integrity",
                        "One stock figure per product: sellable stock, medicine mirror and batch totals must agree.",
                        refresh),
                metrics(), directory());
        page.setPadding(new Insets(22, 24, 24, 24));
        page.setStyle(F + "-fx-background-color: " + PharmacyDesignSystem.BG + ";");
        load();
        return PharmacyDesignSystem.workspace(page);
    }

    private HBox metrics() {
        HBox row = new HBox(14,
                card("PRODUCTS CHECKED", checked, PharmacyDesignSystem.PRIMARY),
                card("CONSISTENT", healthy, PharmacyDesignSystem.SUCCESS),
                card("NEEDS ATTENTION", problems, PharmacyDesignSystem.DANGER));
        row.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        return row;
    }

    private VBox card(String label, Label value, String color) {
        Label l = PharmacyDesignSystem.sectionEyebrow(label);
        value.setStyle(F + "-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: " + color + ";");
        VBox c = PharmacyDesignSystem.card();
        c.setSpacing(4);
        c.getChildren().addAll(l, value);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    private VBox directory() {
        PharmacyDesignSystem.styleTable(table);
        table.setPlaceholder(PharmacyDesignSystem.emptyState("All stock is consistent",
                "No product has a mismatch between its stock figure and its batches."));
        table.setMinHeight(380);
        table.getColumns().add(col("Product", 220, StockService.StockCheck::name));
        table.getColumns().add(col("Category", 120, StockService.StockCheck::category));
        table.getColumns().add(col("Stock (authority)", 120, c -> String.valueOf(c.productQty())));
        table.getColumns().add(col("Medicine mirror", 120, c -> c.medicineQty() == null ? "—" : String.valueOf(c.medicineQty())));
        table.getColumns().add(col("Batch total", 110, c -> c.lotQty() == null ? "n/a" : String.valueOf(c.lotQty())));
        TableColumn<StockService.StockCheck, String> st = col("Status", 190, StockService.StockCheck::status);
        st.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                setGraphic("OK".equals(v) ? PharmacyDesignSystem.successBadge("OK")
                        : PharmacyDesignSystem.dangerBadge(v.replace('_', ' ')));
            }
        });
        table.getColumns().add(st);

        status.setStyle(F + "-fx-font-size: 12px; -fx-text-fill: " + PharmacyDesignSystem.MUTED + ";");
        HBox bar = new HBox(12, onlyProblems, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox box = PharmacyDesignSystem.card();
        box.getChildren().addAll(bar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void load() {
        try {
            List<StockService.StockCheck> all = stockService.reconcile();
            long bad = all.stream().filter(c -> !c.ok()).count();
            checked.setText(String.valueOf(all.size()));
            problems.setText(String.valueOf(bad));
            healthy.setText(String.valueOf(all.size() - bad));
            table.getItems().setAll(onlyProblems.isSelected()
                    ? all.stream().filter(c -> !c.ok()).toList() : all);
            status.setText(bad == 0 ? "Everything agrees." : bad + " product(s) need review (see migration 002 repair notes).");
        } catch (Exception e) {
            AlertUtil.showError("Stock Integrity", "Could not run the check: " + e.getMessage());
        }
    }

    private TableColumn<StockService.StockCheck, String> col(String title, double w,
            java.util.function.Function<StockService.StockCheck, String> f) {
        TableColumn<StockService.StockCheck, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        return c;
    }
}

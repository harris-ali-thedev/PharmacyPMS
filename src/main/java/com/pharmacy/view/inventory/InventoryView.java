package com.pharmacy.view.inventory;

import com.pharmacy.controller.InventoryController;
import com.pharmacy.model.Medicine;
import com.pharmacy.service.ReportExportService;
import com.pharmacy.util.AlertUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class InventoryView {

    private TableView<Medicine> table;
    private TextField searchField;
    private Label statusLabel;

    public BorderPane getView() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        // ── Header row ───────────────────────────────────────
        Label title = new Label("Inventory Management");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        searchField = new TextField();
        searchField.setPromptText("Search by name, category, barcode...");
        searchField.setPrefHeight(34);
        searchField.setPrefWidth(240);

        Button searchBtn = new Button("Search");
        searchBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");

        Button addBtn    = new Button("+ Add Medicine");
        addBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");

        Button refreshBtn = new Button("↺ Refresh");
        refreshBtn.setStyle("-fx-cursor: hand;");

        Button lowStockBtn = new Button("⚠ Low Stock Only");
        lowStockBtn.setStyle("-fx-cursor: hand;");

        Button exportExcelBtn = new Button("Export Excel");
        exportExcelBtn.setStyle("-fx-background-color: #0ED118; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.setStyle("-fx-background-color: #F21000; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");


        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, searchField, searchBtn, spacer, lowStockBtn, addBtn, refreshBtn);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.SOMETIMES);

        // ── Table ─────────────────────────────────────────────
        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");


        HBox bottomToolbar = new HBox(8, exportExcelBtn, exportPdfBtn);
        bottomToolbar.setAlignment(javafx.geometry.Pos.BOTTOM_RIGHT);

        content.getChildren().addAll(title, toolbar, table, bottomToolbar, statusLabel);
        pane.setCenter(content);

        // ── Controller ───────────────────────────────────────
        InventoryController controller = new InventoryController(table, searchField, statusLabel);
        searchBtn.setOnAction(e -> controller.search());
        searchField.setOnAction(e -> controller.search());
        addBtn.setOnAction(e -> controller.showAddDialog());
        refreshBtn.setOnAction(e -> controller.loadAll());
        lowStockBtn.setOnAction(e -> controller.showLowStock());

        exportExcelBtn.setOnAction(e -> exportExcel());
        exportPdfBtn.setOnAction(e -> exportPdf());

        controller.loadAll();

        return pane;
    }

    private TableView<Medicine> buildTable() {
        TableView<Medicine> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label("No medicines found."));

        TableColumn<Medicine, String> nameCol = col("Name", c -> new SimpleStringProperty(c.getValue().getName()), 200);
        TableColumn<Medicine, String> genCol  = col("Generic Name", c -> new SimpleStringProperty(c.getValue().getGenericName()), 160);
        TableColumn<Medicine, String> catCol  = col("Category", c -> new SimpleStringProperty(c.getValue().getCategory()), 120);
        TableColumn<Medicine, String> barCol  = col("Barcode", c -> new SimpleStringProperty(c.getValue().getBarcode()), 110);

        TableColumn<Medicine, Double> unitPriceCol = new TableColumn<>("Unit Price");
        unitPriceCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getUnitPrice()).asObject());
        unitPriceCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Double v, boolean e) {
                super.updateItem(v, e); setText(e || v == null ? null : String.format("%.2f", v));
            }
        });

        TableColumn<Medicine, Double> packPriceCol = new TableColumn<>("Pack Price");
        packPriceCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPackCost()).asObject());
        packPriceCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Double v, boolean e) {
                super.updateItem(v, e); setText(e || v == null ? null : String.format("%.2f", v));
            }
        });

        TableColumn<Medicine, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStockQty()).asObject());
        stockCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Integer v, boolean e) {
                super.updateItem(v, e);
                if (e || v == null) { setText(null); return; }
                setText(String.valueOf(v));
                Medicine m = getTableView().getItems().get(getIndex());
                setStyle(m.isLowStock() ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "-fx-text-fill: #059669;");
            }
        });

        TableColumn<Medicine, Integer> reorderCol = col2("Reorder Lvl",
                c -> new SimpleIntegerProperty(c.getValue().getReorderLevel()).asObject());
        reorderCol.setPrefWidth(90);

        TableColumn<Medicine, Boolean> rxCol = new TableColumn<>("Narcotics");
        rxCol.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isNarcotics()).asObject());
        rxCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Boolean v, boolean e) {
                super.updateItem(v, e);
                if (e || v == null) { setText(null); return; }
                setText(v ? "Yes" : "No");
                setStyle(v ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "");
            }
        });

        TableColumn<Medicine, String> supCol = col("Supplier", c -> new SimpleStringProperty(c.getValue().getSupplierName()), 120);

        TableColumn<Medicine, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            final Button editBtn   = new Button("Edit");
            final Button deleteBtn = new Button("Delete");
            {
                editBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
                deleteBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
            }
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Medicine m = getTableView().getItems().get(getIndex());
                HBox box = new HBox(6, editBtn, deleteBtn);
                editBtn.setOnAction(e -> {
                    InventoryController ctrl = (InventoryController) getTableView().getUserData();
                    if (ctrl != null) ctrl.showEditDialog(m);
                });
                deleteBtn.setOnAction(e -> {
                    InventoryController ctrl = (InventoryController) getTableView().getUserData();
                    if (ctrl != null) ctrl.deleteMedicine(m);
                });
                setGraphic(box);
            }
        });

        t.getColumns().addAll(nameCol, genCol, catCol, barCol, stockCol, reorderCol, rxCol, supCol, unitPriceCol, packPriceCol, actionCol);
        return t;
    }

    private <T> TableColumn<Medicine, T> col(String title, javafx.util.Callback<TableColumn.CellDataFeatures<Medicine, T>, javafx.beans.value.ObservableValue<T>> factory, double w) {
        TableColumn<Medicine, T> c = new TableColumn<>(title);
        c.setCellValueFactory(factory);
        c.setPrefWidth(w);
        return c;
    }

    private TableColumn<Medicine, Integer> col2(String title, javafx.util.Callback<TableColumn.CellDataFeatures<Medicine, Integer>, javafx.beans.value.ObservableValue<Integer>> factory) {
        TableColumn<Medicine, Integer> c = new TableColumn<>(title);
        c.setCellValueFactory(factory);
        return c;
    }


    private void exportExcel() {
        try {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Save Excel Report To...");
            // FIXED: Changed recentTable to table
            File dir = dc.showDialog(table.getScene().getWindow());
            if (dir == null) return;

            ReportExportService svc = new ReportExportService();
            // NOTE: Make sure this service method accepts a List<Medicine>!
            String path = svc.exportInventoryToExcel(table.getItems(), dir.getAbsolutePath());

            AlertUtil.showInfo("Export Complete", "Saved to:\n" + path);
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }

    private void exportPdf() {
        try {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Save PDF Report To...");
            // FIXED: Changed recentTable to table
            File dir = dc.showDialog(table.getScene().getWindow());
            if (dir == null) return;

            ReportExportService svc = new ReportExportService();
            // NOTE: Make sure this service method accepts a List<Medicine>!
            String path = svc.exportInventoryToPdf(table.getItems(), "Inventory Report", dir.getAbsolutePath());

            AlertUtil.showInfo("Export Complete", "Saved to:\n" + path);
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }

    public TableView<Medicine> getTable() {
        return table;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }
}

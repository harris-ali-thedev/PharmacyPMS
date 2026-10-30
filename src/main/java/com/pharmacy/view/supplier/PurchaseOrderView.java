package com.pharmacy.view.supplier;

import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.dao.ProductDAO;
import com.pharmacy.model.Product;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.PharmacyDesignSystem;
import com.pharmacy.service.InventoryService;
import com.pharmacy.service.InventoryService.SuggestedPO;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.UiTasks;
import com.pharmacy.dao.PurchaseOrderDAO.POItem;
import com.pharmacy.model.User;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PurchaseOrderView {

    /* ============================================================
       STATE — Suggestion side
       ============================================================ */
    private static final class Row {
        final SuggestedPO.LineItem line;
        final Supplier supplier;
        final IntegerProperty qty;
        final int original;

        Row(SuggestedPO.LineItem line, Supplier supplier) {
            this.line = line;
            this.supplier = supplier;
            this.qty = new SimpleIntegerProperty(line.getSuggestedQty());
            this.original = line.getSuggestedQty();
        }

        double total() { return qty.get() * line.getPackCost(); }
    }

    private record Generated(List<SuggestedPO> pos, int lowStockProducts, long millis) {}

    private static final DateTimeFormatter PO_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final InventoryService inventoryService = new InventoryService();
    private final PurchaseOrderDAO poDAO = new PurchaseOrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();

    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private int lowStockProducts;
    private long generatedMillis;
    private boolean generatedOnce;

    private final Label headline = new Label("No suggestions generated yet");
    private final Label subline = new Label("Press Generate to analyse low-stock products.");
    private final Label skuValue = bigNumber(), unitsValue = bigNumber(), totalValue = bigNumber();
    private final VBox breakdownBox = new VBox(8);
    private final VBox validationBox = new VBox(12);
    private final Label statusLabel = new Label();
    private TableView<Row> suggestionTable;
    private Button generateBtn, createAllBtn;

    /* ============================================================
       STATE — Purchase Order side
       ============================================================ */
    private final ObservableList<Map<String, Object>> poRows = FXCollections.observableArrayList();
    private FilteredList<Map<String, Object>> filteredRows;
    private TableView<Map<String, Object>> poTable;
    private TextField searchField;
    private ComboBox<String> statusFilter;
    private Label totalOrdersLabel, pendingOrdersLabel, receivedOrdersLabel,
            cancelledOrdersLabel, totalValueLabel;

    /* ============================================================
       VIEW
       ============================================================ */
    public Node getView() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");

        VBox left = new VBox(16, summaryCard(), suggestionCard(), breakdownCard(), actionBar());
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = card();
        right.setPrefWidth(310);
        right.setMinWidth(270);
        right.setMaxWidth(340);
        right.getChildren().addAll(title("Validation"), validationBox);

        HBox top = new HBox(16, left, right);

        page.getChildren().addAll(toolbar(), top, buildPurchaseOrdersSection());

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG
                + "; -fx-background: " + AppUIManager.COLOR_BG + ";");

        refreshSummary();
        loadOrders();
        return scroll;
    }

    /* ============================================================
       Toolbar
       ============================================================ */
    private Node toolbar() {
        Label pageTitle = AppUIManager.pageTitle("Purchase Orders");
        Button newPo = new Button("+ New PO");
        newPo.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        newPo.setOnAction(e -> showNewPoDialog());

        generateBtn = new Button("Generate Suggestions");
        generateBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        generateBtn.setOnAction(e -> generate());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        HBox bar = new HBox(10, pageTitle, newPo, generateBtn, spacer, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /* ============================================================
       Summary Card
       ============================================================ */
    private Node summaryCard() {
        headline.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        subline.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        subline.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox h = new HBox(28, new VBox(4, headline, subline), spacer,
                stat("Total SKUs", skuValue), stat("Total Units", unitsValue), stat("Total Value", totalValue));
        h.setAlignment(Pos.CENTER_LEFT);
        VBox c = card();
        c.getChildren().add(h);
        return c;
    }

    /* ============================================================
       Suggestion table
       ============================================================ */
    private Node suggestionCard() {
        suggestionTable = new TableView<>(rows);
        AppUIManager.styleTable(suggestionTable);
        suggestionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        suggestionTable.setFixedCellSize(34);
        suggestionTable.setPrefHeight(300);
        suggestionTable.setPlaceholder(new Label("No suggestions. Press Generate Suggestions."));

        TableColumn<Row, String> sku = new TableColumn<>("SKU Name");
        sku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().line.getProductName()));

        TableColumn<Row, String> sup = new TableColumn<>("Supplier");
        sup.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().supplier.getName()));

        TableColumn<Row, Row> stock = new TableColumn<>("Current Stock");
        stock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        stock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); return; }
                int s = r.line.getCurrentStock();
                Label n = new Label(String.valueOf(s));
                n.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (s <= 0 ? "#B91C1C" : AppUIManager.COLOR_TEXT_DARK) + ";");
                Label tag = new Label(s <= 0 ? "out of stock" : "low stock");
                tag.setStyle("-fx-font-size: 10px; -fx-padding: 1 6; -fx-background-radius: 8; "
                        + (s <= 0 ? "-fx-background-color:#FEE2E2; -fx-text-fill:#B91C1C;"
                        : "-fx-background-color:#FEF3C7; -fx-text-fill:#B45309;"));
                setGraphic(new HBox(8, n, tag));
            }
        });

        TableColumn<Row, Number> qty = new TableColumn<>("Suggested Qty");
        qty.setCellValueFactory(c -> c.getValue().qty);
        qty.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Integer> spinner = new Spinner<>(1, 1_000_000, 1);
            {
                spinner.setEditable(true);
                spinner.setPrefWidth(110);
                spinner.valueProperty().addListener((o, a, n) -> {
                    Row r = getTableRow() == null ? null : getTableRow().getItem();
                    if (r != null && n != null && r.qty.get() != n) r.qty.set(n);
                });
                spinner.focusedProperty().addListener((o, was, now) -> { if (!now) spinner.increment(0); });
            }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                spinner.getValueFactory().setValue(v.intValue());
                setGraphic(spinner);
            }
        });

        TableColumn<Row, String> unit = new TableColumn<>("Unit Price");
        unit.setCellValueFactory(c -> new SimpleStringProperty(pkr(c.getValue().line.getPackCost())));

        TableColumn<Row, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(c -> Bindings.createStringBinding(() -> pkr(c.getValue().total()), c.getValue().qty));

        suggestionTable.getColumns().setAll(List.of(sku, sup, stock, qty, unit, total));

        VBox c = card();
        c.setPadding(new Insets(0));
        c.getChildren().add(suggestionTable);
        return c;
    }

    private Node breakdownCard() {
        VBox c = card();
        c.getChildren().addAll(title("Supplier Breakdown"), breakdownBox);
        return c;
    }

    private Node actionBar() {
        Button reset = PharmacyDesignSystem.ghostButton("Reset Quantities");
        reset.setOnAction(e -> rows.forEach(r -> r.qty.set(r.original)));
        Button discard = PharmacyDesignSystem.secondaryButton("Discard Suggestions");
        discard.setOnAction(e -> { rows.clear(); refreshSummary(); });
        createAllBtn = PharmacyDesignSystem.greenButton("Create Purchase Orders");
        createAllBtn.setOnAction(e -> createOrders(new ArrayList<>(rows)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox h = new HBox(10, spacer, reset, discard, createAllBtn);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    /* ============================================================
       Purchase Orders — Section
       ============================================================ */
    private Node buildPurchaseOrdersSection() {
        VBox section = new VBox(0);
        section.getStyleClass().add("po-section");

        /* ---- Header ---- */
        Label secTitle = AppUIManager.sectionTitle("View Orders");
//        secTitle.getStyleClass().add("po-section-title");

        Label sub = new Label("Create, review, receive, and cancel supplier purchase orders.");
        sub.setStyle("-fx-font-weight: bold;");

        VBox titleBox = new VBox(3, secTitle, sub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button refreshBtn = new Button("⟳  Refresh");
        refreshBtn.getStyleClass().add("po-ghost-btn");
        refreshBtn.setOnAction(e -> loadOrders());

        HBox titleRow = new HBox(12, titleBox, sp, refreshBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 16, 0));
        titleRow.getStyleClass().add("po-header-row");

        /* ---- Stat cards ---- */
        totalOrdersLabel     = metricValueLabel("0", "#0F172A");
        pendingOrdersLabel   = metricValueLabel("0", "#B45309");
        receivedOrdersLabel  = metricValueLabel("0", "#047857");
        cancelledOrdersLabel = metricValueLabel("0", "#475569");
        totalValueLabel      = metricValueLabel(pkr(0), "#1D4ED8");

        HBox summaryRow = new HBox(12,
                statCard("Total Orders", totalOrdersLabel, "#1D4ED8"),
                statCard("Open",         pendingOrdersLabel, "#F59E0B"),
                statCard("Received",     receivedOrdersLabel, "#059669"),
                statCard("Cancelled",    cancelledOrdersLabel, "#94A3B8"),
                statCard("Total Cost",   totalValueLabel, "#0EA5E9"));
        totalOrdersLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #1D4ED8;");
        pendingOrdersLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #F59E0B;");
        receivedOrdersLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #059669;");
        cancelledOrdersLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        totalValueLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #0EA5E9;");


        summaryRow.getChildren().forEach(card -> {
            if (card instanceof HBox hb) hb.getChildren().forEach(child -> {
                if (child instanceof VBox vb) vb.getChildren().forEach(c -> {
                    if (c instanceof Label lbl) lbl.setStyle(lbl.getStyle() + " -fx-font-weight: bold;");
                });
            });
        });
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(summaryRow.getChildren().get(0), Priority.ALWAYS);

        /* ---- Filter bar ---- */
        searchField = new TextField();
        searchField.setPromptText("Search PO #, supplier, creator, or status…");
        searchField.getStyleClass().add("po-search-field");
        searchField.setPrefHeight(36);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((o, a, b) -> applyFilters());

        Label searchIcon = new Label("⌕");
        searchIcon.getStyleClass().add("po-search-icon");

        HBox searchWrap = new HBox(6, searchIcon, searchField);
        searchWrap.getStyleClass().add("po-search-wrap");
        searchWrap.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchWrap, Priority.ALWAYS);

        statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All", "PENDING", "ORDERED", "RECEIVED", "CANCELLED"));
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(160);
        statusFilter.getStyleClass().add("po-status-filter");
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());

        HBox filterBar = new HBox(10, searchWrap, statusFilter);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(14, 0, 14, 0));

        /* ---- Table ---- */
        poTable = buildPOTable();
        filteredRows = new FilteredList<>(poRows, r -> true);
        poTable.setItems(filteredRows);

        VBox body = new VBox(0, summaryRow, filterBar, poTable);
        body.setPadding(new Insets(16, 20, 20, 20));

        section.getChildren().addAll(titleRow, body);
        section.setPadding(new Insets(20, 20, 8, 20));
        return section;
    }

    /* ============================================================
       Purchase Orders — Table
       ============================================================ */
    private TableView<Map<String, Object>> buildPOTable() {
        TableView<Map<String, Object>> table = new TableView<>();
        table.getStyleClass().add("po-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(emptyState("No purchase orders yet",
                "Create one with “+ New PO” or generate suggestions above."));
        table.setFixedCellSize(34);
        table.setPrefHeight(430);

        TableColumn<Map<String, Object>, String> idCol = new TableColumn<>("PO #");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + number(d.getValue(), "po_id").intValue()));
        idCol.setMaxWidth(70);
        idCol.setStyle("-fx-font-weight: bold; -fx-text-fill: #1D4ED8;");

        TableColumn<Map<String, Object>, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(d -> new SimpleStringProperty(text(d.getValue(), "supplier_name")));

        TableColumn<Map<String, Object>, Integer> itemCountCol = new TableColumn<>("Items");
        itemCountCol.setCellValueFactory(d -> new SimpleObjectProperty<>(number(d.getValue(), "item_count").intValue()));
        itemCountCol.setMaxWidth(70);
        itemCountCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Order Date");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(formatDate(d.getValue().get("order_date"))));

        TableColumn<Map<String, Object>, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(d -> new SimpleStringProperty(
                pkr(number(d.getValue(), "total_cost").doubleValue())));
        totalCol.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A; -fx-alignment: CENTER-RIGHT;");

        TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(text(d.getValue(), "status")));
        statusCol.setMaxWidth(120);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setGraphic(null); return; }
                setGraphic(statusBadge(item));
            }
        });

        TableColumn<Map<String, Object>, String> createdByCol = new TableColumn<>("Created By");
        createdByCol.setCellValueFactory(d -> new SimpleStringProperty(text(d.getValue(), "created_by_name")));
        createdByCol.setMaxWidth(180);

        TableColumn<Map<String, Object>, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(200);
        actionsCol.setMaxWidth(200);
        actionsCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn    = actionButton("View",    "po-action-view");
            private final Button receiveBtn = actionButton("Receive", "po-action-receive");
            private final Button cancelBtn  = actionButton("Cancel",  "po-action-cancel");
            private final HBox box = new HBox(6, viewBtn, receiveBtn, cancelBtn);
            { box.setAlignment(Pos.CENTER_RIGHT); }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                boolean active = isActiveOrder(text(row, "status"));

                viewBtn.setOnAction(e -> showOrderDetails(row));
                receiveBtn.setDisable(!active);
                receiveBtn.setOnAction(e -> receivePo(row));
                cancelBtn.setDisable(!active);
                cancelBtn.setOnAction(e -> cancelPo(row));

                setGraphic(box);
            }
        });

        table.getColumns().addAll(idCol, supplierCol, itemCountCol, dateCol,
                totalCol, statusCol, createdByCol, actionsCol);

        table.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) showOrderDetails(row.getItem());
            });
            return row;
        });
        return table;
    }

    private void applyFilters() {
        if (filteredRows == null) return;

        String keyword = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilter == null ? "All" : statusFilter.getValue();

        filteredRows.setPredicate(row -> {
            String status = text(row, "status");
            boolean statusMatches = selectedStatus == null || "All".equals(selectedStatus)
                    || selectedStatus.equalsIgnoreCase(status);
            if (!statusMatches) return false;
            if (keyword.isEmpty()) return true;
            return String.valueOf(row.get("po_id")).contains(keyword)
                    || text(row, "supplier_name").toLowerCase().contains(keyword)
                    || text(row, "created_by_name").toLowerCase().contains(keyword)
                    || status.toLowerCase().contains(keyword);
        });

        updateSummary();
    }

    private void updateSummary() {
        if (filteredRows == null) return;

        int pending = 0, received = 0, cancelled = 0;
        double totalValue = 0;

        for (Map<String, Object> row : filteredRows) {
            String status = text(row, "status");
            if (isActiveOrder(status)) pending++;
            else if ("RECEIVED".equalsIgnoreCase(status)) received++;
            else if ("CANCELLED".equalsIgnoreCase(status)) cancelled++;

            Object totalObj = row.get("total_cost");
            double orderTotal = 0;
            if (totalObj instanceof Number && ((Number) totalObj).doubleValue() > 0) {
                orderTotal = ((Number) totalObj).doubleValue();
            } else {
                int poId = number(row, "po_id").intValue();
                try {
                    List<POItem> items = poDAO.getPOItems(poId);
                    orderTotal = items.stream()
                            .mapToDouble(item -> item.qtyOrdered * item.packCost).sum();
                    row.put("total_cost", orderTotal);
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(PurchaseOrderView.class.getName()).log(
                            java.util.logging.Level.WARNING, "Could not calculate order total for PO #" + poId, ex);
                }
            }
            totalValue += orderTotal;
        }

        totalOrdersLabel.setText(String.valueOf(filteredRows.size()));
        pendingOrdersLabel.setText(String.valueOf(pending));
        receivedOrdersLabel.setText(String.valueOf(received));
        cancelledOrdersLabel.setText(String.valueOf(cancelled));
        totalValueLabel.setText(pkr(totalValue));

        if (poTable != null) poTable.refresh();
    }

    /* ============================================================
       Order Details dialog
       ============================================================ */
    private void showOrderDetails(Map<String, Object> order) {
        int poId = number(order, "po_id").intValue();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Purchase Order Details");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(18));
        content.setPrefWidth(780);

        /* Header */
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label("PO #" + poId);
        t.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header.getChildren().addAll(t, statusBadge(text(order, "status")), headerSpacer);

        /* Info grid */
        GridPane info = new GridPane();
        info.setHgap(18);
        info.setVgap(8);
        info.add(fieldLabel("Supplier"), 0, 0);
        info.add(metricValueLabel(text(order, "supplier_name"), "#111827"), 1, 0);
        info.add(fieldLabel("Created By"), 2, 0);
        info.add(metricValueLabel(text(order, "created_by_name"), "#111827"), 3, 0);
        info.add(fieldLabel("Order Date"), 0, 1);
        info.add(metricValueLabel(formatDate(order.get("order_date")), "#111827"), 1, 1);
        info.add(fieldLabel("Total"), 2, 1);
        info.add(metricValueLabel(pkr(number(order, "total_cost").doubleValue()), "#111827"), 3, 1);

        /* Items table */
        TableView<POItem> itemsTable = buildPOItemsTable();
        try {
            itemsTable.setItems(FXCollections.observableArrayList(poDAO.getPOItems(poId)));
        } catch (Exception ex) {
            AlertUtil.showError("Error", "Could not load purchase order items: " + ex.getMessage());
        }
        itemsTable.setFixedCellSize(34);
        itemsTable.setPrefHeight(Math.min(60 + itemsTable.getItems().size() * 34, 320));

        Label itemsTitle = new Label("Ordered Items");
        itemsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label countPill = new Label(itemsTable.getItems().size() + " item" +
                (itemsTable.getItems().size() == 1 ? "" : "s"));
        countPill.setStyle("-fx-background-color:#EFF6FF; -fx-text-fill:#1D4ED8; -fx-font-size:11px; "
                + "-fx-font-weight:bold; -fx-padding:2 10; -fx-background-radius:10;");

        Region itemsSpacer = new Region();
        HBox.setHgrow(itemsSpacer, Priority.ALWAYS);
        HBox itemsHeader = new HBox(8, itemsTitle, countPill, itemsSpacer);
        itemsHeader.setAlignment(Pos.CENTER_LEFT);

        double grand = itemsTable.getItems().stream()
                .mapToDouble(i -> i.qtyOrdered * i.packCost).sum();
        Label totalCaption = new Label("Grand Total");
        totalCaption.setStyle("-fx-font-size:12px; -fx-text-fill:#6B7280;");
        Label totalAmt = new Label(pkr(grand));
        totalAmt.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#059669;");
        VBox totalBox = new VBox(2, totalCaption, totalAmt);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Region totSpacer = new Region();
        HBox.setHgrow(totSpacer, Priority.ALWAYS);
        HBox totalRow = new HBox(8, totSpacer, totalBox);
        totalRow.setPadding(new Insets(8, 4, 0, 4));

        VBox itemsCard = new VBox(10, itemsHeader, itemsTable, totalRow);
        itemsCard.setPadding(new Insets(14));
        itemsCard.setStyle("-fx-background-color:#FFFFFF; -fx-background-radius:10; "
                + "-fx-border-color:#E5E7EB; -fx-border-radius:10; -fx-border-width:1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 6, 0, 0, 2);");

        /* Actions */
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (isActiveOrder(text(order, "status"))) {
            Button receiveBtn = AppUIManager.primaryButton("Receive Stock");
            receiveBtn.setOnAction(e -> { dialog.close(); receivePo(order); });
            Button cancelBtn = dangerButton("Cancel Order");
            cancelBtn.setOnAction(e -> { dialog.close(); cancelPo(order); });
            actions.getChildren().addAll(cancelBtn, receiveBtn);
        }

        content.getChildren().addAll(header, info, itemsCard, actions);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private TableView<POItem> buildPOItemsTable() {
        TableView<POItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No items added"));
        table.setFixedCellSize(34);
        table.getStyleClass().add("po-items-table");

        TableColumn<POItem, String> prodCol = new TableColumn<>("Product");
        prodCol.setCellValueFactory(data -> {
            POItem item = data.getValue();
            String name = item.productName == null || item.productName.isBlank()
                    ? "Product - " + item.productId : item.productName;
            return new SimpleStringProperty(name);
        });

        TableColumn<POItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().qtyOrdered));
        qtyCol.setMaxWidth(80);
        qtyCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<POItem, String> unitCol = new TableColumn<>("Unit Cost");
        unitCol.setCellValueFactory(data -> new SimpleStringProperty(pkr(data.getValue().unitCost)));
        unitCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<POItem, String> packCol = new TableColumn<>("Pack Cost");
        packCol.setCellValueFactory(data -> new SimpleStringProperty(pkr(data.getValue().packCost)));
        packCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<POItem, String> lineTotalCol = new TableColumn<>("Line Value");
        lineTotalCol.setCellValueFactory(data ->
                new SimpleStringProperty(pkr(data.getValue().qtyOrdered * data.getValue().packCost)));
        lineTotalCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #111827;");

        table.getColumns().addAll(prodCol, qtyCol, unitCol, packCol, lineTotalCol);
        return table;
    }

    /* ============================================================
       Generation / validation
       ============================================================ */
    private void generate() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null || !user.canManageInventory()) {
            AlertUtil.showError("Not allowed", "You do not have permission to create purchase orders.");
            return;
        }
        generateBtn.setDisable(true);
        statusLabel.setText("Analysing stock and sales…");
        long start = System.currentTimeMillis();
        UiTasks.run(() -> new Generated(inventoryService.generateSuggestedPOs(user.getUserId()),
                        productDAO.getLowStock().size(), System.currentTimeMillis() - start),
                g -> {
                    List<Row> built = new ArrayList<>();
                    for (SuggestedPO po : g.pos()) {
                        for (SuggestedPO.LineItem li : po.getLines()) built.add(new Row(li, po.getSupplier()));
                    }
                    built.forEach(r -> r.qty.addListener((o, a, n) -> refreshSummary()));
                    rows.setAll(built);
                    lowStockProducts = g.lowStockProducts();
                    generatedMillis = g.millis();
                    generatedOnce = true;
                    generateBtn.setDisable(false);
                    statusLabel.setText("");
                    refreshSummary();
                },
                err -> {
                    generateBtn.setDisable(false);
                    statusLabel.setText("");
                    AlertUtil.showError("Generation failed", err.getMessage());
                });
    }

    private void refreshSummary() {
        int skus = rows.size();
        long units = rows.stream().mapToLong(r -> r.qty.get()).sum();
        double value = rows.stream().mapToDouble(Row::total).sum();
        skuValue.setText(String.valueOf(skus));
        unitsValue.setText(String.format("%,d", units));
        totalValue.setText(pkr(value));

        if (!generatedOnce) {
            headline.setText("No suggestions generated yet");
            subline.setText("Press Generate Suggestions to analyse low-stock products.");
        } else if (skus == 0) {
            headline.setText("Nothing to reorder");
            subline.setText("No low-stock product with an assigned supplier was found.");
        } else {
            headline.setText("Suggested Purchase Order");
            subline.setText(String.format("Generated in %.1f s · quantity = 30-day sales rate × supplier lead time × 1.5 − stock, minimum 2× reorder level.",
                    generatedMillis / 1000.0));
        }
        if (createAllBtn != null) createAllBtn.setDisable(rows.isEmpty());
        rebuildBreakdown();
        rebuildValidation();
    }

    private void rebuildBreakdown() {
        breakdownBox.getChildren().clear();
        if (rows.isEmpty()) {
            breakdownBox.getChildren().add(muted("Suppliers appear here after suggestions are generated."));
            return;
        }
        Map<Integer, List<Row>> bySupplier = rows.stream()
                .collect(Collectors.groupingBy(r -> r.supplier.getSupplierId(), LinkedHashMap::new, Collectors.toList()));
        for (List<Row> group : bySupplier.values()) {
            Supplier s = group.get(0).supplier;
            double total = group.stream().mapToDouble(Row::total).sum();
            TitledPane pane = new TitledPane(String.format("Supplier: %s (%d SKUs, Total: %s)", s.getName(), group.size(), pkr(total)),
                    groupContent(group));
            pane.setExpanded(false);
            breakdownBox.getChildren().add(pane);
        }
    }

    private Node groupContent(List<Row> group) {
        VBox box = new VBox(6);
        for (Row r : group) {
            box.getChildren().add(muted(String.format("%s × %d = %s", r.line.getProductName(), r.qty.get(), pkr(r.total()))));
        }
        Button create = AppUIManager.primaryButton("Create PO for this supplier");
        create.setOnAction(e -> createOrders(group));
        box.getChildren().add(create);
        return box;
    }

    private void rebuildValidation() {
        validationBox.getChildren().clear();
        if (!generatedOnce) {
            validationBox.getChildren().add(muted("Checks run after suggestions are generated."));
            return;
        }
        long suppliers = rows.stream().map(r -> r.supplier.getSupplierId()).distinct().count();
        long edited = rows.stream().filter(r -> r.qty.get() != r.original).count();
        long zeroCost = rows.stream().filter(r -> r.line.getPackCost() <= 0).count();
        int excluded = Math.max(0, lowStockProducts - rows.size());
        long outOfStock = rows.stream().filter(r -> r.line.getCurrentStock() <= 0).count();

        validationBox.getChildren().add(note("INFO", "Supplier grouping",
                rows.size() + " SKU(s) grouped into " + suppliers + " supplier(s)."));
        validationBox.getChildren().add(zeroCost > 0
                ? note("WARNING", "Cost data missing", zeroCost + " SKU(s) have no unit cost, so totals are understated.")
                : note("SUCCESS", "Cost data complete", "Every suggested line has a unit cost."));
        validationBox.getChildren().add(excluded > 0
                ? note("WARNING", "Risk flag", excluded + " low-stock product(s) have no supplier assigned and were excluded.")
                : note("SUCCESS", "Supplier coverage", "All low-stock products have a supplier."));
        validationBox.getChildren().add(note(outOfStock > 0 ? "DANGER" : "SUCCESS", "Stock-outs",
                outOfStock > 0 ? outOfStock + " suggested SKU(s) are completely out of stock." : "No suggested SKU is out of stock."));
        validationBox.getChildren().add(note("INFO", "Manual edits",
                edited == 0 ? "Quantities match the calculated suggestion." : edited + " line(s) differ from the suggestion."));
    }

    /* ============================================================
       Creating / managing POs
       ============================================================ */
    private void createOrders(List<Row> selected) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null || !user.canManageInventory()) {
            AlertUtil.showError("Not allowed", "You do not have permission to create purchase orders.");
            return;
        }
        if (selected.isEmpty()) return;
        Map<Supplier, List<Row>> bySupplier = new LinkedHashMap<>();
        for (Row r : selected) {
            bySupplier.computeIfAbsent(r.supplier, k -> new ArrayList<>()).add(r);
        }
        if (!AlertUtil.showConfirm("Create Purchase Orders",
                "Create " + bySupplier.size() + " pending purchase order(s) totalling "
                        + pkr(selected.stream().mapToDouble(Row::total).sum()) + "?")) return;

        UiTasks.run(() -> {
            List<Integer> ids = new ArrayList<>();
            for (Map.Entry<Supplier, List<Row>> e : bySupplier.entrySet()) {
                List<POItem> items = e.getValue().stream().map(r -> new POItem(r.line.getProductId(),
                        r.line.getProductName(), r.qty.get(), r.line.getUnitCost(), r.line.getPackCost())).toList();
                ids.add(poDAO.createPO(e.getKey().getSupplierId(), user.getUserId(), items));
            }
            return ids;
        }, ids -> {
            ids.forEach(id -> AuditLogger.log("PO_CREATED", "purchase_orders", id, "Purchase order created from view"));
            rows.removeAll(selected);
            refreshSummary();
            loadOrders();
            AlertUtil.showInfo("Purchase orders created", ids.size() + " purchase order(s) created with status PENDING.");
        }, err -> AlertUtil.showError("Could not create purchase orders", err.getMessage()));
    }

    private void loadOrders() {
        UiTasks.run(poDAO::getAllPOs,
                list -> {
                    poRows.setAll(list);
                    applyFilters();
                },
                err -> AlertUtil.showError("Purchase orders", "Could not load purchase orders: " + err.getMessage()));
    }

    private void receivePo(Map<String, Object> po) {
        int id = (Integer) po.get("po_id");
        Dialog<LocalDate> d = new Dialog<>();
        d.setTitle("Receive PO #" + id);
        d.setHeaderText("Receiving adds every line to stock. Expiry-tracked items need an expiry date.");
        DatePicker expiry = new DatePicker();
        expiry.setPromptText("Expiry date (for tracked items)");
        d.getDialogPane().setContent(new VBox(8, new Label("Expiry date"), expiry));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(b -> b == ButtonType.OK ? (expiry.getValue() == null ? LocalDate.MIN : expiry.getValue()) : null);
        d.showAndWait().ifPresent(date -> UiTasks.run(() -> {
            poDAO.receivePO(id, date == LocalDate.MIN ? null : date);
            return id;
        }, ok -> {
            AuditLogger.log("PO_RECEIVED", "purchase_orders", id, "Purchase order received");
            loadOrders();
            AlertUtil.showInfo("Received", "PO #" + id + " received and stock updated.");
        }, err -> AlertUtil.showError("Receive failed", err.getMessage())));
    }

    private void cancelPo(Map<String, Object> po) {
        int id = (Integer) po.get("po_id");
        if (!AlertUtil.showConfirm("Cancel PO #" + id, "Cancel this purchase order?")) return;
        UiTasks.run(() -> { poDAO.cancelPO(id); return id; }, ok -> {
            AuditLogger.log("PO_CANCELLED", "purchase_orders", id, "Purchase order cancelled");
            loadOrders();
        }, err -> AlertUtil.showError("Cancel failed", err.getMessage()));
    }

    /* ============================================================
       Manual PO dialog
       ============================================================ */
    private void showNewPoDialog() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null || !user.canManageInventory()) {
            AlertUtil.showError("Not allowed", "You do not have permission to create purchase orders.");
            return;
        }
        List<Supplier> suppliers;
        try { suppliers = supplierDAO.getAll(); }
        catch (Exception e) { AlertUtil.showError("Suppliers", e.getMessage()); return; }

        ComboBox<Supplier> supplierBox = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplierBox.setPromptText("Select supplier");
        ComboBox<Product> productBox = new ComboBox<>();
        productBox.setPromptText("Select product");
        productBox.setPrefWidth(320);
        Spinner<Integer> qty = new Spinner<>(1, 1_000_000, 10);
        qty.setEditable(true);
        TextField cost = new TextField();
        cost.setPromptText("Unit cost");
        ObservableList<POItem> lines = FXCollections.observableArrayList();

        supplierBox.setOnAction(e -> {
            lines.clear();
            productBox.getItems().clear();
            if (supplierBox.getValue() == null) return;
            try {
                productBox.setItems(FXCollections.observableArrayList(
                        productDAO.getBySupplier(supplierBox.getValue().getSupplierId())));
            } catch (Exception ex) { AlertUtil.showError("Products", ex.getMessage()); }
        });
        productBox.setOnAction(e -> {
            if (productBox.getValue() != null) cost.setText(String.valueOf(productBox.getValue().getUnitCost()));
        });

        TableView<POItem> table = new TableView<>(lines);
        table.setPrefHeight(180);
        table.setFixedCellSize(34);
        table.getStyleClass().add("po-items-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<POItem, String> n = new TableColumn<>("Product");
        n.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().productName));
        TableColumn<POItem, String> q = new TableColumn<>("Qty");
        q.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().qtyOrdered)));
        TableColumn<POItem, String> t = new TableColumn<>("Line Total");
        t.setCellValueFactory(c -> new SimpleStringProperty(pkr(c.getValue().qtyOrdered * c.getValue().packCost)));
        table.getColumns().setAll(List.of(n, q, t));
        table.setPlaceholder(new Label("Add at least one line."));

        Button add = AppUIManager.primaryButton("Add Line");
        add.setOnAction(e -> {
            Product p = productBox.getValue();
            double unit;
            try { unit = Double.parseDouble(cost.getText().trim()); }
            catch (Exception ex) { AlertUtil.showError("New PO", "Enter a valid unit cost."); return; }
            if (p == null || unit < 0 || qty.getValue() == null || qty.getValue() <= 0) {
                AlertUtil.showError("New PO", "Select a product, a positive quantity and a valid cost.");
                return;
            }
            lines.removeIf(l -> l.productId == p.getProductId());
            lines.add(new POItem(p.getProductId(), p.getName(), qty.getValue(), unit, unit));
        });
        Button remove = AppUIManager.outlineButton("Remove Selected");
        remove.setOnAction(e -> { POItem s = table.getSelectionModel().getSelectedItem(); if (s != null) lines.remove(s); });

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("New Purchase Order");
        VBox content = new VBox(10, new Label("Supplier"), supplierBox, new Label("Product"), productBox,
                new HBox(10, new VBox(4, new Label("Quantity"), qty), new VBox(4, new Label("Unit cost"), cost), add),
                table, remove);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Create PO");
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            if (supplierBox.getValue() == null || lines.isEmpty()) {
                ev.consume();
                AlertUtil.showError("New PO", "Select a supplier and add at least one line.");
            }
        });

        d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            int supplierId = supplierBox.getValue().getSupplierId();
            List<POItem> items = new ArrayList<>(lines);
            UiTasks.run(() -> poDAO.createPO(supplierId, user.getUserId(), items), id -> {
                AuditLogger.log("PO_CREATED", "purchase_orders", id, "Manual purchase order created");
                loadOrders();
                AlertUtil.showInfo("Purchase order created", "PO #" + id + " created with status PENDING.");
            }, err -> AlertUtil.showError("Could not create PO", err.getMessage()));
        });
    }

    /* ============================================================
       Small UI helpers — general
       ============================================================ */
    private Node note(String level, String heading, String text) {
        String[] pal = switch (level) {
            case "DANGER"  -> new String[]{"#FEE2E2", "#FCA5A5", "#B91C1C"};
            case "WARNING" -> new String[]{"#FEF3C7", "#FCD34D", "#B45309"};
            case "SUCCESS" -> new String[]{"#DCFCE7", "#86EFAC", "#15803D"};
            default        -> new String[]{"#DBEAFE", "#93C5FD", "#1D4ED8"};
        };
        Label h = new Label(heading);
        h.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + pal[2] + ";");
        Label b = new Label(text);
        b.setWrapText(true);
        b.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        VBox box = new VBox(4, h, b);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: " + pal[0] + "; -fx-border-color: " + pal[1]
                + "; -fx-border-radius: 10; -fx-background-radius: 10;");
        return box;
    }

    private VBox stat(String caption, Label value) {
        Label c = new Label(caption);
        c.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return new VBox(2, c, value);
    }

    private Label bigNumber() {
        Label l = new Label("0");
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return l;
    }

    private Label title(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return l;
    }

    private Label muted(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return l;
    }

    private VBox card() {
        VBox c = new VBox(12);
        c.setPadding(new Insets(18));
        c.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: "
                + AppUIManager.COLOR_BORDER + "; -fx-border-radius: 12; -fx-border-width: 1;");
        return c;
    }

    /* ============================================================
       Small UI helpers — Purchase Orders
       ============================================================ */
    private Label metricValueLabel(String text, String color) {
        Label l = new Label(text);
        l.getStyleClass().add("po-stat-value");
        l.setStyle("-fx-text-fill: " + color + ";");
        return l;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        return l;
    }

    private Node statCard(String caption, Label value, String accent) {
        Region accentBar = new Region();
        accentBar.setPrefWidth(4);
        accentBar.setMinWidth(4);
        accentBar.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 4;");

        Label cap = new Label(caption);
        cap.getStyleClass().add("po-stat-label");

        VBox textCol = new VBox(3, cap, value);
        HBox card = new HBox(12, accentBar, textCol);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("po-stat-card");
        card.setPadding(new Insets(12, 16, 12, 14));
        card.setMinWidth(150);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Button actionButton(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("po-action-btn", styleClass);
        b.setFocusTraversable(false);
        return b;
    }

    private Button dangerButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-border-color: #FCA5A5; "
                + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-padding: 4 10; -fx-cursor: hand;");
        return b;
    }

    private HBox statusBadge(String status) {
        String dot, bg, fg;
        if ("PENDING".equalsIgnoreCase(status) || "ORDERED".equalsIgnoreCase(status)) {
            dot = "#F59E0B"; bg = "#FEF3C7"; fg = "#92400E";
        } else if ("RECEIVED".equalsIgnoreCase(status)) {
            dot = "#10B981"; bg = "#D1FAE5"; fg = "#065F46";
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            dot = "#94A3B8"; bg = "#F1F5F9"; fg = "#475569";
        } else {
            dot = "#94A3B8"; bg = "#F1F5F9"; fg = "#475569";
        }
        Circle c = new Circle(3.5, Color.web(dot));
        Label l = new Label(status);
        l.setStyle("-fx-text-fill: " + fg + "; -fx-font-weight: bold; -fx-font-size: 10.5px;");
        HBox box = new HBox(6, c, l);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(3, 10, 3, 10));
        box.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 20;");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private Node emptyState(String title, String sub) {
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #334155;");
        Label s = new Label(sub);
        s.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
        VBox box = new VBox(4, t, s);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private boolean isActiveOrder(String status) {
        return !"RECEIVED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status);
    }

    private String text(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }

    private Number number(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number ? (Number) v : 0;
    }

    private String formatDate(Object value) {
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().format(PO_DATE_FMT);
        return value == null ? "-" : value.toString();
    }

    private static String pkr(double v) { return String.format("Rs %,.2f", v); }
}

package com.pharmacy.view;

import com.pharmacy.dao.ProductDAO;
import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.dao.PurchaseOrderDAO.POItem;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Product;
import com.pharmacy.model.Supplier;
import com.pharmacy.model.User;
import com.pharmacy.service.InventoryService;
import com.pharmacy.service.InventoryService.SuggestedPO;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import com.pharmacy.util.UiTasks;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PurchaseOrderView {

    /** One editable suggestion line. */
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

    private final InventoryService inventoryService = new InventoryService();
    private final PurchaseOrderDAO poDAO = new PurchaseOrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> orders = FXCollections.observableArrayList();
    private List<Map<String, Object>> allOrders = List.of();

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
    private TableView<Map<String, Object>> orderTable;
    private Button generateBtn, createAllBtn;
    private ComboBox<String> statusFilter;
    private DatePicker dateFilter;

    public Node getView() {
        VBox page = new VBox(16);
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
        page.getChildren().addAll(toolbar(), top, ordersCard());

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + "; -fx-background: " + AppUIManager.COLOR_BG + ";");

        refreshSummary();
        loadOrders();
        return scroll;
    }

    // ── Layout ───────────────────────────────────────────────

    private Node toolbar() {
        Label pageTitle = AppUIManager.pageTitle("Purchase Orders");
        Button newPo = AppUIManager.outlineButton("+ New PO");
        newPo.setOnAction(e -> showNewPoDialog());
        generateBtn = AppUIManager.primaryButton("Generate Suggestions");
        generateBtn.setOnAction(e -> generate());

        statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "PENDING", "ORDERED", "RECEIVED", "CANCELLED"));
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> applyOrderFilters());
        dateFilter = new DatePicker();
        dateFilter.setPromptText("Order date");
        dateFilter.setOnAction(e -> applyOrderFilters());
        Button clear = AppUIManager.outlineButton("Clear");
        clear.setOnAction(e -> { statusFilter.setValue("All"); dateFilter.setValue(null); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        HBox bar = new HBox(10, pageTitle, newPo, generateBtn, spacer, statusLabel,
                new Label("Status"), statusFilter, dateFilter, clear);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

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

    private Node suggestionCard() {
        suggestionTable = new TableView<>(rows);
        AppUIManager.styleTable(suggestionTable);
        suggestionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        suggestionTable.setPrefHeight(300);
        suggestionTable.setPlaceholder(new Label("No suggestions. Press Generate Suggestions."));

        TableColumn<Row, String> sku = new TableColumn<>("SKU Name");
        sku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().line.getProductName()));

        TableColumn<Row, String> sup = new TableColumn<>("Supplier");
        sup.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().supplier.getName()));

        TableColumn<Row, Row> stock = new TableColumn<>("Current Stock");
        stock.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
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
        Button reset = AppUIManager.outlineButton("Reset Quantities");
        reset.setOnAction(e -> rows.forEach(r -> r.qty.set(r.original)));
        Button discard = AppUIManager.outlineButton("Discard Suggestions");
        discard.setOnAction(e -> { rows.clear(); refreshSummary(); });
        createAllBtn = AppUIManager.primaryButton("Create Purchase Orders");
        createAllBtn.setOnAction(e -> createOrders(new ArrayList<>(rows)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox h = new HBox(10, spacer, reset, discard, createAllBtn);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private Node ordersCard() {
        orderTable = new TableView<>(orders);
        AppUIManager.styleTable(orderTable);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        orderTable.setPrefHeight(320);
        orderTable.setPlaceholder(new Label("No purchase orders match the current filters."));

        orderTable.getColumns().setAll(List.of(
                mapCol("PO #", "po_id", v -> "#" + v),
                mapCol("Supplier", "supplier_name", String::valueOf),
                mapCol("Created By", "created_by_name", String::valueOf),
                mapCol("Order Date", "order_date", v -> v == null ? "—"
                        : ((java.sql.Timestamp) v).toLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))),
                statusCol(),
                mapCol("Items", "item_count", String::valueOf),
                mapCol("Total", "total_cost", v -> pkr(((Number) v).doubleValue())),
                actionsCol()));

        VBox c = card();
        c.getChildren().addAll(title("Purchase Order History"), orderTable);
        return c;
    }

    // ── Generation / validation ──────────────────────────────

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

    // ── Creating / managing POs ──────────────────────────────

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
        UiTasks.run(poDAO::getAllPOs, list -> { allOrders = list; applyOrderFilters(); },
                err -> AlertUtil.showError("Purchase orders", "Could not load purchase orders: " + err.getMessage()));
    }

    private void applyOrderFilters() {
        if (statusFilter == null) return;
        String st = statusFilter.getValue();
        LocalDate day = dateFilter.getValue();
        orders.setAll(allOrders.stream().filter(o -> {
            boolean s = st == null || "All".equals(st) || st.equals(o.get("status"));
            boolean d = day == null || (o.get("order_date") instanceof java.sql.Timestamp ts
                    && ts.toLocalDateTime().toLocalDate().equals(day));
            return s && d;
        }).toList());
    }

    private TableColumn<Map<String, Object>, String> statusCol() {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>("Status");
        c.setCellValueFactory(x -> new SimpleStringProperty(String.valueOf(x.getValue().get("status"))));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String color = switch (s) {
                    case "RECEIVED" -> AppUIManager.COLOR_SUCCESS;
                    case "CANCELLED" -> AppUIManager.COLOR_DANGER;
                    default -> AppUIManager.COLOR_WARNING;
                };
                setGraphic(AppUIManager.badge(s, color));
            }
        });
        return c;
    }

    private TableColumn<Map<String, Object>, Void> actionsCol() {
        TableColumn<Map<String, Object>, Void> c = new TableColumn<>("Actions");
        c.setCellFactory(col -> new TableCell<>() {
            private final Button view = AppUIManager.outlineButton("View");
            private final Button receive = AppUIManager.primaryButton("Receive");
            private final Button cancel = AppUIManager.outlineButton("Cancel");
            private final HBox box = new HBox(6, view, receive, cancel);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                view.setOnAction(e -> showItems(current()));
                receive.setOnAction(e -> receivePo(current()));
                cancel.setOnAction(e -> cancelPo(current()));
            }
            private Map<String, Object> current() { return getTableView().getItems().get(getIndex()); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                String st = String.valueOf(current().get("status"));
                boolean open = "PENDING".equals(st) || "ORDERED".equals(st);
                receive.setVisible(open); receive.setManaged(open);
                cancel.setVisible(open); cancel.setManaged(open);
                setGraphic(box);
            }
        });
        c.setMinWidth(230);
        return c;
    }

    private void showItems(Map<String, Object> po) {
        int id = (Integer) po.get("po_id");
        UiTasks.run(() -> poDAO.getPOItems(id), items -> {
            String text = items.stream().map(i -> String.format("%s — %d × %s", i.productName, i.qtyOrdered, pkr(i.packCost)))
                    .collect(Collectors.joining("\n"));
            AlertUtil.showInfo("PO #" + id + " — " + po.get("supplier_name"), text.isEmpty() ? "No items." : text);
        }, err -> AlertUtil.showError("Purchase order", err.getMessage()));
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

    // ── Manual PO dialog ─────────────────────────────────────

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

    // ── Small UI helpers ─────────────────────────────────────

    private TableColumn<Map<String, Object>, String> mapCol(String name, String key,
                                                            java.util.function.Function<Object, String> fmt) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(name);
        c.setCellValueFactory(x -> new SimpleStringProperty(fmt.apply(x.getValue().get(key))));
        return c;
    }

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

    private static String pkr(double v) { return String.format("PKR %,.2f", v); }
}

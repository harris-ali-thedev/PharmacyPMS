package com.pharmacy.view.supplier;

import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PurchaseOrderView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.US);

    private final PurchaseOrderDAO purchaseOrderDAO = new PurchaseOrderDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ObservableList<Map<String, Object>> poRows = FXCollections.observableArrayList();

    private TableView<Map<String, Object>> poTable;
    private FilteredList<Map<String, Object>> filteredRows;
    private TextField searchField;
    private ComboBox<String> statusFilter;
    private Label totalOrdersLabel;
    private Label pendingOrdersLabel;
    private Label receivedOrdersLabel;
    private Label cancelledOrdersLabel;
    private Label totalValueLabel;

    public Node getView() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(14);
        content.getChildren().addAll(buildHeader(), buildSummaryRow(), buildToolbar());

        poTable = buildPOTable();
        filteredRows = new FilteredList<>(poRows, row -> true);
        poTable.setItems(filteredRows);
        VBox.setVgrow(poTable, Priority.ALWAYS);

        VBox tableCard = card();
        tableCard.getChildren().addAll(sectionTitle("Purchase Orders"), poTable);
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        content.getChildren().add(tableCard);

        pane.setCenter(AppUIManager.scrollWrap(content));
        loadData();
        return pane;
    }

    private Node buildHeader() {
        Label title = new Label("Purchase Orders");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Create, review, receive, and cancel supplier purchase orders.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        Button createBtn = primaryButton("+ New Purchase Order");
        createBtn.setOnAction(e -> showCreatePODialog());

        Button refreshBtn = secondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox titleBox = new VBox(3, title, subtitle);
        HBox header = new HBox(12, titleBox, spacer, refreshBtn, createBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildSummaryRow() {
        totalOrdersLabel = valueLabel("0");
        pendingOrdersLabel = valueLabel("0");
        receivedOrdersLabel = valueLabel("0");
        cancelledOrdersLabel = valueLabel("0");
        totalValueLabel = valueLabel("Rs 0.00");

        HBox row = new HBox(10,
                metricCard("Total Orders", totalOrdersLabel, "#111827"),
                metricCard("Open", pendingOrdersLabel, "#D97706"),
                metricCard("Received", receivedOrdersLabel, "#059669"),
                metricCard("Cancelled", cancelledOrdersLabel, "#6B7280"),
                metricCard("Total Cost", totalValueLabel, "#185FA5")
        );
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildToolbar() {
        searchField = new TextField();
        searchField.setPromptText("Search PO, supplier, creator, or status");
        searchField.setPrefHeight(36);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "PENDING", "ORDERED", "RECEIVED", "CANCELLED"));
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(160);
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        HBox toolbar = new HBox(10, searchField, statusFilter);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private void loadData() {
        try {
            poRows.setAll(purchaseOrderDAO.getAllPOs());
            applyFilters();
            updateSummary();
        } catch (SQLException ex) {
            AlertUtil.showError("Error", "Failed to load purchase orders: " + ex.getMessage());
        }
    }


    private void applyFilters() {
        if (filteredRows == null) {
            return;
        }

        String keyword = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilter == null ? "All" : statusFilter.getValue();

        filteredRows.setPredicate(row -> {
            String status = text(row, "status");
            boolean statusMatches = selectedStatus == null || "All".equals(selectedStatus) || selectedStatus.equalsIgnoreCase(status);
            if (!statusMatches) {
                return false;
            }
            if (keyword.isEmpty()) {
                return true;
            }
            return String.valueOf(row.get("po_id")).contains(keyword)
                    || text(row, "supplier_name").toLowerCase().contains(keyword)
                    || text(row, "created_by_name").toLowerCase().contains(keyword)
                    || status.toLowerCase().contains(keyword);
        });
    }

    private void updateSummary() {
        int pending = 0;
        int received = 0;
        int cancelled = 0;
        double totalValue = 0;

        // CHANGED: Loop over the currently visible rows, not the entire database
        for (Map<String, Object> row : filteredRows) {
            String status = text(row, "status");
            if (isActiveOrder(status)) {
                pending++;
            } else if ("RECEIVED".equalsIgnoreCase(status)) {
                received++;
            } else if ("CANCELLED".equalsIgnoreCase(status)) {
                cancelled++;
            }

            Object totalObj = row.get("total_cost");
            double orderTotal = 0;

            if (totalObj instanceof Number && ((Number) totalObj).doubleValue() > 0) {
                orderTotal = ((Number) totalObj).doubleValue();
            } else {
                int poId = number(row, "po_id").intValue();
                try {
                    List<PurchaseOrderDAO.POItem> items = purchaseOrderDAO.getPOItems(poId);
                    orderTotal = items.stream().mapToDouble(item -> item.qtyOrdered * item.packCost).sum();
                    row.put("total_cost", orderTotal);
                } catch (SQLException ex) {
                    System.err.println("Could not calculate order total for PO #" + poId + ": " + ex.getMessage());
                }
            }

            totalValue += orderTotal;
        }

        // CHANGED: Use filteredRows.size() to show the count of visible orders
        totalOrdersLabel.setText(String.valueOf(filteredRows.size()));
        pendingOrdersLabel.setText(String.valueOf(pending));
        receivedOrdersLabel.setText(String.valueOf(received));
        cancelledOrdersLabel.setText(String.valueOf(cancelled));
        totalValueLabel.setText("Rs " + money(totalValue));

        if (poTable != null) {
            poTable.refresh();
        }
    }

    private TableView<Map<String, Object>> buildPOTable() {
        TableView<Map<String, Object>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No purchase orders found"));
        table.setPrefHeight(520);

        TableColumn<Map<String, Object>, Integer> idCol = new TableColumn<>("PO #");
        idCol.setCellValueFactory(data -> new SimpleObjectProperty<>(number(data.getValue(), "po_id").intValue()));
        idCol.setMaxWidth(90);

        TableColumn<Map<String, Object>, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(data -> new SimpleStringProperty(text(data.getValue(), "supplier_name")));

        TableColumn<Map<String, Object>, Integer> itemCountCol = new TableColumn<>("Items");
        itemCountCol.setCellValueFactory(data -> new SimpleObjectProperty<>(number(data.getValue(), "item_count").intValue()));
        itemCountCol.setMaxWidth(80);

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Order Date");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().get("order_date"))));

        TableColumn<Map<String, Object>, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty("Rs " + money(number(data.getValue(), "total_cost").doubleValue())));

        TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(text(data.getValue(), "status")));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(item);
                badge.setStyle(statusStyle(item));
                setGraphic(badge);
            }
        });

        TableColumn<Map<String, Object>, String> createdByCol = new TableColumn<>("Created By");
        createdByCol.setCellValueFactory(data -> new SimpleStringProperty(text(data.getValue(), "created_by_name")));

        TableColumn<Map<String, Object>, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(270);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = smallButton("View");
            private final Button receiveBtn = smallButton("Receive");
            private final Button cancelBtn = dangerButton("Cancel");
            private final HBox actions = new HBox(6, viewBtn, receiveBtn, cancelBtn);

            {
                actions.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                Map<String, Object> row = getTableView().getItems().get(getIndex());
                int poId = number(row, "po_id").intValue();
                boolean active = isActiveOrder(text(row, "status"));

                viewBtn.setOnAction(e -> showOrderDetails(row));
                receiveBtn.setDisable(!active);
                receiveBtn.setOnAction(e -> handleReceivePO(poId));
                cancelBtn.setDisable(!active);
                cancelBtn.setOnAction(e -> handleCancelPO(poId));

                setGraphic(actions);
            }
        });

        table.getColumns().addAll(idCol, supplierCol, itemCountCol, dateCol, totalCol, statusCol, createdByCol, actionsCol);
        table.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showOrderDetails(row.getItem());
                }
            });
            return row;
        });
        return table;
    }

    private void showOrderDetails(Map<String, Object> order) {
        int poId = number(order, "po_id").intValue();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Purchase Order Details");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(18));
        content.setPrefWidth(780);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("PO #" + poId);
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        Label status = new Label(text(order, "status"));
        status.setStyle(statusStyle(text(order, "status")));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, status, spacer);

        GridPane info = new GridPane();
        info.setHgap(18);
        info.setVgap(8);
        info.add(fieldLabel("Supplier"), 0, 0);
        info.add(valueLabel(text(order, "supplier_name")), 1, 0);
        info.add(fieldLabel("Created By"), 2, 0);
        info.add(valueLabel(text(order, "created_by_name")), 3, 0);
        info.add(fieldLabel("Order Date"), 0, 1);
        info.add(valueLabel(formatDate(order.get("order_date"))), 1, 1);
        info.add(fieldLabel("Total"), 2, 1);
        info.add(valueLabel("Rs " + money(number(order, "total_cost").doubleValue())), 3, 1);

        TableView<PurchaseOrderDAO.POItem> itemsTable = buildPOItemsTable(true);
        try {
            itemsTable.setItems(FXCollections.observableArrayList(purchaseOrderDAO.getPOItems(poId)));
        } catch (SQLException ex) {
            AlertUtil.showError("Error", "Could not load purchase order items: " + ex.getMessage());
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (isActiveOrder(text(order, "status"))) {
            Button receiveBtn = primaryButton("Receive Stock");
            receiveBtn.setOnAction(e -> {
                dialog.close();
                handleReceivePO(poId);
            });
            Button cancelBtn = dangerButton("Cancel Order");
            cancelBtn.setOnAction(e -> {
                dialog.close();
                handleCancelPO(poId);
            });
            actions.getChildren().addAll(cancelBtn, receiveBtn);
        }

        content.getChildren().addAll(header, info, sectionTitle("Ordered Items"), itemsTable, actions);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private TableView<PurchaseOrderDAO.POItem> buildPOItemsTable(boolean readOnly) {
        TableView<PurchaseOrderDAO.POItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(readOnly ? 300 : 230);
        table.setPlaceholder(new Label("No items added"));

        TableColumn<PurchaseOrderDAO.POItem, String> medCol = new TableColumn<>("Medicine");
        medCol.setCellValueFactory(data -> {
            PurchaseOrderDAO.POItem item = data.getValue();
            String medicineName = item.medicineName == null || item.medicineName.isBlank()
                    ? "Med - " + item.medId
                    : item.medicineName;
            return new SimpleStringProperty(medicineName);
        });

        TableColumn<PurchaseOrderDAO.POItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().qtyOrdered));
        qtyCol.setMaxWidth(90);

        TableColumn<PurchaseOrderDAO.POItem, String> unitCol = new TableColumn<>("Unit Cost");
        unitCol.setCellValueFactory(data -> new SimpleStringProperty("Rs " + money(data.getValue().unitCost)));

        TableColumn<PurchaseOrderDAO.POItem, String> packCol = new TableColumn<>("Pack Cost");
        packCol.setCellValueFactory(data -> new SimpleStringProperty("Rs " + money(data.getValue().packCost)));

        TableColumn<PurchaseOrderDAO.POItem, String> lineTotalCol = new TableColumn<>("Line Value");
        lineTotalCol.setCellValueFactory(data -> {
            PurchaseOrderDAO.POItem item = data.getValue();
            double lineTotal = item.qtyOrdered * item.packCost;
            return new SimpleStringProperty("Rs " + money(lineTotal));
        });

        table.getColumns().addAll(medCol, qtyCol, unitCol, packCol, lineTotalCol);
        return table;
    }

    private void handleReceivePO(int poId) {
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Receive Purchase Order");
        dialog.setHeaderText("Confirm stock receipt for PO #" + poId);

        ButtonType receiveType = new ButtonType("Receive & Update Stock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(receiveType, ButtonType.CANCEL);

        DatePicker expiryPicker = new DatePicker(LocalDate.now().plusYears(2));
        expiryPicker.setMaxWidth(Double.MAX_VALUE);
        VBox content = new VBox(8, fieldLabel("Expiry date for received batch records"), expiryPicker);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> btn == receiveType ? expiryPicker.getValue() : null);
        Optional<LocalDate> result = dialog.showAndWait();
        result.ifPresent(expiry -> {
            if (expiry == null) {
                AlertUtil.showError("Validation", "Select an expiry date.");
                return;
            }
            try {
                purchaseOrderDAO.receivePO(poId, expiry);
                AlertUtil.showInfo("Success", "Purchase order #" + poId + " has been received and stock has been updated.");
                loadData();
            } catch (SQLException ex) {
                AlertUtil.showError("Database Error", "Failed to receive purchase order: " + ex.getMessage());
            }
        });
    }

    private void handleCancelPO(int poId) {
        if (!AlertUtil.showConfirm("Cancel Purchase Order", "Cancel PO #" + poId + "? This is allowed only before stock is received.")) {
            return;
        }
        try {
            purchaseOrderDAO.cancelPO(poId);
            AlertUtil.showInfo("Cancelled", "Purchase order #" + poId + " has been cancelled.");
            loadData();
        } catch (SQLException ex) {
            AlertUtil.showError("Database Error", "Failed to cancel purchase order: " + ex.getMessage());
        }
    }

    private void showCreatePODialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Create Purchase Order");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(18));
        content.setPrefWidth(900);

        Label title = new Label("New Purchase Order");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        ComboBox<Supplier> supplierBox = new ComboBox<>();
        supplierBox.setPromptText("Select supplier");
        supplierBox.setMaxWidth(Double.MAX_VALUE);
        try {
            supplierBox.setItems(FXCollections.observableArrayList(supplierDAO.getAll()));
        } catch (SQLException ex) {
            AlertUtil.showError("Error", "Could not load suppliers: " + ex.getMessage());
        }

        ObservableList<PurchaseOrderDAO.POItem> itemsList = FXCollections.observableArrayList();
        TableView<PurchaseOrderDAO.POItem> itemTable = buildPOItemsTable(false);
        itemTable.setItems(itemsList);

        ComboBox<Medicine> medicineBox = new ComboBox<>();
        medicineBox.setPromptText("Select supplier first");
        medicineBox.setDisable(true);
        medicineBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(medicineBox, Priority.ALWAYS);

        TextField qtyField = numberField("Qty", 80);
        TextField unitCostField = numberField("Unit cost", 100);
        TextField packCostField = numberField("Pack cost", 100);

        Label currentStockLabel = valueLabel("-");
        Label reorderLevelLabel = valueLabel("-");
        Label medicineSupplierLabel = valueLabel("-");
        Label orderTotalLabel = valueLabel("Rs 0.00");

        GridPane medicineDetails = new GridPane();
        medicineDetails.setHgap(14);
        medicineDetails.setVgap(8);
        medicineDetails.add(fieldLabel("Current Stock"), 0, 0);
        medicineDetails.add(currentStockLabel, 1, 0);
        medicineDetails.add(fieldLabel("Reorder Level"), 2, 0);
        medicineDetails.add(reorderLevelLabel, 3, 0);
        medicineDetails.add(fieldLabel("Supplier"), 0, 1);
        medicineDetails.add(medicineSupplierLabel, 1, 1);
        medicineDetails.add(fieldLabel("Order Total"), 2, 1);
        medicineDetails.add(orderTotalLabel, 3, 1);

        ObservableList<Medicine> supplierMedicines = FXCollections.observableArrayList();
        medicineBox.setItems(supplierMedicines);

        supplierBox.valueProperty().addListener((obs, oldSupplier, newSupplier) -> {
            supplierMedicines.clear();
            itemsList.clear();
            medicineBox.getSelectionModel().clearSelection();
            clearMedicineDetails(currentStockLabel, reorderLevelLabel, medicineSupplierLabel, unitCostField, packCostField);
            updateCreateTotal(itemsList, orderTotalLabel);

            if (newSupplier == null) {
                medicineBox.setDisable(true);
                medicineBox.setPromptText("Select supplier first");
                return;
            }

            try {
                supplierMedicines.setAll(purchaseOrderDAO.getMedicinesBySupplier(newSupplier.getSupplierId()));
                medicineBox.setDisable(supplierMedicines.isEmpty());
                medicineBox.setPromptText(supplierMedicines.isEmpty()
                        ? "No medicines for selected supplier"
                        : "Select medicine supplied by " + newSupplier.getName());
            } catch (SQLException ex) {
                medicineBox.setDisable(true);
                medicineBox.setPromptText("Could not load medicines");
                AlertUtil.showError("Error", "Could not load medicines for selected supplier: " + ex.getMessage());
            }
        });

        medicineBox.valueProperty().addListener((obs, oldMedicine, newMedicine) -> {
            if (newMedicine == null) {
                clearMedicineDetails(currentStockLabel, reorderLevelLabel, medicineSupplierLabel, unitCostField, packCostField);
                return;
            }
            populateMedicineDetails(newMedicine, currentStockLabel, reorderLevelLabel, medicineSupplierLabel, unitCostField, packCostField);
            int suggestedQty = Math.max(newMedicine.getReorderLevel() - newMedicine.getStockQty(), 1);
            qtyField.setText(String.valueOf(suggestedQty));
        });

        itemsList.addListener((javafx.collections.ListChangeListener<PurchaseOrderDAO.POItem>) change -> updateCreateTotal(itemsList, orderTotalLabel));

        Button addBtn = primaryButton("Add Item");
        addBtn.setOnAction(e -> addSelectedMedicine(itemsList, medicineBox, qtyField, unitCostField, packCostField));

        Button addLowStockBtn = secondaryButton("Add Low Stock");
        addLowStockBtn.setOnAction(e -> addLowStockItems(itemsList, supplierMedicines));

        Button clearBtn = secondaryButton("Clear Items");
        clearBtn.setOnAction(e -> itemsList.clear());

        HBox addRow = new HBox(8, medicineBox, qtyField, unitCostField, packCostField, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        HBox itemActions = new HBox(8, addLowStockBtn, clearBtn);
        itemActions.setAlignment(Pos.CENTER_LEFT);

        TableColumn<PurchaseOrderDAO.POItem, Void> removeCol = new TableColumn<>("");
        removeCol.setMaxWidth(90);
        removeCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = dangerButton("Remove");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                removeBtn.setOnAction(e -> itemsList.remove(getIndex()));
                setGraphic(removeBtn);
            }
        });
        itemTable.getColumns().add(removeCol);

        Button submitBtn = primaryButton("Submit Purchase Order");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setPrefHeight(40);
        submitBtn.setOnAction(e -> {
            Supplier supplier = supplierBox.getValue();
            if (supplier == null) {
                AlertUtil.showError("Validation", "Please select a supplier.");
                return;
            }
            if (itemsList.isEmpty()) {
                AlertUtil.showError("Validation", "Add at least one item to the purchase order.");
                return;
            }
            try {
                int userId = SessionManager.getInstance().getCurrentUser().getUserId();
                int poId = purchaseOrderDAO.createPO(supplier.getSupplierId(), userId, new ArrayList<>(itemsList));
                AlertUtil.showInfo("Success", "Purchase order #" + poId + " was created for " + supplier.getName() + ".");
                dialog.close();
                loadData();
            } catch (SQLException ex) {
                AlertUtil.showError("Database Error", "Failed to save purchase order: " + ex.getMessage());
            }
        });

        VBox supplierCard = card();
        supplierCard.getChildren().addAll(sectionTitle("Supplier"), supplierBox);

        VBox itemsCard = card();
        itemsCard.getChildren().addAll(sectionTitle("Items"), itemTable, addRow, medicineDetails, itemActions);

        content.getChildren().addAll(title, supplierCard, itemsCard, submitBtn);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void addSelectedMedicine(ObservableList<PurchaseOrderDAO.POItem> itemsList, ComboBox<Medicine> medicineBox,
                                     TextField qtyField, TextField unitCostField, TextField packCostField) {
        Medicine medicine = medicineBox.getValue();
        if (medicine == null) {
            AlertUtil.showError("Selection Error", "Please select a medicine.");
            return;
        }

        try {
            int qty = Integer.parseInt(qtyField.getText().trim());
            double unitCost = Double.parseDouble(unitCostField.getText().trim());
            double packCost = Double.parseDouble(packCostField.getText().trim());
            if (qty <= 0 || unitCost < 0 || packCost < 0) {
                throw new NumberFormatException();
            }
            itemsList.add(new PurchaseOrderDAO.POItem(medicine.getMedId(), medicine.getName(), qty, unitCost, packCost));
            medicineBox.getSelectionModel().clearSelection();
            qtyField.clear();
        } catch (NumberFormatException ex) {
            AlertUtil.showError("Input Error", "Quantity must be positive, and costs must be valid non-negative numbers.");
        }
    }

    private void addLowStockItems(ObservableList<PurchaseOrderDAO.POItem> itemsList, List<Medicine> medicines) {
        int added = 0;
        for (Medicine medicine : medicines) {
            int qty = medicine.getReorderLevel() - medicine.getStockQty();
            if (qty > 0 && itemsList.stream().noneMatch(item -> item.medId == medicine.getMedId())) {
                itemsList.add(new PurchaseOrderDAO.POItem(
                        medicine.getMedId(),
                        medicine.getName(),
                        qty,
                        medicine.getUnitCost(),
                        medicine.getPackCost()
                ));
                added++;
            }
        }
        if (added == 0) {
            AlertUtil.showInfo("No Items Added", "There are no low-stock medicines for this supplier that are not already in the order.");
        }
    }

    private void updateCreateTotal(List<PurchaseOrderDAO.POItem> items, Label totalLabel) {
        double total = items.stream().mapToDouble(item -> item.qtyOrdered * item.packCost).sum();
        totalLabel.setText("Rs " + money(total));
    }

    private void populateMedicineDetails(Medicine medicine, Label currentStockLabel, Label reorderLevelLabel,
                                         Label medicineSupplierLabel, TextField unitCostField, TextField packCostField) {
        currentStockLabel.setText(String.valueOf(medicine.getStockQty()));
        reorderLevelLabel.setText(String.valueOf(medicine.getReorderLevel()));
        medicineSupplierLabel.setText(medicine.getSupplierName() == null || medicine.getSupplierName().isBlank()
                ? "-"
                : medicine.getSupplierName());
        unitCostField.setText(medicine.getUnitCost() > 0 ? String.valueOf(medicine.getUnitCost()) : "");
        packCostField.setText(medicine.getPackCost() > 0 ? String.valueOf(medicine.getPackCost()) : "");
    }

    private void clearMedicineDetails(Label currentStockLabel, Label reorderLevelLabel, Label medicineSupplierLabel,
                                      TextField unitCostField, TextField packCostField) {
        currentStockLabel.setText("-");
        reorderLevelLabel.setText("-");
        medicineSupplierLabel.setText("-");
        unitCostField.clear();
        packCostField.clear();
    }

    private VBox card() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E5E7EB; -fx-border-radius: 8;");
        return card;
    }

    private Node metricCard(String title, Label value, String color) {
        VBox card = new VBox(5, fieldLabel(title), value);
        card.setPadding(new Insets(12));
        card.setMinWidth(135);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E5E7EB; -fx-border-radius: 8;");
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return card;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        return label;
    }

    private static Label valueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }

    private TextField numberField(String prompt, double width) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(width);
        return field;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #F9FAFB; -fx-text-fill: #111827; -fx-border-color: #D1D5DB; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
        return button;
    }

    private Button smallButton(String text) {
        Button button = secondaryButton(text);
        button.setStyle(button.getStyle() + "-fx-font-size: 11px;");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-border-color: #FCA5A5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px;");
        return button;
    }

    private String statusStyle(String status) {
        String color = "#6B7280";
        String background = "#F3F4F6";
        if ("PENDING".equalsIgnoreCase(status) || "ORDERED".equalsIgnoreCase(status)) {
            color = "#92400E";
            background = "#FEF3C7";
        } else if ("RECEIVED".equalsIgnoreCase(status)) {
            color = "#065F46";
            background = "#D1FAE5";
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            color = "#374151";
            background = "#E5E7EB";
        }
        return "-fx-background-color: " + background + "; -fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-padding: 4 8; -fx-background-radius: 12;";
    }

    private boolean isActiveOrder(String status) {
        return !"RECEIVED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status);
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? (Number) value : 0;
    }

    private String money(double value) {
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
        return MONEY.format(value);
    }

    private String formatDate(Object value) {
        if (value instanceof Timestamp timestamp) {
            return DATE_FORMAT.format(timestamp.toLocalDateTime());
        }
        return value == null ? "-" : value.toString();
    }
}

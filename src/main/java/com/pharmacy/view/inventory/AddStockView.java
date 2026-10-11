package com.pharmacy.view.inventory;

import com.pharmacy.controller.InventoryController;
import com.pharmacy.dao.BatchDAO;
import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.model.Batch;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AddStockView {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BatchDAO batchDAO = new BatchDAO();
    private final PurchaseOrderDAO purchaseOrderDAO = new PurchaseOrderDAO();

    private final ComboBox<Medicine> medicineBox = new ComboBox<>();
    private final ComboBox<Medicine> supplierBox = new ComboBox<>();
    private final Label currentStockLabel = valueLabel("-");
    private final Label reorderLevelLabel = valueLabel("-");
    private final Label supplierLabel = valueLabel("-");
    private final TextField batchNumberField = new TextField();
    private final TextField quantityField = new TextField();
    private final TextField unitCostField = new TextField();
    private final TextField packCostField = new TextField();
    private final DatePicker expiryPicker = new DatePicker();
    private final TextArea notesArea = new TextArea();
    private final Label statusLabel = new Label("Ready");
    private final TableView<Batch> batchTable = new TableView<>();

    public Node getView() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        Label title = new Label("Add Stock");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Receive new inventory batches and update medicine stock quantities.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        content.getChildren().addAll(title, subtitle, buildFormCard(), buildTableCard(), statusLabel);
        pane.setCenter(AppUIManager.scrollWrap(content));

        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        loadMedicines();
        configureSelectionHandling();
        return pane;
    }

    private VBox buildFormCard() {
        VBox card = createCard();

        TextField searchField = new TextField();
        searchField.setPromptText("Search by name, category, or scan barcode...");
        searchField.setPrefHeight(36);
        searchField.setStyle("-fx-font-size: 13px;");

        Button addBtn = new Button("+ Add to Cart");
        addBtn.setPrefHeight(36);
        addBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");


        medicineBox.setPromptText("Search medicine");
        medicineBox.setMaxWidth(Double.MAX_VALUE);

        HBox searchRow = new HBox(8, searchField, addBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        supplierBox.setPromptText("Select supplier");
        supplierBox.setMaxWidth(Double.MAX_VALUE);

        batchNumberField.setPromptText("Supplier batch / lot number");
        quantityField.setPromptText("Quantity received");
        unitCostField.setPromptText("Unit cost");
        packCostField.setPromptText("Pack cost");
        expiryPicker.setPromptText("Expiry date");
        expiryPicker.setValue(LocalDate.now().plusMonths(12));

        notesArea.setPromptText("Optional receiving notes");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        grid.add(fieldLabel("Medicine"), 0, 0);
        grid.add(medicineBox, 1, 0);
        grid.add(fieldLabel("Current Stock"), 2, 0);
        grid.add(currentStockLabel, 3, 0);

        grid.add(fieldLabel("Batch Number"), 0, 1);
        grid.add(batchNumberField, 1, 1);
        grid.add(fieldLabel("Reorder Level"), 2, 1);
        grid.add(reorderLevelLabel, 3, 1);

        grid.add(fieldLabel("Quantity Received"), 0, 2);
        grid.add(quantityField, 1, 2);
        grid.add(fieldLabel("Supplier"), 2, 2);
        grid.add(supplierLabel, 3, 2);

        grid.add(fieldLabel("Unit Cost"), 0, 3);
        grid.add(unitCostField, 1, 3);
        grid.add(fieldLabel("Pack Cost"), 2, 3);
        grid.add(packCostField, 3, 3);

        grid.add(fieldLabel("Expiry Date"), 0, 4);
        grid.add(expiryPicker, 1, 4);
        grid.add(fieldLabel("Notes"), 2, 4);
        grid.add(notesArea, 3, 4);

        Button saveBtn = new Button("Add Order");
        saveBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> handleSave());

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> resetForm());

        Button refreshBtn = new Button("Refresh Medicines");
        refreshBtn.setOnAction(e -> loadMedicines());

        Button newBtn = new Button("+ New Medicine");
        newBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");

        InventoryController controller = new InventoryController(new TableView<>(), new TextField(), new Label());
        newBtn.setOnAction(e -> controller.showAddDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10, saveBtn, clearBtn, refreshBtn, spacer, newBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(sectionTitle("Receive Batch"), grid, actions,searchField);
        return card;
    }

    private VBox buildTableCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Existing Batches"));

        batchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        batchTable.setPlaceholder(new Label("Select a medicine to view its batches."));

        TableColumn<Batch, String> batchCol = new TableColumn<>("Batch");
        batchCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBatchNumber()));
        batchCol.setPrefWidth(150);

        TableColumn<Batch, Number> receivedCol = new TableColumn<>("Received");
        receivedCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQtyReceived()));
        receivedCol.setPrefWidth(90);

        TableColumn<Batch, Number> remainingCol = new TableColumn<>("Remaining");
        remainingCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQtyRemaining()));
        remainingCol.setPrefWidth(90);

        TableColumn<Batch, LocalDate> expiryCol = new TableColumn<>("Expiry");
        expiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getExpiryDate()));
        expiryCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        expiryCol.setPrefWidth(110);

        TableColumn<Batch, String> unitCostCol = new TableColumn<>("Unit Cost");
        unitCostCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("Rs %.2f", c.getValue().getUnitCost())));
        unitCostCol.setPrefWidth(110);

        TableColumn<Batch, String> packCostCol = new TableColumn<>("Pack Cost");
        packCostCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("Rs %.2f", c.getValue().getPackCost())));
        packCostCol.setPrefWidth(110);

        TableColumn<Batch, LocalDateTime> receivedAtCol = new TableColumn<>("Received At");
        receivedAtCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getReceivedAt()));
        receivedAtCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATE_TIME_FORMAT.format(item));
            }
        });

        batchTable.getColumns().setAll(batchCol, receivedCol, remainingCol, expiryCol, unitCostCol, packCostCol, receivedAtCol);
        VBox.setVgrow(batchTable, Priority.ALWAYS);
        card.getChildren().add(batchTable);

        batchTable.setMinHeight(200);

        return card;
    }

    private void configureSelectionHandling() {
        medicineBox.setOnAction(e -> {
            Medicine medicine = medicineBox.getValue();
            if (medicine == null) {
                clearMedicineSummary();
                batchTable.getItems().clear();
                return;
            }
            populateMedicineSummary(medicine);
            loadBatches(medicine);
        });
    }

    private void loadMedicines() {
        try {
            List<Medicine> medicines = medicineDAO.getAll();
            Medicine selected = medicineBox.getValue();
            medicineBox.setItems(FXCollections.observableArrayList(medicines));

            if (selected != null) {
                medicines.stream()
                        .filter(medicine -> medicine.getMedId() == selected.getMedId())
                        .findFirst()
                        .ifPresentOrElse(medicine -> {
                            medicineBox.setValue(medicine);
                            populateMedicineSummary(medicine);
                            loadBatches(medicine);
                        }, () -> {
                            clearMedicineSummary();
                            batchTable.getItems().clear();
                        });
            } else {
                clearMedicineSummary();
            }

            statusLabel.setText(medicines.size() + " medicine(s) available.");
        } catch (Exception e) {
            AlertUtil.showError("Load Error", e.getMessage());
            statusLabel.setText("Failed to load medicines");
        }
    }

    private void loadBatches(Medicine medicine) {
        try {
            batchTable.setItems(FXCollections.observableArrayList(batchDAO.getByMedicine(medicine.getMedId())));
        } catch (Exception e) {
            batchTable.getItems().clear();
            AlertUtil.showError("Batch Load Error", e.getMessage());
        }
    }

    private void handleSave() {
        Medicine medicine = medicineBox.getValue();
        if (medicine == null) {
            AlertUtil.showError("Validation", "Select a medicine first.");
            return;
        }

        String batchNumber = batchNumberField.getText().trim();
        if (batchNumber.isEmpty()) {
            AlertUtil.showError("Validation", "Batch number is required.");
            return;
        }

        int qty;
        double unitCost;
        double packCost;
        try {
            qty = Integer.parseInt(quantityField.getText().trim());
            if (qty <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            AlertUtil.showError("Validation", "Quantity must be a positive whole number.");
            return;
        }

        try {
            unitCost = Double.parseDouble(unitCostField.getText().trim());
            if (unitCost < 0) throw new NumberFormatException();
        } catch (Exception e) {
            AlertUtil.showError("Validation", "Unit cost must be a valid non-negative number.");
            return;
        }

        try {
            packCost = Double.parseDouble(packCostField.getText().trim());
            if (packCost < 0) throw new NumberFormatException();
        } catch (Exception e) {
            AlertUtil.showError("Validation", "Pack cost must be a valid non-negative number.");
            return;
        }

        LocalDate expiryDate = expiryPicker.getValue();
        if (expiryDate == null || !expiryDate.isAfter(LocalDate.now().minusDays(1))) {
            AlertUtil.showError("Validation", "Select a valid expiry date.");
            return;
        }

        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                int batchId = insertBatch(con, medicine.getMedId(), batchNumber, qty, expiryDate, unitCost, packCost);
//                updateMedicineStock(con, medicine.getMedId(), qty, purchaseCost);
                con.commit();
                String notes = notesArea.getText().trim();

                AuditLogger.log(
                        "ADD_STOCK",
                        "batches",
                        batchId,
                        "Added stock for " + medicine.getName() + ": batch " + batchNumber + ", qty " + qty
                                + (notes.isEmpty() ? "" : ", notes: " + notes) + "\nPlease visit Purchase Orders"
                );

                statusLabel.setText("Stock added successfully.");
                notesArea.clear();
                resetEntryFields();
                reloadSelectedMedicine(medicine.getMedId());
                AlertUtil.showInfo("Stock Added", "Batch saved and inventory updated successfully.");
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AlertUtil.showError("Save Failed", e.getMessage());
            statusLabel.setText("Stock add failed");
        }

        Medicine med = medicineBox.getValue();
        if(med == null) {
            AlertUtil.showError("Selection Error", "Please select a medicine from the list");
            return;
        }
        ObservableList<PurchaseOrderDAO.POItem> itemsList = FXCollections.observableArrayList();
        try{
            itemsList.add(new PurchaseOrderDAO.POItem(med.getMedId(), qty, unitCost, packCost));
            quantityField.clear();
            unitCostField.clear();
            packCostField.clear();
            medicineBox.getSelectionModel().clearSelection();
        }
        catch(NumberFormatException ex){
            AlertUtil.showError("Input Error", "Quantity, unit cost, and pack cost must be valid numbers");
        }

//        Supplier s = supplierBox.getValue();
//        if( s == null){
//            AlertUtil.showError("Validation", "Add at least one item to PO");
//            return;
//        }
        try{
            int userId = SessionManager.getInstance().getCurrentUser().getUserId();
            purchaseOrderDAO.createPO(medicine.getSupplierId(), userId, new ArrayList<>(itemsList));
            AlertUtil.showInfo("Success", "Purchase Order successful and sent to " + medicine.getSupplierName());
//            loadData();
        }
        catch(SQLException ex){
            AlertUtil.showError("Database Error", "Failed to save PO " + ex.getMessage());
        }
    }

    private int insertBatch(Connection con, int medId, String batchNumber, int qty, LocalDate expiryDate, double unitCost, double packCost)
            throws SQLException {
        String sql = "INSERT INTO batches (med_id, batch_number, qty_received, qty_remaining, expiry_date, unit_cost, pack_cost) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, medId);
            ps.setString(2, batchNumber);
            ps.setInt(3, qty);
            ps.setInt(4, qty);
            ps.setDate(5, java.sql.Date.valueOf(expiryDate));
            ps.setDouble(6, unitCost);
            ps.setDouble(7, packCost);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Batch could not be created.");
    }

//    private void updateMedicineStock(Connection con, int medId, int qty, double purchaseCost) throws SQLException {
//        String sql = "UPDATE medicines SET stock_qty = stock_qty + ?, purchase_cost = ? WHERE med_id = ?";
//        try (PreparedStatement ps = con.prepareStatement(sql)) {
//            ps.setInt(1, qty);
//            ps.setDouble(2, purchaseCost);
//            ps.setInt(3, medId);
//            ps.executeUpdate();
//        }
//    }

    private void reloadSelectedMedicine(int medId) {
        loadMedicines();
        medicineBox.getItems().stream()
                .filter(medicine -> medicine.getMedId() == medId)
                .findFirst()
                .ifPresent(medicine -> {
                    medicineBox.setValue(medicine);
                    populateMedicineSummary(medicine);
                    loadBatches(medicine);
                });
    }

    private void resetForm() {
        medicineBox.setValue(null);
        clearMedicineSummary();
        batchTable.getItems().clear();
        resetEntryFields();
        notesArea.clear();
        statusLabel.setText("Form cleared");
    }

    private void resetEntryFields() {
        batchNumberField.clear();
        quantityField.clear();
        unitCostField.clear();
        packCostField.clear();
        expiryPicker.setValue(LocalDate.now().plusMonths(12));
    }

    private void populateMedicineSummary(Medicine medicine) {
        currentStockLabel.setText(String.valueOf(medicine.getStockQty()));
        reorderLevelLabel.setText(String.valueOf(medicine.getReorderLevel()));
        supplierLabel.setText(medicine.getSupplierName() == null || medicine.getSupplierName().isBlank()
                ? "-"
                : medicine.getSupplierName());
        if (unitCostField.getText().isBlank() && medicine.getUnitCost() > 0) {
            unitCostField.setText(String.valueOf(medicine.getUnitCost()));
        }
        if (packCostField.getText().isBlank() && medicine.getPackCost() > 0) {
            packCostField.setText(String.valueOf(medicine.getPackCost()));
        }
    }

    private void clearMedicineSummary() {
        currentStockLabel.setText("-");
        reorderLevelLabel.setText("-");
        supplierLabel.setText("-");
    }

    private VBox createCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 10;");
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
}

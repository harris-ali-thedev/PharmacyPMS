package com.pharmacy.controller;

import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AuditLogger;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.util.List;
import java.util.Optional;

public class InventoryController {

    private final TableView<Medicine> table;
    private final TextField searchField;
    private final Label statusLabel;
    private final MedicineDAO dao   = new MedicineDAO();
    private final SupplierDAO supDao = new SupplierDAO();

    public InventoryController(TableView<Medicine> table, TextField searchField, Label statusLabel) {
        this.table = table;
        this.searchField = searchField;
        this.statusLabel = statusLabel;
        table.setUserData(this);
    }

    public void loadAll() {
        try {
            List<Medicine> list = dao.getAll();
            table.setItems(FXCollections.observableArrayList(list));
            statusLabel.setText(list.size() + " medicines loaded.");
        } catch (Exception e) {
            AlertUtil.showError("Load Error", e.getMessage());
        }
    }

    public void search() {
        String kw = searchField.getText().trim();
        if (kw.isEmpty()) { loadAll(); return; }
        try {
            List<Medicine> list = dao.search(kw);
            table.setItems(FXCollections.observableArrayList(list));
            statusLabel.setText(list.size() + " result(s) for \"" + kw + "\".");
        } catch (Exception e) {
            AlertUtil.showError("Search Error", e.getMessage());
        }
    }

    public void showLowStock() {
        try {
            List<Medicine> list = dao.getLowStock();
            table.setItems(FXCollections.observableArrayList(list));
            statusLabel.setText(list.size() + " low-stock items.");
        } catch (Exception e) {
            AlertUtil.showError("Error", e.getMessage());
        }
    }

    public void showAddDialog() {
        showDialog(null);
    }

    public void showEditDialog(Medicine m) {
        showDialog(m);
    }

    public void deleteMedicine(Medicine m) {
        if (!AlertUtil.showConfirm("Delete Medicine",
                "Delete '" + m.getName() + "'? This cannot be undone.")) return;
        try {
            dao.delete(m.getMedId());
            AuditLogger.log("DELETE_MEDICINE", "medicines", m.getMedId(), "Deleted: " + m.getName());
            loadAll();
        } catch (Exception e) {
            AlertUtil.showError("Delete Failed", e.getMessage());
        }
    }

    // ─── Dialog ───────────────────────────────────────────────
    private void showDialog(Medicine existing) {
        Dialog<Medicine> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Medicine" : "Edit Medicine");
        dialog.initModality(Modality.APPLICATION_MODAL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameF      = field(existing != null ? existing.getName() : "", "Medicine name");
        TextField genericF   = field(existing != null ? existing.getGenericName() : "", "Generic name");
        TextField categoryF  = field(existing != null ? existing.getCategory() : "", "e.g. Antibiotic");
        TextField barcodeF   = field(existing != null ? existing.getBarcode() : "", "Barcode");
        TextField priceF     = field(existing != null ? String.valueOf(existing.getUnitPrice()) : "0", "Unit price");
        TextField unitCostF  = field(existing != null ? String.valueOf(existing.getUnitCost()) : "0", "Unit cost");
        TextField packCostF  = field(existing != null ? String.valueOf(existing.getPackCost()) : "0", "Pack cost");
        TextField stockF     = field(existing != null ? String.valueOf(existing.getStockQty()) : "0", "Current stock");
        TextField reorderF   = field(existing != null ? String.valueOf(existing.getReorderLevel()) : "20", "Reorder level");
        CheckBox scheduledCb = new CheckBox("Narcotic / Restricted Drug");
        if (existing != null) scheduledCb.setSelected(existing.isNarcotics());

        List<Supplier> suppliers;
        try { suppliers = supDao.getAll(); } catch (Exception e) { suppliers = List.of(); }
        ComboBox<Supplier> supBox = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supBox.setPromptText("Select supplier");
        if (existing != null) {
            for (Supplier s : suppliers) {
                if (s.getSupplierId() == existing.getSupplierId()) { supBox.setValue(s); break; }
            }
        }

        int r = 0;
        grid.add(new Label("Name *"),        0, r); grid.add(nameF, 1, r++);
        grid.add(new Label("Generic Name"),  0, r); grid.add(genericF, 1, r++);
        grid.add(new Label("Category"),      0, r); grid.add(categoryF, 1, r++);
        grid.add(new Label("Barcode"),       0, r); grid.add(barcodeF, 1, r++);
        grid.add(new Label("Unit Price *"),  0, r); grid.add(priceF, 1, r++);
        grid.add(new Label("Unit Cost"),     0, r); grid.add(unitCostF, 1, r++);
        grid.add(new Label("Pack Cost"),     0, r); grid.add(packCostF, 1, r++);
        grid.add(new Label("Stock Qty"),     0, r); grid.add(stockF, 1, r++);
        grid.add(new Label("Reorder Level"), 0, r); grid.add(reorderF, 1, r++);
        grid.add(new Label("Supplier"),      0, r); grid.add(supBox, 1, r++);
        grid.add(scheduledCb,                1, r);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (nameF.getText().trim().isEmpty()) {
                AlertUtil.showError("Validation", "Medicine name is required.");
                return null;
            }
            Medicine m = existing != null ? existing : new Medicine();
            m.setName(nameF.getText().trim());
            m.setGenericName(genericF.getText().trim());
            m.setCategory(categoryF.getText().trim());
            m.setBarcode(barcodeF.getText().trim());
            try { m.setUnitPrice(Double.parseDouble(priceF.getText())); } catch (Exception ignored) {}
            try { m.setUnitCost(Double.parseDouble(unitCostF.getText())); } catch (Exception ignored) {}
            try { m.setPackCost(Double.parseDouble(packCostF.getText())); } catch (Exception ignored) {}
            try { m.setStockQty(Integer.parseInt(stockF.getText())); } catch (Exception ignored) {}
            try { m.setReorderLevel(Integer.parseInt(reorderF.getText())); } catch (Exception ignored) {}
            m.setNarcotics(scheduledCb.isSelected());
            if (supBox.getValue() != null) m.setSupplierId(supBox.getValue().getSupplierId());
            return m;
        });

        Optional<Medicine> result = dialog.showAndWait();
        result.ifPresent(m -> {
            try {
                if (existing == null) {
                    int id = dao.insert(m);
                    AuditLogger.log("ADD_MEDICINE", "medicines", id, "Added: " + m.getName());
                } else {
                    dao.update(m);
                    AuditLogger.log("EDIT_MEDICINE", "medicines", m.getMedId(), "Edited: " + m.getName());
                }
                loadAll();
            } catch (Exception e) {
                AlertUtil.showError("Save Failed", e.getMessage());
            }
        });
    }

    private TextField field(String value, String prompt) {
        TextField tf = new TextField(value);
        tf.setPromptText(prompt);
        tf.setPrefWidth(220);
        return tf;
    }
}

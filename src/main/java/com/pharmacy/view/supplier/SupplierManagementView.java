package com.pharmacy.view.supplier;

import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AuditLogger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.util.List;
import java.util.Optional;

public class SupplierManagementView {

    private final SupplierDAO dao = new SupplierDAO();
    private TableView<Supplier> table;

    public BorderPane getView() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        Label title = new Label("Supplier Management");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Button addBtn     = new Button("+ Add Supplier");
        addBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
        Button refreshBtn = new Button("↺ Refresh");

        HBox toolbar = new HBox(8, addBtn, refreshBtn);

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(title, toolbar, table);
        pane.setCenter(content);

        addBtn.setOnAction(e -> showDialog(null));
        refreshBtn.setOnAction(e -> loadAll());
        loadAll();

        return pane;
    }

    private void loadAll() {
        try {
            List<Supplier> list = dao.getAll();
            table.setItems(FXCollections.observableArrayList(list));
        } catch (Exception e) {
            AlertUtil.showError("Error", e.getMessage());
        }
    }

    private TableView<Supplier> buildTable() {
        TableView<Supplier> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label("No suppliers found."));

        TableColumn<Supplier, String> nameCol    = sc("Supplier Name", c -> new SimpleStringProperty(c.getValue().getName()), 180);
        TableColumn<Supplier, String> contactCol = sc("Person Name", c -> new SimpleStringProperty(c.getValue().getContactName()), 140);
        TableColumn<Supplier, String> phoneCol   = sc("Phone", c -> new SimpleStringProperty(c.getValue().getPhone()), 120);
        TableColumn<Supplier, String> emailCol   = sc("Email", c -> new SimpleStringProperty(c.getValue().getEmail()), 180);
        TableColumn<Supplier, Integer> ldCol     = new TableColumn<>("Lead Days");
        ldCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getLeadDays()).asObject());
        TableColumn<Supplier, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isActive()).asObject());
        activeCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Boolean v, boolean e) {
                super.updateItem(v, e);
                if (e || v == null) { setText(null); return; }
                setText(v ? "Yes" : "No");
                setStyle(v ? "-fx-text-fill: #059669;" : "-fx-text-fill: #DC2626;");
            }
        });
//        561b60

        TableColumn<Supplier, Void> actionCol = new TableColumn<>("Actions");
//        final Button deleteBtn;// = new Button("Delete");
        actionCol.setCellFactory(col -> new TableCell<>() {
            final Button editBtn = new Button("Edit");
            //            final Button deactBtn  = new Button("Deactivate");
            final Button deleteBtn = new Button("Delete");
            {
                editBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
//                deactBtn.setStyle("-fx-background-color: #561B60; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
                deleteBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;"); // Red style
            };
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Supplier s = getTableView().getItems().get(getIndex());
                editBtn.setOnAction(e -> showDialog(s));
//                deactBtn.setOnAction(e -> {
//                    if (AlertUtil.showConfirm("Deactivate", "Deactivate supplier '" + s.getName() + "'?")) {
//                        try { dao.delete(s.getSupplierId()); loadAll(); }
//                        catch (Exception ex) { AlertUtil.showError("Error", ex.getMessage()); }
//                    }
//                });
                deleteBtn.setOnAction(e -> {
                    handleSupplierDelete(s);
                });
                setGraphic(new HBox(6, editBtn, deleteBtn));
            }
        });

        t.getColumns().addAll(nameCol, contactCol, phoneCol, emailCol, ldCol, activeCol, actionCol);
        return t;
    }

    private void showDialog(Supplier existing) {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Supplier" : "Edit Supplier");
        dialog.initModality(Modality.APPLICATION_MODAL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField nameF    = f(existing != null ? existing.getName() : "");
        TextField contactF = f(existing != null ? existing.getContactName() : "");
        TextField phoneF   = f(existing != null ? existing.getPhone() : "");
        TextField emailF   = f(existing != null ? existing.getEmail() : "");
        TextArea  addrF    = new TextArea(existing != null ? existing.getAddress() : "");
        addrF.setPrefRowCount(2); addrF.setPrefWidth(220);
        TextField leadF    = f(existing != null ? String.valueOf(existing.getLeadDays()) : "3");

        int r = 0;
        grid.add(new Label("Comp Name *"),    0, r); grid.add(nameF, 1, r++);
        grid.add(new Label("Person"),   0, r); grid.add(contactF, 1, r++);
        grid.add(new Label("Phone"),     0, r); grid.add(phoneF, 1, r++);
        grid.add(new Label("Email"),     0, r); grid.add(emailF, 1, r++);
        grid.add(new Label("Address"),   0, r); grid.add(addrF, 1, r++);
        grid.add(new Label("Lead Days"), 0, r); grid.add(leadF, 1, r);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (nameF.getText().trim().isEmpty()) { AlertUtil.showError("Validation", "Name is required."); return null; }

            Supplier s = existing != null ? existing : new Supplier();
            s.setName(nameF.getText().trim());
            s.setContactName(contactF.getText().trim());
            s.setPhone(phoneF.getText().trim());
            s.setEmail(emailF.getText().trim());
            s.setAddress(addrF.getText().trim());
            try { s.setLeadDays(Integer.parseInt(leadF.getText())); } catch (Exception ignored) {}
            s.setActive(true);
            return s;
        });

        Optional<Supplier> result = dialog.showAndWait();
        result.ifPresent(s -> {
            try {
                if (existing == null) { dao.insert(s); AuditLogger.log("ADD_SUPPLIER", "suppliers", null, s.getName()); }
                else                  { dao.update(s); AuditLogger.log("EDIT_SUPPLIER", "suppliers", s.getSupplierId(), s.getName()); }
                loadAll();
            } catch (Exception e) { AlertUtil.showError("Save Failed", e.getMessage()); }
        });
    }

    private TextField f(String val) { TextField tf = new TextField(val); tf.setPrefWidth(220); return tf; }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Supplier, T> sc(String title, javafx.util.Callback<TableColumn.CellDataFeatures<Supplier, T>, javafx.beans.value.ObservableValue<T>> f, double w) {
        TableColumn<Supplier, T> c = new TableColumn<>(title); c.setCellValueFactory(f); c.setPrefWidth(w); return c;
    }

    private void handleSupplierDelete(Supplier s) {
//        if (s.getSupplierId() == currentSupplierId) {
//            AlertUtil.showError("Action Denied", "You cannot delete your own account.");
//            return;
//        }

        // 3. Confirm and execute
        boolean confirm = AlertUtil.showConfirm("Delete Supplier",
                "Are you sure you want to PERMANENTLY delete " + s.getContactName() + "? This cannot be undone.");

        if (confirm) {
            try {
                boolean success = dao.softDelete(s.getSupplierId());
                if (success) {
                    AuditLogger.log("DELETE_USER", "users", s.getSupplierId(), "Permanently deleted user: " + s.getContactName());
                    loadAll(); // Refresh the table so they disappear
                } else {
                    AlertUtil.showError("Database Error", "Could not delete user. They might be tied to existing sales records.");
                }
            } catch (Exception e) {
                AlertUtil.showError("Error", e.getMessage());
            }
        }
    }
}

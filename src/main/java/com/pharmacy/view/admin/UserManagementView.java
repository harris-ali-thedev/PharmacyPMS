package com.pharmacy.view.admin;

import com.pharmacy.dao.UserDAO;
import com.pharmacy.model.User;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserManagementView {

    private final UserDAO dao = new UserDAO();
    private TableView<User> table;

    public BorderPane getView(){
        if (!SessionManager.getInstance().isAdmin()) {
            BorderPane p = new BorderPane();
            p.setCenter(new Label("Access denied. Admins only."));
            return p;
        }

        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        Label title = new Label("User Management");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Button addBtn     = new Button("+ Add User");
        addBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
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
            List<User> list = dao.getAllUsers();
            table.setItems(FXCollections.observableArrayList(list));
        } catch (Exception e) {
            AlertUtil.showError("Error", e.getMessage());
        }
    }

    private TableView<User> buildTable(){
        TableView<User> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        col(t, "Username", c -> new SimpleStringProperty(c.getValue().getUsername()), 140);
        col(t, "Full Name", c -> new SimpleStringProperty(c.getValue().getFullName()), 180);
        col(t, "Role", c -> new SimpleStringProperty(c.getValue().getRole()), 120);

        TableColumn<User, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isActive()).asObject());
        activeCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Boolean v, boolean e) {
                super.updateItem(v, e);
                if (e || v == null) { setText(null); return; }
                setText(v ? "Yes" : "No");
                setStyle(v ? "-fx-text-fill: #059669;" : "-fx-text-fill: #DC2626;");
            }
        });
        t.getColumns().add(activeCol);

        TableColumn<User, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(col -> new TableCell<>() {
            final Button editBtn  = new Button("Edit");
            final Button deleteBtn = new Button("Delete");
//            final Button pwdBtn   = new Button("Reset Password");
//            final Button usrBtn   = new Button("Change Username");
            {
                editBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
//                usrBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;"); // Green style
//                pwdBtn.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
                deleteBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;"); // Red style
            }
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                User u = getTableView().getItems().get(getIndex());
                editBtn.setOnAction(e -> showDialog(u));
//                usrBtn.setOnAction(e -> showUsernameReset(u)); // 2. LINK IT TO YOUR METHOD
//                pwdBtn.setOnAction(e -> showPasswordReset(u));
                deleteBtn.setOnAction(e -> handleDelete(u));
                deleteBtn.setDisable(!u.isActive() || u.getUserId() == 1);
//                setGraphic(new HBox(6, editBtn, usrBtn, pwdBtn, deleteBtn));
                setGraphic(new HBox(6, editBtn, deleteBtn));

            }
        });
        t.getColumns().add(actionCol);
        return t;
    }

    private void showDialog(User existing) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add User" : "Edit User");
        dialog.initModality(Modality.APPLICATION_MODAL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField usernameF  = f(existing != null ? existing.getUsername() : "");
        TextField fullNameF  = f(existing != null ? existing.getFullName() : "");
        PasswordField passF  = new PasswordField(); passF.setPromptText("Password (min 6 chars)");

        ComboBox<String> roleBox = new ComboBox<>();
//        if(existing != null) {
//            System.out.println("User details: ");
//            System.out.println("Role: " + existing.getRole());
//            System.out.println("ID: " + existing.getUserId());
//            System.out.println(existing.getUsername());
//            System.out.println(existing.getFullName());
//        }

//        UserDAO userDAO = new UserDAO();
        try {
            int adminCount = dao.getAdminCount();
//            boolean isExistingAdmin = (existing != null && "ADMIN".equals(existing.getRole()));
//            System.out.println("Total admins = " + adminCount);

            if (adminCount < 2) {
                roleBox.getItems().addAll("ADMIN", "PHARMACIST", "CASHIER");
            } else {
                roleBox.getItems().addAll("PHARMACIST", "CASHIER");
            }

        }
        catch (SQLException ex){
            roleBox.getItems().addAll("PHARMACIST", "CASHIER");
            System.err.println("Database error checking admin count: " + ex.getMessage());
        }
        roleBox.setValue(existing != null ? existing.getRole() : "CASHIER");
        CheckBox activeCb = new CheckBox("Active");
        activeCb.setSelected(existing == null || existing.isActive());

        int r = 0;
        grid.add(new Label("Username "),  0, r); grid.add(usernameF, 1, r++);
        if(existing != null){
            Button usrBtn   = new Button("Change Username");
            usrBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;"); // Green style
            usrBtn.setOnAction(e -> showUsernameReset(existing));
            grid.add(new Label(), 0, r); grid.add(usrBtn, 1, r++);
        }

        grid.add(new Label("Full Name "), 0, r); grid.add(fullNameF, 1, r++);

        if (existing == null) {
            grid.add(new Label("Password "), 0, r); grid.add(passF, 1, r++);
        }
        else{
            Button pwdBtn = new Button("Reset Password");
            pwdBtn.setStyle("-fx-background-color: #2162BC; -fx-text-fill: white; -fx-cursor: hand;");
            pwdBtn.setOnAction(e -> showPasswordReset(existing));
            grid.add(new Label("Password"), 0, r); grid.add(pwdBtn, 1, r++);
        }

        if(existing != null && existing.getUserId() == 1){
            roleBox.setDisable(true);
            grid.add(roleBox,1, r++);
        }
        else {
            grid.add(new Label("Role"), 0, r);
            grid.add(roleBox, 1, r++);
        }
        grid.add(activeCb,                 1, r);

        if (existing != null) usernameF.setDisable(true);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (usernameF.getText().trim().isEmpty() || fullNameF.getText().trim().isEmpty()) {
                AlertUtil.showError("Validation", "Username and full name are required."); return null;
            }
            if (existing == null && passF.getText().length() < 6) {
                AlertUtil.showError("Validation", "Password must be at least 6 characters."); return null;
            }
            User u = existing != null ? existing : new User();
            u.setUsername(usernameF.getText().trim());
            u.setFullName(fullNameF.getText().trim());
            u.setRole(roleBox.getValue());
            u.setActive(activeCb.isSelected());
            if (existing == null) u.setPasswordHash(passF.getText()); // plain, hashed in DAO
            return u;
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(u -> {
            try {
                if (existing == null) {
                    dao.insertUser(u, u.getPasswordHash());
                    AuditLogger.log("ADD_USER", "users", null, "Created user: " + u.getUsername());
                } else {
                    dao.updateUser(u);
                    AuditLogger.log("EDIT_USER", "users", u.getUserId(), "Edited user: " + u.getUsername());
                }
                loadAll();
            } catch (Exception e) { AlertUtil.showError("Save Failed", e.getMessage()); }
        });
    }

    private void showPasswordReset(User u) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password for: " + u.getUsername());
        dialog.setContentText("New password:");
        dialog.initModality(Modality.APPLICATION_MODAL);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(pwd -> {
            if (pwd.length() < 6) { AlertUtil.showError("Validation", "Password too short (min 6)."); return; }
            try {
                // FIXED: Use adminResetPassword instead of changePassword
                dao.adminResetPassword(u.getUserId(), pwd);
                AuditLogger.log("RESET_PASSWORD", "users", u.getUserId(), "Password reset for: " + u.getUsername());
                AlertUtil.showInfo("Done", "Password updated successfully.");
            } catch (Exception e) { AlertUtil.showError("Error", e.getMessage()); }
        });
    }

    private void showUsernameReset(User u) {
        // Pre-fill the box with their current username so it's easier to edit
        TextInputDialog dialog = new TextInputDialog(u.getUsername());
        dialog.setTitle("Change Username");
        dialog.setHeaderText("Change username for: " + u.getUsername());
        dialog.setContentText("New username:");
        dialog.initModality(Modality.APPLICATION_MODAL);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newUsername -> {
            if (newUsername.length() < 4) {
                AlertUtil.showError("Validation", "Username too short (min 4).");
                return;
            }
            try {
                dao.changeUsername(u.getUserId(), newUsername);
                AuditLogger.log("CHANGE_USERNAME", "users", u.getUserId(), "Username changed from " + u.getUsername() + " to " + newUsername);
                AlertUtil.showInfo("Done", "Username updated successfully.");

                loadAll(); // Refresh the table so the new name appears immediately!

            } catch (Exception e) {
                AlertUtil.showError("Error", "Could not change username. It might already be taken by someone else.");
            }
        });
    }

    private TextField f(String val) { TextField tf = new TextField(val); tf.setPrefWidth(220); return tf; }

    @SuppressWarnings("unchecked")
    private void col(TableView<User> t, String title, javafx.util.Callback<TableColumn.CellDataFeatures<User, String>, javafx.beans.value.ObservableValue<String>> f, double w) {
        TableColumn<User, String> c = new TableColumn<>(title); c.setCellValueFactory(f); c.setPrefWidth(w); t.getColumns().add(c);
    }

    private void handleDelete(User u) {
        // 1. Safeguard against deleting Admins
        if (u.getUserId() == 1) {
            AlertUtil.showError("Action Denied", "System Administrators cannot be deleted.");
            return;
        }

        // 2. Safeguard against deleting yourself
        int currentUserId = SessionManager.getInstance().getCurrentUser().getUserId();
        if (u.getUserId() == currentUserId) {
            AlertUtil.showError("Action Denied", "You cannot delete your own account.");
            return;
        }

        // 3. Confirm and execute
        boolean confirm = AlertUtil.showConfirm("Deactivate User",
                "Are you sure you want to deactivate " + u.getUsername() + "? They will no longer be able to log in.");

        if (confirm) {
            try {
                // FIXED: Use deactivateUser instead of deleteUser
                boolean success = dao.deactivateUser(u.getUserId());
                if (success) {
                    AuditLogger.log("DEACTIVATE_USER", "users", u.getUserId(), "Deactivated user account: " + u.getUsername());
                    loadAll(); // Refresh the table
                } else {
                    AlertUtil.showError("Database Error", "Could not deactivate user.");
                }
            } catch (Exception e) {
                AlertUtil.showError("Error", e.getMessage());
            }
        }
    }
}

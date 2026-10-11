package com.pharmacy.view.patient;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.model.Patient;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.ConfirmationDialog;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;


public class PatientManagementView {

    private final PatientDAO patientDAO = new PatientDAO();

    private TableView<Patient> table;
    private TextField searchField;
    private Label statusLabel;

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");

        // ── Header ────────────────────────────────────────────
        HBox header = buildPageHeader();
        page.getChildren().add(header);

        // ── Toolbar ───────────────────────────────────────────
        HBox toolbar = buildToolbar();
        toolbar.setPadding(new Insets(16, 24, 12, 24));
        toolbar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent " +
                AppUIManager.COLOR_BORDER + " transparent; -fx-border-width: 0 0 1 0;");
        page.getChildren().add(toolbar);

        // ── Table ─────────────────────────────────────────────
        VBox tableWrapper = new VBox(0);
        tableWrapper.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);
        tableWrapper.getChildren().add(buildTable());
        page.getChildren().add(tableWrapper);

        // ── Status bar ────────────────────────────────────────
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(8, 24, 8, 24));
        statusBar.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: " +
                AppUIManager.COLOR_BORDER + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        statusLabel = new Label("Loading patients…");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        statusBar.getChildren().add(statusLabel);
        page.getChildren().add(statusBar);

        loadPatients(null);

        // Keyboard shortcuts
        page.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case N -> { if (e.isControlDown()) openNewPatientForm(); }
                case DELETE -> deleteSelected();
                case F5 -> loadPatients(searchField.getText());
                default -> {}
            }
        });
        page.setFocusTraversable(true);

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true); sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        return sp;
    }

    // ── Header ────────────────────────────────────────────────
    private HBox buildPageHeader() {
        HBox h = new HBox(12);
        h.setPadding(new Insets(22, 24, 16, 24));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent " +
                AppUIManager.COLOR_BORDER + " transparent; -fx-border-width: 0 0 1 0;");

        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("👤  Patient Management"),
                label13("Register, search, and manage patient records, allergies, and history.", AppUIManager.COLOR_TEXT_MEDIUM)
        );

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button newBtn = AppUIManager.primaryButton("➕  New Patient ");
        newBtn.setOnAction(e -> openNewPatientForm());

        h.getChildren().addAll(tb, sp, newBtn);
        return h;
    }

    // ── Toolbar ───────────────────────────────────────────────
    private HBox buildToolbar() {
        HBox tb = new HBox(10);
        tb.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍  Search by name, phone, NIC, or patient code…");
        searchField.setPrefWidth(340);
        searchField.setPrefHeight(36);
        searchField.setStyle("-fx-background-radius:6; -fx-border-radius:6; -fx-border-color:#CBD5E1; -fx-border-width:1; -fx-font-size:13px; -fx-padding:4 10;");
        searchField.setOnAction(e -> loadPatients(searchField.getText()));
        searchField.textProperty().addListener((obs, o, v) -> {
            if (v.length() >= 3 || v.isEmpty()) loadPatients(v);
        });

        Button searchBtn = AppUIManager.primaryButton("Search");
        searchBtn.setOnAction(e -> loadPatients(searchField.getText()));

        Button clearBtn = AppUIManager.outlineButton("Clear");
        clearBtn.setOnAction(e -> { searchField.clear(); loadPatients(null); });

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button editBtn = AppUIManager.outlineButton("✏  Edit");
        editBtn.setOnAction(e -> editSelected());

        Button histBtn = AppUIManager.outlineButton("📋  History");
        histBtn.setOnAction(e -> viewHistory());

        Button delBtn = AppUIManager.dangerButton("🗑  Delete");
        delBtn.setOnAction(e -> deleteSelected());

        Button refreshBtn = AppUIManager.outlineButton("↻  Refresh [F5]");
        refreshBtn.setOnAction(e -> loadPatients(searchField.getText()));

        tb.getChildren().addAll(searchField, searchBtn, clearBtn, sp, editBtn, histBtn, delBtn, refreshBtn);
        return tb;
    }

    // ── Table ─────────────────────────────────────────────────
    private Node buildTable() {
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setPlaceholder(new Label("No patients found"));
        table.setFixedCellSize(36);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Patient, String> codeCol = col("Patient Code", 110,
                p -> p.getPatientCode() == null ? "" : p.getPatientCode());
        TableColumn<Patient, String> nameCol = col("Full Name", 200,
                p -> p.getFirstName() + " " + p.getLastName());
        TableColumn<Patient, String> phoneCol = col("Phone", 120, Patient::getPhone);
        TableColumn<Patient, String> nicCol = col("NIC", 130,
                p -> p.getNicNumber() == null ? "—" : p.getNicNumber());
        TableColumn<Patient, String> genderCol = col("Gender", 80,
                p -> p.getGender() == null ? "—" : p.getGender().toString());
        TableColumn<Patient, String> bloodCol = col("Blood Group", 90,
                p -> p.getBloodGroup() == null ? "—" : p.getBloodGroup().toString());
        TableColumn<Patient, String> allergyCol = col("Allergies", 160,
                p -> p.getAllergies() == null || p.getAllergies().isEmpty() ? "None" :
                        String.join(", ", p.getAllergies()));
        TableColumn<Patient, String> pointsCol = col("Loyalty Pts", 90,
                p -> String.valueOf(p.getLoyaltyPoints()));
        TableColumn<Patient, String> activeCol = col("Status", 70,
                p -> p.isActive() ? "Active" : "Inactive");

        // Colour-code active status
        activeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle("-fx-text-fill: " + ("Active".equals(v) ? AppUIManager.COLOR_SUCCESS : AppUIManager.COLOR_DANGER) +
                        "; -fx-font-weight: bold;");
            }
        });

        // Double-click to edit
        table.setRowFactory(tv -> {
            TableRow<Patient> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) editSelected(); });
            return row;
        });

        table.getColumns().addAll(codeCol, nameCol, phoneCol, nicCol, genderCol, bloodCol, allergyCol, pointsCol, activeCol);
        return table;
    }

    // ── Data ──────────────────────────────────────────────────
    private void loadPatients(String query) {
        table.getItems().clear();
        try {
            List<Patient> patients = (query == null || query.isBlank())
                    ? patientDAO.getAll()
                    : patientDAO.search(query);
            table.getItems().addAll(patients);
            statusLabel.setText(patients.size() + " patient" + (patients.size() == 1 ? "" : "s") + " found");
        } catch (Exception ex) {
            statusLabel.setText("Error loading patients: " + ex.getMessage());
            MessageDialog.showError("Load Error", "Could not load patients:\n" + ex.getMessage());
        }
    }

    // ── Actions ───────────────────────────────────────────────
    private void openNewPatientForm() {
        PatientFormDialog dialog = new PatientFormDialog(null);
        dialog.showAndWait();
        loadPatients(null);
    }

    private void editSelected() {
        Patient selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            MessageDialog.showWarning("No Selection", "Please select a patient to edit.");
            return;
        }
        new PatientFormDialog(selected).showAndWait();
        loadPatients(searchField.getText());
    }

    private void viewHistory() {
        Patient selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            MessageDialog.showWarning("No Selection", "Please select a patient to view history.");
            return;
        }
        new PatientHistoryView(selected).show();
    }

    private void deleteSelected() {
        Patient selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            MessageDialog.showWarning("No Selection", "Please select a patient to delete.");
            return;
        }
        if (ConfirmationDialog.confirmDelete("patient '" + selected.getFirstName() + " " + selected.getLastName() + "'")) {
            try {
                patientDAO.softDelete(selected.getPatientId());
                loadPatients(searchField.getText());
                MessageDialog.showSuccess("Deleted", "Patient has been deactivated successfully.");
            } catch (Exception ex) {
                MessageDialog.showError("Delete Failed", "Could not delete patient:\n" + ex.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private <T> TableColumn<Patient, T> col(String title, double width,
                                            java.util.function.Function<Patient, T> extractor) {
        TableColumn<Patient, T> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setResizable(true);
        col.setCellValueFactory(data -> {
            T val = extractor.apply(data.getValue());
            if (val instanceof String s)
                return (javafx.beans.value.ObservableValue<T>) new SimpleStringProperty(s);
            return new javafx.beans.property.SimpleObjectProperty<>(val);
        });
        return col;
    }

    private Label label13(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: " + color + ";");
        return l;
    }
}
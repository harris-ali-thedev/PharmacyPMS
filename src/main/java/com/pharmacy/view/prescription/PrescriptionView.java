package com.pharmacy.view.prescription;

import com.pharmacy.dao.PrescriptionDAO;
import com.pharmacy.model.Prescription;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PrescriptionView — Browse, search, and process prescriptions.
 */
public class PrescriptionView {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final PrescriptionDAO rxDAO = new PrescriptionDAO();

    private TableView<Prescription> table;
    private TextField searchField;
    private ComboBox<String> statusCombo;
    private Label statusLabel;

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");
        page.getChildren().addAll(buildHeader(), buildToolbar());

        VBox tw = new VBox(0);
        tw.setPadding(new Insets(20, 24, 16, 24));
        VBox.setVgrow(tw, Priority.ALWAYS);
        tw.getChildren().add(buildTable());
        page.getChildren().add(tw);

        HBox bar = new HBox();
        bar.setPadding(new Insets(6, 24, 6, 24));
        bar.setStyle("-fx-background-color:#F8FAFC; -fx-border-color:" + AppUIManager.COLOR_BORDER + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        statusLabel = new Label("Loading…");
        statusLabel.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        bar.getChildren().add(statusLabel);
        page.getChildren().add(bar);

        loadPrescriptions(null, "ALL");

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true); sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return sp;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setPadding(new Insets(22, 24, 16, 24));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("💊  Prescriptions"),
                label("Process, verify, and track prescriptions.", AppUIManager.COLOR_TEXT_MEDIUM)
        );
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button newBtn = AppUIManager.primaryButton("➕  New Prescription");
        newBtn.setOnAction(e -> new PrescriptionFormView().showDialog());
        h.getChildren().addAll(tb, sp, newBtn);
        return h;
    }

    private HBox buildToolbar() {
        HBox tb = new HBox(10);
        tb.setPadding(new Insets(14, 24, 12, 24));
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");

        searchField = new TextField();
        searchField.setPromptText("🔍  Search by Rx number, patient, doctor…");
        searchField.setPrefWidth(300); searchField.setPrefHeight(36);
        searchField.setStyle("-fx-background-radius:6; -fx-border-radius:6; -fx-border-color:#CBD5E1; -fx-border-width:1; -fx-font-size:13px; -fx-padding:4 10;");
        searchField.textProperty().addListener((obs, o, v) -> loadPrescriptions(v, statusCombo.getValue()));

        statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("ALL", "PENDING", "VERIFIED", "DISPENSED", "CANCELLED", "EXPIRED");
        statusCombo.setValue("ALL");
        statusCombo.setStyle("-fx-font-size:13px;");
        statusCombo.setOnAction(e -> loadPrescriptions(searchField.getText(), statusCombo.getValue()));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button dispensBtn = AppUIManager.successButton("✔  Dispense");
        dispensBtn.setOnAction(e -> dispenseSelected());

        Button interactionBtn = AppUIManager.warningButton("⚠  Check Interactions");
        interactionBtn.setOnAction(e -> checkInteractions());

        Button refreshBtn = AppUIManager.outlineButton("↻  Refresh");
        refreshBtn.setOnAction(e -> loadPrescriptions(searchField.getText(), statusCombo.getValue()));

        tb.getChildren().addAll(searchField, statusCombo, sp, interactionBtn, dispensBtn, refreshBtn);
        return tb;
    }

    private Node buildTable() {
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setPlaceholder(new Label("No prescriptions found"));
        table.setFixedCellSize(36);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                strCol("Rx #",         130, rx -> rx.getPrescriptionNumber() == null ? "—" : rx.getPrescriptionNumber()),
//                strCol("Date",         110, rx -> rx.getPrescribedDate() != null ? rx.getPrescribedDate().format(DATE_FMT) : "—"),
                strCol("Patient",      170, rx -> rx.getPatientName() == null ? "—" : rx.getPatientName()),
                strCol("Doctor",       160, rx -> rx.getDoctorName() == null ? "—" : rx.getDoctorName()),
                strCol("Diagnosis",    180, rx -> rx.getDiagnosis() == null ? "—" : rx.getDiagnosis()),
//                strCol("Valid Until",  110, rx -> rx.getValidUntil() != null ? rx.getValidUntil().format(DATE_FMT) : "—"),
//                strCol("Refills",       70, rx -> rx.getRefillsAllowed() + "/" + rx.getRefillsUsed()),
                buildStatusCol()
        );

        table.setRowFactory(tv -> {
            TableRow<Prescription> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) viewDetails(row.getItem()); });
            return row;
        });

        return table;
    }

    private TableColumn<Prescription, String> buildStatusCol() {
        TableColumn<Prescription, String> col = new TableColumn<>("Status");
        col.setPrefWidth(100);
        col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus() == null ? "—" : d.getValue().getStatus()));
        col.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                String color = switch (v) {
                    case "DISPENSED" -> AppUIManager.COLOR_SUCCESS;
                    case "PENDING"   -> AppUIManager.COLOR_WARNING;
                    case "VERIFIED"  -> AppUIManager.COLOR_INFO;
                    case "CANCELLED", "EXPIRED" -> AppUIManager.COLOR_DANGER;
                    default -> AppUIManager.COLOR_TEXT_MEDIUM;
                };
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });
        return col;
    }

    private void loadPrescriptions(String query, String status) {
        table.getItems().clear();
        try {
            List<Prescription> all = (query == null || query.isBlank())
                    ? rxDAO.getAll() : rxDAO.getAll();
            List<Prescription> filtered = "ALL".equals(status) ? all :
                    all.stream().filter(rx -> status.equals(rx.getStatus())).toList();
            table.getItems().addAll(filtered);
            statusLabel.setText(filtered.size() + " prescriptions");
        } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
        }
    }

    private void dispenseSelected() {
        Prescription rx = table.getSelectionModel().getSelectedItem();
        if (rx == null) { MessageDialog.showWarning("No Selection", "Please select a prescription to dispense."); return; }
        new DrugInteractionView(rx).show();
    }

    private void checkInteractions() {
        Prescription rx = table.getSelectionModel().getSelectedItem();
        if (rx == null) { MessageDialog.showWarning("No Selection", "Please select a prescription."); return; }
        new DrugInteractionView(rx).show();
    }

    private void viewDetails(Prescription rx) {
        MessageDialog.showInfo("Prescription Details",
                "Rx #: " + rx.getPrescriptionNumber() + "\n" +
                        "Patient: " + (rx.getPatientName() != null ? rx.getPatientName() : "—") + "\n" +
                        "Doctor: " + (rx.getDoctorName() != null ? rx.getDoctorName() : "—") + "\n" +
                        "Diagnosis: " + (rx.getDiagnosis() != null ? rx.getDiagnosis() : "—") + "\n" +
                        "Status: " + rx.getStatus());
    }

    private TableColumn<Prescription, String> strCol(String title, double w, java.util.function.Function<Prescription, String> fn) {
        TableColumn<Prescription, String> col = new TableColumn<>(title);
        col.setPrefWidth(w);
        col.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return col;
    }
    private Label label(String text, String color) {
        Label l = new Label(text); l.setStyle("-fx-font-size:13px; -fx-text-fill:" + color + ";"); return l;
    }
}
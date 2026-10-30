package com.pharmacy.view.patient;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.model.Patient;
import com.pharmacy.view.PharmacyDesignSystem;
import com.pharmacy.view.dialogs.ConfirmationDialog;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Patient registry — clinical design aligned with MediCare PMS tokens.
 */
public class PatientManagementView {

    private static final String F = PharmacyDesignSystem.FONT;

    private final PatientDAO patientDAO = new PatientDAO();

    private TableView<Patient> table;
    private TextField searchField;
    private Label statusLabel;
    private Label totalMetric;
    private Label activeMetric;
    private Label allergyMetric;
    private Label loyaltyMetric;

    public Node getView() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(22, 24, 24, 24));
        page.setStyle(F + "-fx-background-color: " + PharmacyDesignSystem.BG + ";");

        page.getChildren().addAll(
                buildHeader(),
                buildMetrics(),
                buildDirectoryCard()
        );

        loadPatients(null);

        page.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case N -> {
                    if (e.isControlDown()) openNewPatientForm();
                }
                case DELETE -> deleteSelected();
                case F5 -> loadPatients(searchField.getText());
                default -> {
                }
            }
        });
        page.setFocusTraversable(true);

        return PharmacyDesignSystem.workspace(page);
    }

    // ── Header ───────────────────────────────────────────────

    private HBox buildHeader() {
        Button newBtn = PharmacyDesignSystem.primaryButton("+ New Patient");
        newBtn.setOnAction(e -> openNewPatientForm());

        Button refreshBtn = PharmacyDesignSystem.secondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadPatients(searchField != null ? searchField.getText() : null));

        return PharmacyDesignSystem.pageHeaderWithActions(
                "Patient Management",
                "Register, search, and manage patient records, allergies, and clinical history.",
                refreshBtn,
                newBtn
        );
    }

    // ── KPI strip ────────────────────────────────────────────

    private GridPane buildMetrics() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        totalMetric = metricValueLabel(PharmacyDesignSystem.PRIMARY);
        activeMetric = metricValueLabel(PharmacyDesignSystem.SUCCESS);
        allergyMetric = metricValueLabel(PharmacyDesignSystem.WARNING);
        loyaltyMetric = metricValueLabel(PharmacyDesignSystem.INFO);

        VBox total = metricCard("Total Patients", totalMetric, "In current result set",
                PharmacyDesignSystem.PRIMARY, PharmacyDesignSystem.INFO_BG, "Pt");
        VBox active = metricCard("Active", activeMetric, "Eligible for dispensing",
                PharmacyDesignSystem.SUCCESS, PharmacyDesignSystem.SUCCESS_BG, "✓");
        VBox allergy = metricCard("With Allergies", allergyMetric, "Clinical caution flags",
                PharmacyDesignSystem.WARNING, PharmacyDesignSystem.WARNING_BG, "!");
        VBox loyalty = metricCard("Loyalty Points", loyaltyMetric, "Sum across results",
                PharmacyDesignSystem.INFO, PharmacyDesignSystem.PRIMARY_SOFT, "★");

        grid.add(total, 0, 0);
        grid.add(active, 1, 0);
        grid.add(allergy, 2, 0);
        grid.add(loyalty, 3, 0);
        GridPane.setHgrow(total, Priority.ALWAYS);
        GridPane.setHgrow(active, Priority.ALWAYS);
        GridPane.setHgrow(allergy, Priority.ALWAYS);
        GridPane.setHgrow(loyalty, Priority.ALWAYS);
        return grid;
    }

    private Label metricValueLabel(String accent) {
        Label value = new Label("—");
        value.setStyle(F + "-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: " + accent + ";");
        return value;
    }

    private VBox metricCard(String label, Label valueNode, String detail, String accent, String iconBg, String iconGlyph) {
        StackPane icon = new StackPane();
        icon.setMinSize(44, 44);
        icon.setMaxSize(44, 44);
        icon.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 11;");
        Label glyph = new Label(iconGlyph);
        glyph.setStyle(F + "-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: " + accent + ";");
        icon.getChildren().add(glyph);

        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle(F + "-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + PharmacyDesignSystem.FAINT + ";");
        Label detailNode = new Label(detail);
        detailNode.setStyle(F + "-fx-font-size: 12px; -fx-text-fill: " + PharmacyDesignSystem.MUTED + ";");

        VBox copy = new VBox(2, labelNode, valueNode, detailNode);
        HBox row = new HBox(14, icon, copy);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = PharmacyDesignSystem.card();
        card.setPadding(new Insets(16, 18, 16, 18));
        card.getChildren().setAll(row);
        return card;
    }

    // ── Directory card ───────────────────────────────────────

    private VBox buildDirectoryCard() {
        VBox card = PharmacyDesignSystem.card();
        card.setSpacing(14);
        card.setPadding(new Insets(16, 18, 14, 18));
        VBox.setVgrow(card, Priority.ALWAYS);

        Label eyebrow = PharmacyDesignSystem.sectionEyebrow("Patient directory");
        Label title = new Label("Records");
        title.setStyle(F + "-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: " + PharmacyDesignSystem.TEXT + ";");

        VBox heading = new VBox(2, eyebrow, title);

        HBox toolbar = buildToolbar();
        Node tableNode = buildTable();
        HBox footer = buildFooter();

        VBox.setVgrow(tableNode, Priority.ALWAYS);
        card.getChildren().addAll(heading, toolbar, tableNode, footer);
        return card;
    }

    private HBox buildToolbar() {
        searchField = PharmacyDesignSystem.searchField("Search by name, phone, NIC, or patient code…");
        searchField.setPrefWidth(360);
        searchField.setPrefHeight(36);
        HBox.setHgrow(searchField, Priority.SOMETIMES);
        searchField.setOnAction(e -> loadPatients(searchField.getText()));
        searchField.textProperty().addListener((obs, o, v) -> {
            if (v.length() >= 3 || v.isEmpty()) loadPatients(v);
        });

        Button searchBtn = PharmacyDesignSystem.primaryButton("Search");
        searchBtn.setOnAction(e -> loadPatients(searchField.getText()));
//        searchBtn.setPrefHeight(36);

        Button clearBtn = PharmacyDesignSystem.ghostButton("Clear");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            loadPatients(null);
        });
//        clearBtn.setPrefHeight(36);

        HBox searchCluster = new HBox(8, searchField, searchBtn, clearBtn);
        searchCluster.setAlignment(Pos.CENTER_LEFT);
        searchCluster.setPrefHeight(36);

        Button editBtn = PharmacyDesignSystem.primaryButton("Edit");
        editBtn.setOnAction(e -> editSelected());
        editBtn.setMaxHeight(32);

        Button histBtn = PharmacyDesignSystem.secondaryButton("History");
        histBtn.setOnAction(e -> viewHistory());
        histBtn.setMaxHeight(32);

        Button delBtn = PharmacyDesignSystem.dangerButton("Deactivate");
        delBtn.setOnAction(e -> deleteSelected());
        delBtn.setMaxHeight(32);

        return PharmacyDesignSystem.toolbar(searchCluster, editBtn, histBtn, delBtn);
    }

    private HBox buildFooter() {
        statusLabel = new Label("Loading patients…");
        statusLabel.setStyle(F + "-fx-font-size: 12px; -fx-text-fill: " + PharmacyDesignSystem.MUTED + ";");

        Label hint = new Label("Ctrl+N new  ·  F5 refresh  ·  Del deactivate  ·  Double-click to edit");
        hint.setStyle(F + "-fx-font-size: 11px; -fx-text-fill: " + PharmacyDesignSystem.FAINT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(12, statusLabel, spacer, hint);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(4, 2, 0, 2));
        return footer;
    }

    // ── Table ────────────────────────────────────────────────

    private Node buildTable() {
        table = new TableView<>();
        PharmacyDesignSystem.styleTable(table);
        table.setPlaceholder(PharmacyDesignSystem.emptyState(
                "No patients found",
                "Try another search, or register a new patient with + New Patient."));
        table.setFixedCellSize(40);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(360);

        TableColumn<Patient, String> codeCol = col("Code", 108,
                p -> p.getPatientCode() == null ? "—" : p.getPatientCode());
        styleMonoCell(codeCol);

        TableColumn<Patient, String> nameCol = col("Full Name", 190,
                p -> (p.getFirstName() == null ? "" : p.getFirstName()) + " "
                        + (p.getLastName() == null ? "" : p.getLastName()));
        nameCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Patient p = getTableRow() != null ? getTableRow().getItem() : null;
                Label name = new Label(v.trim());
                name.setStyle(F + "-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + PharmacyDesignSystem.TEXT + ";");
                String meta = p == null ? "" : buildNameMeta(p);
                Label sub = new Label(meta);
                sub.setStyle(F + "-fx-font-size: 11px; -fx-text-fill: " + PharmacyDesignSystem.FAINT + ";");
                VBox box = new VBox(1, name, sub);
                setGraphic(box);
                setText(null);
            }
        });

        TableColumn<Patient, String> phoneCol = col("Phone", 118,
                p -> blankToDash(p.getPhone()));
        TableColumn<Patient, String> nicCol = col("NIC", 124,
                p -> blankToDash(p.getNicNumber()));
        TableColumn<Patient, String> genderCol = col("Gender", 78,
                p -> p.getGender() == null ? "—" : p.getGender().toString());

        TableColumn<Patient, String> bloodCol = col("Blood", 88,
                p -> p.getBloodGroup() == null ? "—" : p.getBloodGroup().toString());
        bloodCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null || "—".equals(v)) {
                    setGraphic(null);
                    setText(empty ? null : "—");
                    setStyle(F + "-fx-text-fill: " + PharmacyDesignSystem.FAINT + ";");
                    return;
                }
                setText(null);
                setGraphic(PharmacyDesignSystem.infoBadge(v));
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        TableColumn<Patient, String> allergyCol = col("Allergies", 170,
                p -> p.getAllergies() == null || p.getAllergies().isEmpty() ? "None"
                        : String.join(", ", p.getAllergies()));
        allergyCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                boolean none = "None".equals(v);
                setText(null);
                setGraphic(none
                        ? PharmacyDesignSystem.successBadge("None")
                        : PharmacyDesignSystem.warningBadge(truncate(v, 28)));
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        TableColumn<Patient, String> pointsCol = col("Loyalty", 82,
                p -> String.valueOf(p.getLoyaltyPoints()));
        pointsCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(v);
                setStyle(F + "-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + PharmacyDesignSystem.PRIMARY + ";");
            }
        });

        TableColumn<Patient, String> activeCol = col("Status", 92,
                p -> p.isActive() ? "Active" : "Inactive");
        activeCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setText(null);
                setGraphic("Active".equals(v)
                        ? PharmacyDesignSystem.successBadge("Active")
                        : PharmacyDesignSystem.dangerBadge("Inactive"));
                setAlignment(Pos.CENTER_LEFT);
            }
        });

//        TableColumn<Patient, Void> actionCol = new TableColumn<>("Actions");
//        actionCol.setPrefWidth(168);
//        actionCol.setMinWidth(150);
//        actionCol.setCellFactory(tc -> new TableCell<>() {
//            private final Button edit = PharmacyDesignSystem.tableActionButton("Edit", PharmacyDesignSystem.PRIMARY);
//            private final Button hist = PharmacyDesignSystem.tableActionButton("Hx", PharmacyDesignSystem.INFO);
//            private final Button del = PharmacyDesignSystem.tableActionButton("Del", PharmacyDesignSystem.DANGER);
//            private final HBox box = new HBox(6, edit, hist, del);
//
//            {
//                box.setAlignment(Pos.CENTER_LEFT);
//            }
//
//            @Override
//            protected void updateItem(Void item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty) {
//                    setGraphic(null);
//                    return;
//                }
//                Patient p = getTableView().getItems().get(getIndex());
//                edit.setOnAction(e -> openEdit(p));
//                hist.setOnAction(e -> new PatientHistoryView(p).show());
//                del.setOnAction(e -> deactivate(p));
//                setGraphic(box);
//            }
//        });

        table.setRowFactory(tv -> {
            TableRow<Patient> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) editSelected();
            });
            return row;
        });

        table.getColumns().addAll(
                codeCol, nameCol, phoneCol, nicCol, genderCol,
                bloodCol, allergyCol, pointsCol, activeCol
        );
        return table;
    }

    private void styleMonoCell(TableColumn<Patient, String> column) {
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    return;
                }
                setText(v);
                setStyle(F + "-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: " + PharmacyDesignSystem.MUTED + ";");
            }
        });
    }

    private String buildNameMeta(Patient p) {
        StringBuilder sb = new StringBuilder();
        int age = p.getAge();
        if (age >= 0) sb.append(age).append(" yrs");
        if (p.getLoyaltyTier() != null && !p.getLoyaltyTier().isBlank()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(p.getLoyaltyTier());
        }
        if (sb.isEmpty()) return "Patient record";
        return sb.toString();
    }

    // ── Data ─────────────────────────────────────────────────

    private void loadPatients(String query) {
        table.getItems().clear();
        try {
            List<Patient> patients = (query == null || query.isBlank())
                    ? patientDAO.getAll()
                    : patientDAO.search(query);
            table.getItems().addAll(patients);
            updateMetrics(patients);
            int n = patients.size();
            statusLabel.setText(n + " patient" + (n == 1 ? "" : "s") + " found");
        } catch (Exception ex) {
            statusLabel.setText("Error loading patients");
            totalMetric.setText("—");
            activeMetric.setText("—");
            allergyMetric.setText("—");
            loyaltyMetric.setText("—");
            MessageDialog.showError("Load Error", "Could not load patients:\n" + ex.getMessage());
        }
    }

    private void updateMetrics(List<Patient> patients) {
        long active = patients.stream().filter(Patient::isActive).count();
        long withAllergies = patients.stream()
                .filter(p -> p.getAllergies() != null && !p.getAllergies().isEmpty())
                .count();
        int points = patients.stream().mapToInt(Patient::getLoyaltyPoints).sum();

        totalMetric.setText(String.valueOf(patients.size()));
        activeMetric.setText(String.valueOf(active));
        allergyMetric.setText(String.valueOf(withAllergies));
        loyaltyMetric.setText(String.format("%,d", points));
    }

    // ── Actions ──────────────────────────────────────────────

    private void openNewPatientForm() {
        new PatientFormDialog(null).showAndWait();
        loadPatients(null);
    }

    private void editSelected() {
        Patient selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            MessageDialog.showWarning("No Selection", "Please select a patient to edit.");
            return;
        }
        openEdit(selected);
    }

    private void openEdit(Patient patient) {
        new PatientFormDialog(patient).showAndWait();
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
            MessageDialog.showWarning("No Selection", "Please select a patient to deactivate.");
            return;
        }
        deactivate(selected);
    }

    private void deactivate(Patient selected) {
        if (ConfirmationDialog.confirmDelete(
                "patient '" + selected.getFirstName() + " " + selected.getLastName() + "'")) {
            try {
                patientDAO.softDelete(selected.getPatientId());
                loadPatients(searchField.getText());
                MessageDialog.showSuccess("Deleted", "Patient has been deactivated successfully.");
            } catch (Exception ex) {
                MessageDialog.showError("Delete Failed", "Could not delete patient:\n" + ex.getMessage());
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Patient, T> col(String title, double width,
                                            java.util.function.Function<Patient, T> extractor) {
        TableColumn<Patient, T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setResizable(true);
        column.setCellValueFactory(data -> {
            T val = extractor.apply(data.getValue());
            if (val instanceof String s) {
                return (javafx.beans.value.ObservableValue<T>) new SimpleStringProperty(s);
            }
            return new javafx.beans.property.SimpleObjectProperty<>(val);
        });
        return column;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }
}

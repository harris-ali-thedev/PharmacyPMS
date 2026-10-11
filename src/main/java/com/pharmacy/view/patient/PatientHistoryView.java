package com.pharmacy.view.patient;

import com.pharmacy.model.Patient;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PatientHistoryView {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final Patient patient;

    private Stage stage;
    private Label prescriptionCountLabel;
    private Label saleCountLabel;
    private Label totalSpentLabel;
    private Label lastVisitLabel;
    private Label allergiesLabel;
    private Label conditionsLabel;
    private Label notesLabel;
    private TableView<PrescriptionHistoryRow> prescriptionsTable;
    private TableView<SaleHistoryRow> salesTable;

    public PatientHistoryView(Patient p) {
        this.patient = p;
    }

    public void show() {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Patient History - " + patient.getDisplayName().trim());
        stage.setWidth(1100);
        stage.setHeight(760);
        stage.setMinWidth(960);
        stage.setMinHeight(680);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");
        root.getChildren().addAll(buildHeader(), buildBody());

        loadHistory();

        stage.setScene(new Scene(root));
        stage.show();
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent " +
                AppUIManager.COLOR_BORDER + " transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(4);
        titleBox.getChildren().addAll(
                AppUIManager.pageTitle("Patient History"),
                subLabel(displayIdentityLine())
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = AppUIManager.badge(patient.isActive() ? "ACTIVE" : "INACTIVE",
                patient.isActive() ? AppUIManager.COLOR_SUCCESS : AppUIManager.COLOR_DANGER);

        header.getChildren().addAll(titleBox, spacer, statusBadge);
        return header;
    }

    private ScrollPane buildBody() {
        VBox body = new VBox(20);
        body.setPadding(new Insets(24));

        body.getChildren().add(buildOverviewCard());
        body.getChildren().add(buildProfileCard());
        body.getChildren().add(buildPrescriptionsCard());
        body.getChildren().add(buildSalesCard());

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return scrollPane;
    }

    private VBox buildOverviewCard() {
        VBox card = AppUIManager.card();
        card.getChildren().add(AppUIManager.sectionTitle("Overview"));

        HBox stats = new HBox(14);
        stats.getChildren().addAll(
                statBox("Prescriptions", prescriptionCountLabel = statValue("0")),
                statBox("Sales", saleCountLabel = statValue("0")),
                statBox("Total Spent", totalSpentLabel = statValue("PKR 0.00")),
                statBox("Last Visit", lastVisitLabel = statValue("—"))
        );

        card.getChildren().add(stats);
        return card;
    }

    private VBox buildProfileCard() {
        VBox card = AppUIManager.card();
        card.getChildren().add(AppUIManager.sectionTitle("Patient Details"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);

        addDetail(grid, 0, 0, "Patient Code", safe(patient.getPatientCode()));
        addDetail(grid, 1, 0, "Full Name", safe(patient.getDisplayName().trim()));
        addDetail(grid, 2, 0, "Phone", safe(patient.getPhone()));
        addDetail(grid, 0, 1, "Gender", patient.getGender() == null ? "—" : patient.getGender().name());
        addDetail(grid, 1, 1, "Blood Group", patient.getBloodGroup() == null ? "—" : patient.getBloodGroup().name());
        addDetail(grid, 2, 1, "Age", patient.getAge() < 0 ? "—" : patient.getAge() + " years");
        addDetail(grid, 0, 2, "Date of Birth", formatDate(patient.getDateOfBirth()));
        addDetail(grid, 1, 2, "Email", safe(patient.getEmail()));
        addDetail(grid, 2, 2, "NIC", safe(patient.getNicNumber()));
        addDetail(grid, 0, 3, "Address", safe(patient.getAddress()));
        addDetail(grid, 1, 3, "Loyalty Tier", safe(patient.getLoyaltyTier()));
        addDetail(grid, 2, 3, "Loyalty Points", String.valueOf(patient.getLoyaltyPoints()));

        allergiesLabel = paragraphLabel(joinList(patient.getAllergies()));
        conditionsLabel = paragraphLabel(joinList(patient.getChronicConditions()));
        notesLabel = paragraphLabel(firstNonBlank(patient.getClinicalNotes(), patient.getNotes(), "—"));

        card.getChildren().addAll(
                grid,
                AppUIManager.hDivider(),
                detailBlock("Allergies", allergiesLabel),
                detailBlock("Chronic Conditions", conditionsLabel),
                detailBlock("Clinical Notes", notesLabel)
        );
        return card;
    }

    private VBox buildPrescriptionsCard() {
        VBox card = AppUIManager.card();
        card.getChildren().add(AppUIManager.sectionTitle("Prescription History"));

        prescriptionsTable = new TableView<>();
        AppUIManager.styleTable(prescriptionsTable);
        prescriptionsTable.setPlaceholder(new Label("No prescriptions found for this patient"));
        prescriptionsTable.setFixedCellSize(36);
        VBox.setVgrow(prescriptionsTable, Priority.ALWAYS);

        prescriptionsTable.getColumns().addAll(
                column("Prescription", 130, PrescriptionHistoryRow::getPrescriptionNumber),
                column("Issued", 110, PrescriptionHistoryRow::getIssuedDate),
                column("Doctor", 180, PrescriptionHistoryRow::getDoctorName),
                column("Clinic", 160, PrescriptionHistoryRow::getClinicName),
                statusColumn("Status", 100, PrescriptionHistoryRow::getStatus),
                column("Diagnosis", 180, PrescriptionHistoryRow::getDiagnosis),
                column("Medicines", 300, PrescriptionHistoryRow::getMedicines)
        );

        card.getChildren().add(prescriptionsTable);
        return card;
    }

    private VBox buildSalesCard() {
        VBox card = AppUIManager.card();
        card.getChildren().add(AppUIManager.sectionTitle("Sales History"));

        salesTable = new TableView<>();
        AppUIManager.styleTable(salesTable);
        salesTable.setPlaceholder(new Label("No sales found for this patient"));
        salesTable.setFixedCellSize(36);
        VBox.setVgrow(salesTable, Priority.ALWAYS);

        salesTable.getColumns().addAll(
                column("Invoice", 110, SaleHistoryRow::getInvoiceNumber),
                column("Date", 165, SaleHistoryRow::getSaleDate),
                column("Payment", 120, SaleHistoryRow::getPaymentMethod),
                statusColumn("Status", 100, SaleHistoryRow::getStatus),
                moneyColumn("Total", 120, SaleHistoryRow::getGrandTotal),
                column("Prescription", 120, SaleHistoryRow::getPrescriptionReference),
                column("Items", 340, SaleHistoryRow::getItems)
        );

        card.getChildren().add(salesTable);
        return card;
    }

    private void loadHistory() {
        try {
            List<PrescriptionHistoryRow> prescriptionRows = fetchPrescriptionHistory();
            List<SaleHistoryRow> saleRows = fetchSaleHistory();

            prescriptionsTable.setItems(FXCollections.observableArrayList(prescriptionRows));
            salesTable.setItems(FXCollections.observableArrayList(saleRows));

            prescriptionCountLabel.setText(String.valueOf(prescriptionRows.size()));
            saleCountLabel.setText(String.valueOf(saleRows.size()));
            totalSpentLabel.setText(formatMoney(calculateTotalSpent(saleRows)));
            lastVisitLabel.setText(resolveLastVisit(prescriptionRows, saleRows));

            allergiesLabel.setText(joinList(patient.getAllergies()));
            conditionsLabel.setText(joinList(patient.getChronicConditions()));
            notesLabel.setText(firstNonBlank(patient.getClinicalNotes(), patient.getNotes(), "—"));
        } catch (Exception ex) {
            MessageDialog.showError("History Load Error", "Could not load patient history:\n" + ex.getMessage());
        }
    }

    private List<PrescriptionHistoryRow> fetchPrescriptionHistory() throws Exception {
        List<PrescriptionHistoryRow> rows = new ArrayList<>();
        String sql = """
                SELECT
                    p.prescription_id,
                    p.prescription_number,
                    p.issued_date,
                    p.created_at,
                    p.doctor_name,
                    p.clinic_name,
                    p.status,
                    p.diagnosis,
                    COALESCE(
                        GROUP_CONCAT(
                            COALESCE(pi.medicine_name, CONCAT('Medicine #', pi.med_id))
                            ORDER BY pi.item_id SEPARATOR ', '
                        ),
                        '—'
                    ) AS medicines
                FROM prescriptions p
                LEFT JOIN prescription_items pi ON pi.prescription_id = p.prescription_id
                WHERE p.patient_id = ?
                GROUP BY
                    p.prescription_id, p.prescription_number, p.issued_date, p.created_at,
                    p.doctor_name, p.clinic_name, p.status, p.diagnosis
                ORDER BY COALESCE(p.issued_date, DATE(p.created_at)) DESC, p.created_at DESC
                """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patient.getPatientId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PrescriptionHistoryRow row = new PrescriptionHistoryRow();
                    row.setPrescriptionNumber(firstNonBlank(rs.getString("prescription_number"),
                            "RX-" + rs.getInt("prescription_id")));
                    row.setIssuedDate(formatDate(firstNonNull(toLocalDate(rs.getDate("issued_date")),
                            toLocalDateTime(rs.getTimestamp("created_at")) != null
                                    ? toLocalDateTime(rs.getTimestamp("created_at")).toLocalDate()
                                    : null)));
                    row.setDoctorName(safe(rs.getString("doctor_name")));
                    row.setClinicName(safe(rs.getString("clinic_name")));
                    row.setStatus(safe(rs.getString("status")));
                    row.setDiagnosis(firstNonBlank(rs.getString("diagnosis"), "—"));
                    row.setMedicines(firstNonBlank(rs.getString("medicines"), "—"));
                    row.setSortDate(firstNonNull(
                            toLocalDate(rs.getDate("issued_date")) != null
                                    ? toLocalDate(rs.getDate("issued_date")).atStartOfDay()
                                    : null,
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    ));
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private List<SaleHistoryRow> fetchSaleHistory() throws Exception {
        List<SaleHistoryRow> rows = new ArrayList<>();
        String sql = """
                SELECT
                    s.sale_id,
                    s.sale_date,
                    s.payment_method,
                    s.status,
                    s.grand_total,
                    s.prescription_id,
                    COALESCE(
                        GROUP_CONCAT(
                            CONCAT(COALESCE(m.name, CONCAT('Medicine #', si.med_id)), ' x', si.quantity)
                            ORDER BY si.sale_item_id SEPARATOR ', '
                        ),
                        '—'
                    ) AS items
                FROM sales s
                LEFT JOIN sale_items si ON si.sale_id = s.sale_id
                LEFT JOIN medicines m ON m.med_id = si.med_id
                WHERE s.patient_id = ?
                GROUP BY
                    s.sale_id, s.sale_date, s.payment_method, s.status,
                    s.grand_total, s.prescription_id
                ORDER BY s.sale_date DESC, s.sale_id DESC
                """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patient.getPatientId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleHistoryRow row = new SaleHistoryRow();
                    row.setInvoiceNumber("SALE-" + rs.getInt("sale_id"));
                    row.setSaleDate(formatDateTime(toLocalDateTime(rs.getTimestamp("sale_date"))));
                    row.setPaymentMethod(safe(rs.getString("payment_method")));
                    row.setStatus(safe(rs.getString("status")));
                    row.setGrandTotal(rs.getDouble("grand_total"));
                    row.setPrescriptionReference(rs.getObject("prescription_id") == null
                            ? "—"
                            : "RX-" + rs.getInt("prescription_id"));
                    row.setItems(firstNonBlank(rs.getString("items"), "—"));
                    row.setSortDate(toLocalDateTime(rs.getTimestamp("sale_date")));
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private double calculateTotalSpent(List<SaleHistoryRow> rows) {
        double total = 0.0;
        for (SaleHistoryRow row : rows) {
            if (!"VOIDED".equalsIgnoreCase(row.getStatus())) {
                total += row.getGrandTotal();
            }
        }
        return total;
    }

    private String resolveLastVisit(List<PrescriptionHistoryRow> prescriptions, List<SaleHistoryRow> sales) {
        LocalDateTime latest = null;

        for (PrescriptionHistoryRow row : prescriptions) {
            latest = latest == null || (row.getSortDate() != null && row.getSortDate().isAfter(latest))
                    ? row.getSortDate() : latest;
        }
        for (SaleHistoryRow row : sales) {
            latest = latest == null || (row.getSortDate() != null && row.getSortDate().isAfter(latest))
                    ? row.getSortDate() : latest;
        }

        return latest == null ? "—" : formatDateTime(latest);
    }

    private VBox statBox(String label, Label valueLabel) {
        VBox box = new VBox(6);
        box.setPrefWidth(220);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10; " +
                "-fx-border-color: " + AppUIManager.COLOR_BORDER + "; -fx-border-radius: 10;");

        Label title = new Label(label);
        title.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        box.getChildren().addAll(title, valueLabel);
        return box;
    }

    private Label statValue(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return label;
    }

    private VBox detailBlock(String title, Label content) {
        VBox box = new VBox(6);
        box.getChildren().addAll(AppUIManager.fieldLabel(title), content);
        return box;
    }

    private void addDetail(GridPane grid, int col, int row, String label, String value) {
        VBox box = new VBox(5);
        box.getChildren().addAll(AppUIManager.fieldLabel(label), paragraphLabel(value));
        grid.add(box, col, row);
    }

    private Label paragraphLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return label;
    }

    private Label subLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return label;
    }

    private String displayIdentityLine() {
        return safe(patient.getPatientCode()) + "  •  " + safe(patient.getDisplayName().trim()) +
                "  •  " + safe(patient.getPhone());
    }

    private <T, V> TableColumn<T, V> column(String title, double width, java.util.function.Function<T, V> extractor) {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> {
            V value = extractor.apply(data.getValue());
            if (value instanceof String) {
                return (javafx.beans.value.ObservableValue<V>) new SimpleStringProperty((String) value);
            }
            return new SimpleObjectProperty<>(value);
        });
        return column;
    }

    private <T> TableColumn<T, Double> moneyColumn(String title, double width, java.util.function.Function<T, Double> extractor) {
        TableColumn<T, Double> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleObjectProperty<>(extractor.apply(data.getValue())));
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatMoney(item));
            }
        });
        return column;
    }

    private <T> TableColumn<T, String> statusColumn(String title, double width, java.util.function.Function<T, String> extractor) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(extractor.apply(data.getValue())));
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                String color = switch (item.toUpperCase()) {
                    case "VERIFIED", "DISPENSED", "COMPLETED", "ACTIVE" -> AppUIManager.COLOR_SUCCESS;
                    case "PENDING", "PARTIAL" -> AppUIManager.COLOR_WARNING;
                    case "CANCELLED", "EXPIRED", "VOIDED", "INACTIVE" -> AppUIManager.COLOR_DANGER;
                    default -> AppUIManager.COLOR_TEXT_MEDIUM;
                };
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });
        return column;
    }

    private String joinList(List<String> values) {
        return values == null || values.isEmpty() ? "—" : String.join(", ", values);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "—";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "—";
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "—" : DATE_FMT.format(date);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "—" : DATE_TIME_FMT.format(dateTime);
    }

    private String formatMoney(double amount) {
        return String.format("PKR %,.2f", amount);
    }

    private static class PrescriptionHistoryRow {
        private String prescriptionNumber;
        private String issuedDate;
        private String doctorName;
        private String clinicName;
        private String status;
        private String diagnosis;
        private String medicines;
        private LocalDateTime sortDate;

        public String getPrescriptionNumber() { return prescriptionNumber; }
        public void setPrescriptionNumber(String prescriptionNumber) { this.prescriptionNumber = prescriptionNumber; }
        public String getIssuedDate() { return issuedDate; }
        public void setIssuedDate(String issuedDate) { this.issuedDate = issuedDate; }
        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
        public String getClinicName() { return clinicName; }
        public void setClinicName(String clinicName) { this.clinicName = clinicName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDiagnosis() { return diagnosis; }
        public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
        public String getMedicines() { return medicines; }
        public void setMedicines(String medicines) { this.medicines = medicines; }
        public LocalDateTime getSortDate() { return sortDate; }
        public void setSortDate(LocalDateTime sortDate) { this.sortDate = sortDate; }
    }

    private static class SaleHistoryRow {
        private String invoiceNumber;
        private String saleDate;
        private String paymentMethod;
        private String status;
        private double grandTotal;
        private String prescriptionReference;
        private String items;
        private LocalDateTime sortDate;

        public String getInvoiceNumber() { return invoiceNumber; }
        public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
        public String getSaleDate() { return saleDate; }
        public void setSaleDate(String saleDate) { this.saleDate = saleDate; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public double getGrandTotal() { return grandTotal; }
        public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }
        public String getPrescriptionReference() { return prescriptionReference; }
        public void setPrescriptionReference(String prescriptionReference) { this.prescriptionReference = prescriptionReference; }
        public String getItems() { return items; }
        public void setItems(String items) { this.items = items; }
        public LocalDateTime getSortDate() { return sortDate; }
        public void setSortDate(LocalDateTime sortDate) { this.sortDate = sortDate; }
    }
}

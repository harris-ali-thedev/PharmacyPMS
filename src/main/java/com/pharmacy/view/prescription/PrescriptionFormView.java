package com.pharmacy.view.prescription;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.model.Patient;
import com.pharmacy.model.Prescription;
import com.pharmacy.model.User;
import com.pharmacy.service.PrescriptionService;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

/**
 * PrescriptionFormView — Dialog for entering a new prescription.
 */
public class PrescriptionFormView {

    private final PatientDAO patientDAO = new PatientDAO();
    private final PrescriptionService prescriptionService = new PrescriptionService();

    private ComboBox<Patient> patientCombo;
    private TextField doctorField, diagnosisField, rxNumberField, validDaysField, doctorRegField, clinicField;
    private TextArea notesArea;
    private DatePicker prescribedDatePicker;
    private Stage dialog;
    private final VBox itemRows = new VBox(10);
    private List<com.pharmacy.model.Medicine> medicines = List.of();
    private final java.util.Map<GridPane, java.util.function.Supplier<Prescription.PrescriptionItem>> itemReaders = new java.util.LinkedHashMap<>();

    public void showDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("New Prescription");
        dialog.setWidth(950);
        dialog.setHeight(640);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        HBox header = new HBox();
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        header.getChildren().add(AppUIManager.sectionTitle("💊  New Prescription"));
        root.getChildren().add(header);

        ScrollPane scroll = new ScrollPane(buildForm());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        HBox footer = new HBox(12);
        footer.setPadding(new Insets(14, 24, 14, 24));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:white; -fx-border-color:" + AppUIManager.COLOR_BORDER + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        Button cancelBtn = AppUIManager.outlineButton("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());
        Button saveBtn = AppUIManager.primaryButton("💾  Save Prescription");
        saveBtn.setOnAction(e -> handleSave());
        footer.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().add(footer);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    private VBox buildForm() {
        VBox form = new VBox(16);
        form.setPadding(new Insets(20));

        VBox card = AppUIManager.card();
        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50); c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);

        patientCombo = new ComboBox<>();
        patientCombo.setMaxWidth(Double.MAX_VALUE);
        patientCombo.setStyle("-fx-font-size:13px;");
        try {
            List<Patient> patients = patientDAO.getAll();
            patientCombo.getItems().setAll(patients);
            patientCombo.setConverter(new javafx.util.StringConverter<>() {
                public String toString(Patient p) { return p == null ? "" : p.getFirstName() + " " + p.getLastName() + " \t\t[" + p.getPhone() + "]"; }
                public Patient fromString(String s) { return null; }
            });
        } catch (Exception ex) { /* silent */ }

        rxNumberField = AppUIManager.styledField("e.g. RX-2024-001");
        doctorField = AppUIManager.styledField("Doctor's full name");
        diagnosisField = AppUIManager.styledField("e.g. Hypertension");
        prescribedDatePicker = AppUIManager.styledDatePicker();
        prescribedDatePicker.setValue(LocalDate.now());
        validDaysField = AppUIManager.styledField("30");
        doctorRegField = AppUIManager.styledField("Medical council reg. no.");
        clinicField = AppUIManager.styledField("Clinic / hospital");
        notesArea = AppUIManager.styledTextArea("Special instructions, notes…", 3);

        row(grid, 0, "Patient *",        patientCombo,         "Rx Number",        rxNumberField);
        row(grid, 1, "Prescribing Doctor *", doctorField,      "Prescribed Date",  prescribedDatePicker);
        row(grid, 2, "Diagnosis",        diagnosisField,       "Valid for (days)", validDaysField);
        row(grid, 3, "Doctor Reg. No.",  doctorRegField,       "Clinic",           clinicField);

        card.getChildren().add(grid);

        VBox notesCard = AppUIManager.card();
        notesCard.getChildren().addAll(AppUIManager.fieldLabel("Notes / Instructions"), notesArea);

        Button addItem = AppUIManager.outlineButton("Add medicine");
        addItem.setDisable(true);
        com.pharmacy.util.UiTasks.run(() -> new com.pharmacy.dao.MedicineDAO().getAll(), loaded -> {
            medicines = loaded;
            addItem.setDisable(false);
            addItemRow();
        }, error -> MessageDialog.showError("Medicines unavailable", error.getMessage()));
        addItem.setOnAction(e -> addItemRow());
        form.getChildren().addAll(card, new Label("Prescription medicines and dosage instructions *"), itemRows, addItem, notesCard);
        return form;
    }

    private void addItemRow() {
        GridPane row = new GridPane();
        row.setHgap(10); row.setVgap(6);
        ComboBox<com.pharmacy.model.Medicine> medicine = new ComboBox<>();
        medicine.getItems().setAll(medicines);
        medicine.setPromptText("Select medicine");
        TextField qty = new TextField("1"), dosage = new TextField(), frequency = new TextField(), duration = new TextField(), instructions = new TextField();
        Button remove = AppUIManager.outlineButton("Remove");
        remove.setOnAction(e -> { itemReaders.remove(row); itemRows.getChildren().remove(row); });
        row.addRow(0, new Label("Medicine"), new Label("Quantity"), new Label("Dosage"), new Label("Frequency"));
        row.addRow(1, medicine, qty, dosage, frequency);
        row.addRow(2, new Label("Duration"), duration, new Label("Instructions"), instructions, remove);
        itemReaders.put(row, () -> {
            var med = medicine.getValue();
            if (med == null) throw new IllegalArgumentException("Select a medicine for every line.");
            int quantity;
            try { quantity = Integer.parseInt(qty.getText().trim()); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Medicine quantity must be a positive whole number."); }
            if (quantity <= 0 || dosage.getText().isBlank() || frequency.getText().isBlank() || duration.getText().isBlank())
                throw new IllegalArgumentException("Every medicine needs a positive quantity, dosage, frequency and duration.");
            var item = new Prescription.PrescriptionItem();
            item.setMedId(med.getMedId()); item.setMedicineName(med.getName()); item.setQtyPrescribed(quantity);
            item.setDosage(dosage.getText().trim()); item.setFrequency(frequency.getText().trim());
            item.setDuration(duration.getText().trim()); item.setInstructions(instructions.getText().trim());
            item.setScheduled(med.isScheduled()); item.setNarcotic(med.isNarcotics());
            return item;
        });
        itemRows.getChildren().add(row);
    }

    private void row(GridPane g, int r, String l1, javafx.scene.Node f1, String l2, javafx.scene.Node f2) {
        g.add(new VBox(5, AppUIManager.fieldLabel(l1), f1), 0, r);
        g.add(new VBox(5, AppUIManager.fieldLabel(l2), f2), 1, r);
    }

    private void handleSave() {
        if (patientCombo.getValue() == null) { MessageDialog.showWarning("Required", "Please select a patient."); return; }
        if (doctorField.getText().isBlank()) { MessageDialog.showWarning("Required", "Doctor name is required."); return; }
        try {
            Patient patient = patientCombo.getValue();
            Prescription rx = new Prescription();
            rx.setItems(itemReaders.values().stream().map(java.util.function.Supplier::get).toList());
            rx.setPatientId(patient.getPatientId());
            rx.setPatientName(patient.getFirstName() + " " + patient.getLastName());
            rx.setPatientNic(patient.getNicNumber());
            rx.setPrescriptionNumber(rxNumberField.getText().trim());
            rx.setDoctorName(doctorField.getText().trim());
            rx.setDoctorRegNo(doctorRegField.getText().trim());
            rx.setClinicName(clinicField.getText().trim());
            rx.setDiagnosis(diagnosisField.getText().trim());
            rx.setIssuedDate(prescribedDatePicker.getValue());
            rx.setValidityDays(validDaysField.getText().isBlank() ? 30 : Integer.parseInt(validDaysField.getText().trim()));
            rx.setNotes(notesArea.getText().trim());
            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null) rx.setCreatedBy(user.getUserId());
            prescriptionService.createPrescription(rx);
            MessageDialog.showSuccess("Saved", "Prescription saved successfully.");
            dialog.close();
        } catch (NumberFormatException ex) {
            MessageDialog.showWarning("Invalid", "Validity must be a whole number of days.");
        } catch (Exception ex) {
            MessageDialog.showError("Save Failed", ex.getMessage());
        }
    }
}

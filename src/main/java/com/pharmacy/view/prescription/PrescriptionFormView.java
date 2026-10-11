package com.pharmacy.view.prescription;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.dao.PrescriptionDAO;
import com.pharmacy.model.Patient;
import com.pharmacy.model.Prescription;
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
    private final PrescriptionDAO rxDAO = new PrescriptionDAO();

    private ComboBox<Patient> patientCombo;
    private TextField doctorField, diagnosisField, rxNumberField, validDaysField;
    private TextArea notesArea;
    private DatePicker prescribedDatePicker;
    private Stage dialog;

    public void showDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("New Prescription");
        dialog.setWidth(620);
        dialog.setHeight(580);

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
        notesArea = AppUIManager.styledTextArea("Special instructions, notes…", 3);

        row(grid, 0, "Patient *",        patientCombo,         "Rx Number",        rxNumberField);
        row(grid, 1, "Prescribing Doctor *", doctorField,      "Prescribed Date",  prescribedDatePicker);
        row(grid, 2, "Diagnosis",        diagnosisField,       "Valid for (days)", validDaysField);

        card.getChildren().add(grid);

        VBox notesCard = AppUIManager.card();
        notesCard.getChildren().addAll(AppUIManager.fieldLabel("Notes / Instructions"), notesArea);

        form.getChildren().addAll(card, notesCard);
        return form;
    }

    private void row(GridPane g, int r, String l1, javafx.scene.Node f1, String l2, javafx.scene.Node f2) {
        g.add(new VBox(5, AppUIManager.fieldLabel(l1), f1), 0, r);
        g.add(new VBox(5, AppUIManager.fieldLabel(l2), f2), 1, r);
    }

    private void handleSave() {
        if (patientCombo.getValue() == null) { MessageDialog.showWarning("Required", "Please select a patient."); return; }
        if (doctorField.getText().isBlank()) { MessageDialog.showWarning("Required", "Doctor name is required."); return; }
        try {
            Prescription rx = new Prescription();
            rx.setPatientId(patientCombo.getValue().getPatientId());
            rx.setPatientName(patientCombo.getValue().getFirstName() + " " + patientCombo.getValue().getLastName());
            rx.setDoctorName(doctorField.getText().trim());
            rx.setDiagnosis(diagnosisField.getText().trim());
//            rx.setPrescribedDate(prescribedDatePicker.getValue());
            int days = validDaysField.getText().isBlank() ? 30 : Integer.parseInt(validDaysField.getText().trim());
//            rx.setValidUntil(prescribedDatePicker.getValue().plusDays(days));
            rx.setStatus("PENDING");
            rx.setNotes(notesArea.getText().trim());
//            rxDAO.save(rx);
            MessageDialog.showSuccess("Saved", "Prescription saved successfully.");
            dialog.close();
        } catch (Exception ex) {
            MessageDialog.showError("Save Failed", ex.getMessage());
        }
    }
}
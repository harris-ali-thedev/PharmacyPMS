package com.pharmacy.view.patient;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.model.Patient;
import com.pharmacy.model.enums.BloodGroup;
import com.pharmacy.model.enums.Gender;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * PatientFormDialog — Add / Edit patient form.
 *
 * Fields: Name, DOB, Gender, Blood Group, Phone, Email,
 *         NIC, Address, Allergies, Medical Conditions, Notes.
 * Inline validation with red-border feedback.
 */
public class PatientFormDialog {

    private final PatientDAO patientDAO = new PatientDAO();
    private final Patient existing;      // null = new patient
    private Stage dialog;

    // Form fields
    private TextField firstNameField, lastNameField, phoneField, emailField, nicField;
    private DatePicker dobPicker;
    private ComboBox<String> genderCombo, bloodGroupCombo;
    private TextArea addressArea, allergyArea, conditionArea, notesArea;
    private Label statusLabel;

    public PatientFormDialog(Patient existing) {
        this.existing = existing;
    }

    public void showAndWait() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? "New Patient" : "Edit Patient — " + existing.getFirstName());
        dialog.setWidth(680);
        dialog.setHeight(700);
        dialog.setResizable(true);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");

        // ── Dialog Header ─────────────────────────────────────
        HBox header = new HBox(12);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent " +
                AppUIManager.COLOR_BORDER + " transparent; -fx-border-width: 0 0 1 0;");

        Label title = AppUIManager.sectionTitle(existing == null ? "➕  Register New Patient" : "✏  Edit Patient");
        Label sub = new Label(existing == null ? "Fill in the patient details below." :
                "Patient Code: " + (existing.getPatientCode() == null ? "—" : existing.getPatientCode()));
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");

        VBox titleBox = new VBox(4, title, sub);
        header.getChildren().add(titleBox);
        root.getChildren().add(header);

        // ── Scrollable form ───────────────────────────────────
        ScrollPane scroll = new ScrollPane(buildForm());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // ── Footer buttons ────────────────────────────────────
        HBox footer = new HBox(12);
        footer.setPadding(new Insets(14, 24, 14, 24));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color: white; -fx-border-color: " +
                AppUIManager.COLOR_BORDER + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_DANGER + ";");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cancelBtn = AppUIManager.outlineButton("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = AppUIManager.primaryButton(existing == null ? "💾  Save Patient" : "💾  Update Patient");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> handleSave());

        footer.getChildren().addAll(statusLabel, spacer, cancelBtn, saveBtn);
        root.getChildren().add(footer);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // ── Form Builder ──────────────────────────────────────────
    private VBox buildForm() {
        VBox form = new VBox(20);
        form.setPadding(new Insets(24));

        // Personal info card
        VBox personalCard = AppUIManager.card();
        personalCard.getChildren().add(AppUIManager.sectionTitle("Personal Information"));

        GridPane grid1 = new GridPane();
        grid1.setHgap(16); grid1.setVgap(12);
        colConst(grid1, 50, 50);

        firstNameField = AppUIManager.styledField("First name");
        lastNameField  = AppUIManager.styledField("Last name");
        dobPicker      = AppUIManager.styledDatePicker();
        genderCombo    = AppUIManager.styledComboBox("MALE", "FEMALE", "OTHER");
        bloodGroupCombo = AppUIManager.styledComboBox("A_POS","A_NEG","B_POS","B_NEG","AB_POS","AB_NEG","O_POS","O_NEG","UNKNOWN");
        phoneField     = AppUIManager.styledField("e.g. 0300-1234567");
        emailField     = AppUIManager.styledField("email@example.com");
        nicField       = AppUIManager.styledField("e.g. 12345-1234567-1");

        addRow(grid1, 0, "First Name *",  firstNameField, "Last Name *", lastNameField);
        addRow(grid1, 1, "Date of Birth", dobPicker,      "Gender",      genderCombo);
        addRow(grid1, 2, "Blood Group",   bloodGroupCombo,"Phone *",     phoneField);
        addRow(grid1, 3, "Email",         emailField,     "NIC Number",  nicField);

        personalCard.getChildren().add(grid1);

        // Address card
        VBox addrCard = AppUIManager.card();
        addrCard.getChildren().add(AppUIManager.sectionTitle("Address"));
        addressArea = AppUIManager.styledTextArea("Full address…", 3);
        addrCard.getChildren().add(addressArea);

        // Medical info card
        VBox medCard = AppUIManager.card();
        medCard.getChildren().add(AppUIManager.sectionTitle("Medical Information"));

        GridPane grid2 = new GridPane();
        grid2.setHgap(16); grid2.setVgap(12);
        colConst(grid2, 50, 50);

        allergyArea   = AppUIManager.styledTextArea("Known allergies (one per line)…", 3);
        conditionArea = AppUIManager.styledTextArea("Chronic conditions, diagnoses…", 3);

        grid2.add(vField("Known Allergies",    allergyArea),   0, 0);
        grid2.add(vField("Medical Conditions", conditionArea), 1, 0);

        notesArea = AppUIManager.styledTextArea("Additional notes, special instructions…", 3);
        medCard.getChildren().addAll(grid2, vField("Notes", notesArea));

        form.getChildren().addAll(personalCard, addrCard, medCard);

        // Populate if editing
        if (existing != null) populate(existing);

        return form;
    }

    private void populate(Patient p) {
        firstNameField.setText(nullSafe(p.getFirstName()));
        lastNameField.setText(nullSafe(p.getLastName()));
        phoneField.setText(nullSafe(p.getPhone()));
        emailField.setText(nullSafe(p.getEmail()));
        nicField.setText(nullSafe(p.getNicNumber()));
        if (p.getDateOfBirth() != null) dobPicker.setValue(p.getDateOfBirth());
        if (p.getGender() != null) genderCombo.setValue(p.getGender().toString());
        if (p.getBloodGroup() != null) bloodGroupCombo.setValue(p.getBloodGroup().toString());
        if (p.getAddress() != null) addressArea.setText(p.getAddress());
        if (p.getAllergies() != null) allergyArea.setText(String.join("\n", p.getAllergies()));
//        if (p.getNotes() != null) notesArea.setText(p.getNotes());
    }

    // ── Save / Validate ───────────────────────────────────────
    private void handleSave() {
        if (!validate()) return;

        try {
            Patient p = (existing != null) ? existing : new Patient();

            p.setFirstName(firstNameField.getText().trim());
            p.setLastName(lastNameField.getText().trim());
            p.setPhone(phoneField.getText().trim());
            p.setEmail(emailField.getText().trim().isEmpty() ? null : emailField.getText().trim());
            p.setNicNumber(nicField.getText().trim().isEmpty() ? null : nicField.getText().trim());
            p.setDateOfBirth(dobPicker.getValue());
            if (genderCombo.getValue() != null) p.setGender(Gender.valueOf(genderCombo.getValue()));
            if (bloodGroupCombo.getValue() != null) p.setBloodGroup(BloodGroup.valueOf(bloodGroupCombo.getValue()));
            p.setAddress(addressArea.getText().trim().isEmpty() ? null : addressArea.getText().trim());
//            p.setNotes(notesArea.getText().trim().isEmpty() ? null : notesArea.getText().trim());

            // Allergies from textarea
            if (!allergyArea.getText().isBlank()) {
                java.util.List<String> allergies = java.util.Arrays.asList(
                        allergyArea.getText().split("\\n"));
                p.setAllergies(new java.util.ArrayList<>(allergies));
            }

            if (existing == null) {
                patientDAO.insert(p);
                MessageDialog.showSuccess("Patient Saved", "Patient registered successfully.");
            } else {
                patientDAO.update(p);
                MessageDialog.showSuccess("Patient Updated", "Patient details updated successfully.");
            }
            dialog.close();

        } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
            statusLabel.setVisible(true);
        }
    }

    private boolean validate() {
        boolean ok = true;
        if (firstNameField.getText().trim().isEmpty()) { markError(firstNameField); ok = false; }
        else markOk(firstNameField);
        if (lastNameField.getText().trim().isEmpty()) { markError(lastNameField); ok = false; }
        else markOk(lastNameField);
        if (phoneField.getText().trim().isEmpty()) { markError(phoneField); ok = false; }
        else markOk(phoneField);
        if (!ok) statusLabel.setText("Please fill in all required fields (marked *).");
        return ok;
    }

    private void markError(TextField f) {
        f.setStyle(f.getStyle().replace("#CBD5E1","#DC2626").replace("border-width:1","border-width:2"));
    }
    private void markOk(TextField f) {
        f.setStyle(AppUIManager.styledField("").getStyle());
    }

    // ── Layout helpers ────────────────────────────────────────
    private void addRow(GridPane g, int row, String lbl1, javafx.scene.Node f1, String lbl2, javafx.scene.Node f2) {
        g.add(vField(lbl1, f1), 0, row);
        g.add(vField(lbl2, f2), 1, row);
    }

    private VBox vField(String label, javafx.scene.Node field) {
        VBox box = new VBox(5);
        box.getChildren().addAll(AppUIManager.fieldLabel(label), field);
        return box;
    }

    private void colConst(GridPane g, double pct1, double pct2) {
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(pct1); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(pct2); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
package com.pharmacy.view.reports;

import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.NarcoticRegisterDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.NarcoticRegisterEntry;
import com.pharmacy.util.SessionManager;
import com.pharmacy.util.UiTasks;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public class NarcoticRegisterView {
    private final TableView<NarcoticRegisterEntry> table = new TableView<>();
    private final ComboBox<Medicine> medicine = new ComboBox<>();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1)), to = new DatePicker(LocalDate.now());
    private final List<String> headers = List.of("Entry", "Date", "Medicine", "Batch", "Type", "In", "Out", "Balance", "Prescription", "Patient", "CNIC", "Doctor", "Doctor registration", "Sale", "Witness", "Witness designation", "Pharmacist", "Notes");
    private final List<Function<NarcoticRegisterEntry, Object>> fields = List.of(
            NarcoticRegisterEntry::getEntryId, NarcoticRegisterEntry::getEntryDate, NarcoticRegisterEntry::getMedicineName,
            NarcoticRegisterEntry::getBatchNumber, NarcoticRegisterEntry::getTransactionType, NarcoticRegisterEntry::getQtyIn,
            NarcoticRegisterEntry::getQtyOut, NarcoticRegisterEntry::getRunningBalance, NarcoticRegisterEntry::getPrescriptionId,
            NarcoticRegisterEntry::getPatientName, NarcoticRegisterEntry::getPatientNic, NarcoticRegisterEntry::getDoctorName,
            NarcoticRegisterEntry::getDoctorRegNo, NarcoticRegisterEntry::getSaleId, NarcoticRegisterEntry::getWitnessName,
            NarcoticRegisterEntry::getWitnessDesignation, NarcoticRegisterEntry::getDispensedByName, NarcoticRegisterEntry::getNotes);

    public Node getView() {
        var session = SessionManager.getInstance();
        if (!session.isAdmin() && !session.isPharmacist()) return new Label("Access denied. Pharmacists and administrators only.");
        Button load = AppUIManager.primaryButton("Load Register"), export = AppUIManager.outlineButton("Export CSV");
        load.setOnAction(e -> load()); export.setOnAction(e -> export());
        for (int i=0; i<headers.size(); i++) {
            var field = fields.get(i);
            TableColumn<NarcoticRegisterEntry,String> column = new TableColumn<>(headers.get(i));
            column.setCellValueFactory(c -> new SimpleStringProperty(java.util.Objects.toString(field.apply(c.getValue()), "")));
            column.setPrefWidth(150); table.getColumns().add(column);
        }
        VBox page = new VBox(16, AppUIManager.pageTitle("Narcotic Register"),
                new HBox(10, medicine, new Label("From"), from, new Label("To"), to, load, export), table);
        page.setPadding(new Insets(24)); VBox.setVgrow(table, Priority.ALWAYS);
        UiTasks.run(() -> new MedicineDAO().getAll().stream().filter(Medicine::isNarcotics).toList(),
                meds -> medicine.getItems().setAll(meds), error -> MessageDialog.showError("Load failed",error.getMessage()));
        return page;
    }
    private void load() {
        if (medicine.getValue()==null || from.getValue()==null || to.getValue()==null || from.getValue().isAfter(to.getValue())) {
            MessageDialog.showWarning("Selection required","Select a medicine and a valid date range."); return;
        }
        int id=medicine.getValue().getMedId(); var start=from.getValue(); var end=to.getValue();
        UiTasks.run(() -> new NarcoticRegisterDAO().getRegisterByDateRange(id,start,end),
                rows -> table.getItems().setAll(rows), error -> { table.getItems().clear(); MessageDialog.showError("Load failed",error.getMessage()); });
    }
    private void export() {
        if (table.getItems().isEmpty()) { MessageDialog.showWarning("Export","Load register entries first."); return; }
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("narcotic-register.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV","*.csv"));
        var file = chooser.showSaveDialog(table.getScene().getWindow()); if(file==null)return;
        var rows=List.copyOf(table.getItems());
        UiTasks.run(() -> {
            try(var writer=Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                writer.write(headers.stream().map(this::csv).collect(java.util.stream.Collectors.joining(","))); writer.newLine();
                for(var row:rows) { writer.write(fields.stream().map(f -> csv(java.util.Objects.toString(f.apply(row),""))).collect(java.util.stream.Collectors.joining(","))); writer.newLine(); }
            }
            return file;
        }, saved -> MessageDialog.showSuccess("Exported",saved.toString()), error -> MessageDialog.showError("Export failed",error.getMessage()));
    }
    private String csv(String value) {
        if (value.matches("^[=+@\\-].*")) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}

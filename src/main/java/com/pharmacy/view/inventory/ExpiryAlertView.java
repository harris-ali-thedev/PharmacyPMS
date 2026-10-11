package com.pharmacy.view.inventory;

import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.User;
import com.pharmacy.service.InventoryService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.SessionManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExpiryAlertView {
    private final InventoryService inventoryService = new InventoryService();
    private final MedicineDAO medicineDAO = new MedicineDAO();

    private final TableView<ExpiryAlertRow> table = new TableView<>();
    private final ComboBox<String> urgencyFilter = new ComboBox<>();
    private final Label statusLabel = new Label("Loading expiry alerts...");
    private final Label totalAlertsLabel = valueLabel("0");
    private final Label expiredLabel = valueLabel("0");
    private final Label criticalLabel = valueLabel("0");
    private final Label atRiskValueLabel = valueLabel("Rs 0.00");

    private List<ExpiryAlertRow> allRows = new ArrayList<>();

    public Node getView(){
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        Label title = new Label("Expiry Alerts");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Monitor expiring and expired inventory batches within the configured "
                + AppConstants.EXPIRY_ALERT_DAYS + "-day alert window.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        content.getChildren().addAll(
                title,
                subtitle,
                buildSummaryCard(),
                buildTableCard(),
                statusLabel
        );

        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        pane.setCenter(content);

        loadAlerts();
        return pane;
    }

    private VBox buildSummaryCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Summary"));

        HBox stats = new HBox(12,
                statBox("Total Alerts", totalAlertsLabel),
                statBox("Expired", expiredLabel),
                statBox("Critical", criticalLabel),
                statBox("At-Risk Value", atRiskValueLabel)
        );
        card.getChildren().add(stats);
        return card;
    }

    private VBox buildTableCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Batch Expiry Details"));

        urgencyFilter.getItems().setAll("All", "Expired", "Critical", "Warning", "Info");
        urgencyFilter.setValue("All");
        urgencyFilter.setOnAction(e -> applyFilter());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadAlerts());

        Button quarantineBtn = new Button("Quarantine Expired");
        quarantineBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        quarantineBtn.setOnAction(e -> quarantineSelectedExpired());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, fieldLabel("Filter"), urgencyFilter, spacer, refreshBtn, quarantineBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        card.getChildren().addAll(toolbar, table);
        return card;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("No expiring batches found."));

        TableColumn<ExpiryAlertRow, String> medicineCol = new TableColumn<>("Medicine");
        medicineCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMedicineName()));
        medicineCol.setPrefWidth(200);

        TableColumn<ExpiryAlertRow, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        categoryCol.setPrefWidth(120);

        TableColumn<ExpiryAlertRow, String> batchCol = new TableColumn<>("Batch");
        batchCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBatchNumber()));
        batchCol.setPrefWidth(120);

        TableColumn<ExpiryAlertRow, LocalDate> expiryCol = new TableColumn<>("Expiry Date");
        expiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getExpiryDate()));
        expiryCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        expiryCol.setPrefWidth(110);

        TableColumn<ExpiryAlertRow, Number> daysCol = new TableColumn<>("Days Left");
        daysCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getDaysUntilExpiry()));
        daysCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                int days = item.intValue();
                setText(days < 0 ? Math.abs(days) + " day(s) overdue" : days + " day(s)");
                ExpiryAlertRow row = getTableView().getItems().get(getIndex());
                setStyle("-fx-text-fill: " + urgencyColor(row.getUrgency()) + "; -fx-font-weight: bold;");
            }
        });
        daysCol.setPrefWidth(120);

        TableColumn<ExpiryAlertRow, Number> qtyCol = new TableColumn<>("Qty Remaining");
        qtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQtyRemaining()));
        qtyCol.setPrefWidth(110);

        TableColumn<ExpiryAlertRow, String> urgencyCol = new TableColumn<>("Urgency");
        urgencyCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUrgency()));
        urgencyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setStyle("-fx-text-fill: " + urgencyColor(item) + "; -fx-font-weight: bold;");
            }
        });
        urgencyCol.setPrefWidth(90);

        TableColumn<ExpiryAlertRow, Number> unitCostCol = new TableColumn<>("Unit Cost");
        unitCostCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getUnitCost()));
        unitCostCol.setCellFactory(moneyCellFactory());
        unitCostCol.setPrefWidth(120);

        TableColumn<ExpiryAlertRow, Number> packCostCol = new TableColumn<>("Pack Cost");
        packCostCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPackCost()));
        packCostCol.setCellFactory(moneyCellFactory());
        packCostCol.setPrefWidth(120);

        TableColumn<ExpiryAlertRow, Number> riskCol = new TableColumn<>("At-Risk Value");
        riskCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getAtRiskValue()));
        riskCol.setCellFactory(moneyCellFactory());
        riskCol.setPrefWidth(120);

        table.getColumns().setAll(medicineCol, categoryCol, batchCol, expiryCol, daysCol, qtyCol, urgencyCol, unitCostCol, packCostCol, riskCol);
    }

    private void loadAlerts() {
        try {
            Map<Integer, Medicine> medicineById = loadMedicineMap();
            List<InventoryService.ExpiryAlert> alerts = inventoryService.getExpiryAlerts();
            List<ExpiryAlertRow> rows = new ArrayList<>();

            for (InventoryService.ExpiryAlert alert : alerts) {
                Medicine medicine = medicineById.get(alert.batch().getMedId());
                rows.add(new ExpiryAlertRow(
                        alert.batch().getBatchId(),
                        alert.batch().getMedId(),
                        medicine != null ? medicine.getName() : "Medicine #" + alert.batch().getMedId(),
                        medicine != null && medicine.getCategory() != null && !medicine.getCategory().isBlank()
                                ? medicine.getCategory()
                                : "-",
                        alert.batch().getBatchNumber(),
                        alert.batch().getExpiryDate(),
                        alert.daysUntilExpiry(),
                        alert.batch().getQtyRemaining(),
                        alert.batch().getUnitCost(),
                        alert.batch().getPackCost(),
                        alert.urgency().name(),
                        "EXPIRED".equals(alert.urgency().name()) ? 0 : alert.batch().getQtyRemaining() * alert.batch().getUnitCost()
                ));
            }

            allRows = rows;
            updateSummary(rows);
            applyFilter();
            statusLabel.setText(rows.size() + " expiry alert(s) loaded.");
        } catch (Exception e) {
            table.getItems().clear();
            totalAlertsLabel.setText("0");
            expiredLabel.setText("0");
            criticalLabel.setText("0");
            atRiskValueLabel.setText("Rs 0.00");
            statusLabel.setText("Failed to load expiry alerts");
            AlertUtil.showError("Load Error", e.getMessage());
        }
    }

    private Map<Integer, Medicine> loadMedicineMap() throws Exception {
        Map<Integer, Medicine> medicineById = new HashMap<>();
        for (Medicine medicine : medicineDAO.getAll()) {
            medicineById.put(medicine.getMedId(), medicine);
        }
        return medicineById;
    }

    private void applyFilter() {
        String filter = urgencyFilter.getValue();
        List<ExpiryAlertRow> filtered = new ArrayList<>();
        for (ExpiryAlertRow row : allRows) {
            if (filter == null || "All".equalsIgnoreCase(filter) || row.getUrgency().equalsIgnoreCase(filter)) {
                filtered.add(row);
            }
        }
        table.getItems().setAll(filtered);
        statusLabel.setText(filtered.size() + " alert(s) shown"
                + ((filter == null || "All".equalsIgnoreCase(filter)) ? "" : " for " + filter.toLowerCase() + " urgency") + ".");
    }

    private void updateSummary(List<ExpiryAlertRow> rows) {
        int expired = 0;
        int critical = 0;
        double risk = 0;

        for (ExpiryAlertRow row : rows) {
            if ("EXPIRED".equals(row.getUrgency())) expired++;
            if ("CRITICAL".equals(row.getUrgency())) critical++;
            risk += row.getAtRiskValue();
        }

        totalAlertsLabel.setText(String.valueOf(rows.size()));
        expiredLabel.setText(String.valueOf(expired));
        criticalLabel.setText(String.valueOf(critical));
        atRiskValueLabel.setText(String.format("Rs %,.2f", risk));
    }

    private void quarantineSelectedExpired() {
        List<ExpiryAlertRow> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            AlertUtil.showError("Selection Required", "Select one or more expired batches first.");
            return;
        }

        List<ExpiryAlertRow> expiredRows = new ArrayList<>();
        for (ExpiryAlertRow row : selected) {
            if ("EXPIRED".equalsIgnoreCase(row.getUrgency())) {
                expiredRows.add(row);
            }
        }

        if (expiredRows.isEmpty()) {
            AlertUtil.showError("Invalid Selection", "Only expired batches can be quarantined from this screen.");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            AlertUtil.showError("Session Error", "No active user session found.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Expired stock removed from dispensing");
        dialog.setTitle("Quarantine Expired Batch");
        dialog.setHeaderText("Reason for quarantine");
        dialog.setContentText("Reason:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String reason = result.get().trim();
        if (reason.isEmpty()) {
            AlertUtil.showError("Validation", "Quarantine reason is required.");
            return;
        }

        if (!AlertUtil.showConfirm("Confirm Quarantine",
                "Quarantine " + expiredRows.size() + " expired batch(es)?")) {
            return;
        }

        try {
            for (ExpiryAlertRow row : expiredRows) {
                inventoryService.quarantineBatch(row.getBatchId(), user.getUserId(), reason);
            }
            AlertUtil.showInfo("Quarantine Complete", expiredRows.size() + " batch(es) quarantined successfully.");
            loadAlerts();
        } catch (Exception e) {
            AlertUtil.showError("Quarantine Failed", e.getMessage());
        }
    }

    private javafx.util.Callback<TableColumn<ExpiryAlertRow, Number>, TableCell<ExpiryAlertRow, Number>> moneyCellFactory() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("Rs %,.2f", item.doubleValue()));
            }
        };
    }

    private String urgencyColor(String urgency) {
        return switch (urgency == null ? "" : urgency.toUpperCase()) {
            case "EXPIRED" -> "#991B1B";
            case "CRITICAL" -> "#DC2626";
            case "WARNING" -> "#D97706";
            default -> "#185FA5";
        };
    }

    private VBox statBox(String title, Label valueLabel) {
        VBox box = new VBox(6);
        box.setPrefWidth(200);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: #F9FAFB; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 10;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private VBox createCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 10;");
        return card;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        return label;
    }

    private static Label valueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }

    private static class ExpiryAlertRow {
        private final int batchId;
        private final int medId;
        private final String medicineName;
        private final String category;
        private final String batchNumber;
        private final LocalDate expiryDate;
        private final int daysUntilExpiry;
        private final int qtyRemaining;
        private final double unitCost;
        private final double packCost;
        private final String urgency;
        private final double atRiskValue;

        private ExpiryAlertRow(int batchId, int medId, String medicineName, String category, String batchNumber,
                               LocalDate expiryDate, int daysUntilExpiry, int qtyRemaining, double unitCost, double packCost,
                               String urgency, double atRiskValue) {
            this.batchId = batchId;
            this.medId = medId;
            this.medicineName = medicineName;
            this.category = category;
            this.batchNumber = batchNumber;
            this.expiryDate = expiryDate;
            this.daysUntilExpiry = daysUntilExpiry;
            this.qtyRemaining = qtyRemaining;
            this.unitCost = unitCost;
            this.packCost = packCost;
            this.urgency = urgency;
            this.atRiskValue = atRiskValue;
        }

        public int getBatchId() { return batchId; }
        public int getMedId() { return medId; }
        public String getMedicineName() { return medicineName; }
        public String getCategory() { return category; }
        public String getBatchNumber() { return batchNumber; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public int getDaysUntilExpiry() { return daysUntilExpiry; }
        public int getQtyRemaining() { return qtyRemaining; }
        public double getUnitCost() { return unitCost; }
        public double getPackCost() { return packCost; }
        public String getUrgency() { return urgency; }
        public double getAtRiskValue() { return atRiskValue; }
    }
}

package com.pharmacy.view.inventory;

import com.pharmacy.model.User;
import com.pharmacy.service.InventoryService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;

public class StockReorderView {
    private final InventoryService inventoryService = new InventoryService();

    private final TableView<InventoryService.SuggestedPO> supplierTable = new TableView<>();
    private final TableView<InventoryService.SuggestedPO.LineItem> lineItemTable = new TableView<>();
    private final Label statusLabel = new Label("Loading reorder suggestions...");
    private final Label supplierCountLabel = valueLabel("0");
    private final Label medicineCountLabel = valueLabel("0");
    private final Label totalCostLabel = valueLabel("Rs 0.00");
    private final Label criticalCountLabel = valueLabel("0");

    private List<InventoryService.SuggestedPO> suggestions = new ArrayList<>();

    public Node getView(){
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        Label title = new Label("Stock Reorder");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Generate supplier-wise purchase order suggestions for medicines at or below reorder level.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        content.getChildren().addAll(
                title,
                subtitle,
                buildSummaryCard(),
                buildSupplierCard(),
                buildLineItemCard(),
                statusLabel
        );

        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        pane.setCenter(AppUIManager.scrollWrap(content));

        loadSuggestions();
        return pane;
    }

    private VBox buildSummaryCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Reorder Summary"));

        HBox stats = new HBox(12,
                statBox("Suppliers", supplierCountLabel),
                statBox("Medicines", medicineCountLabel),
                statBox("Estimated Cost", totalCostLabel),
                statBox("Urgent Lines", criticalCountLabel)
        );
        card.getChildren().add(stats);
        return card;
    }

    private VBox buildSupplierCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Suggested Purchase Orders"));

        Button refreshBtn = new Button("Refresh Suggestions");
        refreshBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadSuggestions());

        Button createSelectedBtn = new Button("Create Selected PO");
        createSelectedBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        createSelectedBtn.setOnAction(e -> createSelectedPO());

        Button createAllBtn = new Button("Create All POs");
        createAllBtn.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        createAllBtn.setOnAction(e -> createAllPOs());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, refreshBtn, spacer, createSelectedBtn, createAllBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureSupplierTable();
        VBox.setVgrow(supplierTable, Priority.ALWAYS);


        card.getChildren().addAll(toolbar, supplierTable);

        return card;
    }

    private VBox buildLineItemCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Supplier Line Items"));

        configureLineItemTable();
        VBox.setVgrow(lineItemTable, Priority.ALWAYS);

        card.setMaxWidth(Double.MAX_VALUE);
        card.setFillWidth(true);

        card.getChildren().add(lineItemTable);
        return card;
    }

    private void configureSupplierTable() {
        supplierTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        supplierTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        supplierTable.setPlaceholder(new Label("No reorder suggestions available."));

        TableColumn<InventoryService.SuggestedPO, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSupplier().getName()));
        supplierCol.setPrefWidth(180);

        TableColumn<InventoryService.SuggestedPO, String> contactCol = new TableColumn<>("Contact");
        contactCol.setCellValueFactory(c -> new SimpleStringProperty(blankAsDash(c.getValue().getSupplier().getContactName())));
        contactCol.setPrefWidth(130);

        TableColumn<InventoryService.SuggestedPO, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(c -> new SimpleStringProperty(blankAsDash(c.getValue().getSupplier().getPhone())));
        phoneCol.setPrefWidth(120);

        TableColumn<InventoryService.SuggestedPO, Number> leadCol = new TableColumn<>("Lead Days");
        leadCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getSupplier().getLeadDays()));
        leadCol.setPrefWidth(90);

        TableColumn<InventoryService.SuggestedPO, Number> lineCountCol = new TableColumn<>("Medicines");
        lineCountCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getLines().size()));
        lineCountCol.setPrefWidth(90);

        TableColumn<InventoryService.SuggestedPO, Number> totalCostCol = new TableColumn<>("Estimated Cost");
        totalCostCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getTotalCost()));
        totalCostCol.setCellFactory(moneyCellFactory());
        totalCostCol.setPrefWidth(120);

        supplierTable.getColumns().setAll(supplierCol, contactCol, phoneCol, leadCol, lineCountCol, totalCostCol);
        supplierTable.setRowFactory(tv -> {
            TableRow<InventoryService.SuggestedPO> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    showLineItems(row.getItem());
                }
            });
            return row;
        });
        supplierTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> showLineItems(value));

        supplierTable.setMinHeight(200);
    }

    private void configureLineItemTable() {
        lineItemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        lineItemTable.setPlaceholder(new Label("Select a supplier suggestion to inspect line items."));

        TableColumn<InventoryService.SuggestedPO.LineItem, String> medCol = new TableColumn<>("Medicine");
        medCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMedicineName()));
        medCol.setPrefWidth(220);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> currentStockCol = new TableColumn<>("Current");
        currentStockCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCurrentStock()));
        currentStockCol.setPrefWidth(80);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> reorderLevelCol = new TableColumn<>("Reorder");
        reorderLevelCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getReorderLevel()));
        reorderLevelCol.setPrefWidth(80);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> suggestedQtyCol = new TableColumn<>("Suggested Qty");
        suggestedQtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getSuggestedQty()));
        suggestedQtyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(String.valueOf(item.intValue()));
                InventoryService.SuggestedPO.LineItem line = getTableView().getItems().get(getIndex());
                boolean urgent = line.getCurrentStock() <= 0 || line.getCurrentStock() <= line.getReorderLevel();
                setStyle(urgent ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "-fx-text-fill: #185FA5;");
            }
        });
        suggestedQtyCol.setPrefWidth(110);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> unitCostCol = new TableColumn<>("Unit Cost");
        unitCostCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getUnitCost()));
        unitCostCol.setCellFactory(moneyCellFactory());
        unitCostCol.setPrefWidth(100);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> packCostCol = new TableColumn<>("Pack Cost");
        packCostCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPackCost()));
        packCostCol.setCellFactory(moneyCellFactory());
        packCostCol.setPrefWidth(100);

        TableColumn<InventoryService.SuggestedPO.LineItem, Number> lineTotalCol = new TableColumn<>("Line Total");
        lineTotalCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getLineTotal()));
        lineTotalCol.setCellFactory(moneyCellFactory());
        lineTotalCol.setPrefWidth(120);

        lineItemTable.getColumns().setAll(medCol, currentStockCol, reorderLevelCol, suggestedQtyCol, unitCostCol, packCostCol, lineTotalCol);

        lineItemTable.setMinHeight(200);
    }

    private void loadSuggestions() {
        try {
            User user = requireCurrentUser();
            suggestions = inventoryService.generateSuggestedPOs(user.getUserId());
            supplierTable.getItems().setAll(suggestions);

            if (suggestions.isEmpty()) {
                lineItemTable.getItems().clear();
                statusLabel.setText("No reorder suggestions available.");
            } else {
                supplierTable.getSelectionModel().selectFirst();
                showLineItems(supplierTable.getSelectionModel().getSelectedItem());
                statusLabel.setText(suggestions.size() + " supplier suggestion(s) loaded.");
            }

            updateSummary();
        } catch (Exception e) {
            suggestions = new ArrayList<>();
            supplierTable.getItems().clear();
            lineItemTable.getItems().clear();
            updateSummary();
            statusLabel.setText("Failed to load reorder suggestions");
            AlertUtil.showError("Load Error", e.getMessage());
        }
    }

    private void showLineItems(InventoryService.SuggestedPO suggestion) {
        if (suggestion == null) {
            lineItemTable.getItems().clear();
            return;
        }
        lineItemTable.getItems().setAll(suggestion.getLines());
    }

    private void createSelectedPO() {
        InventoryService.SuggestedPO selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showError("Selection Required", "Select a supplier suggestion first.");
            return;
        }

        if (!AlertUtil.showConfirm("Create Purchase Order",
                "Create a purchase order for supplier '" + selected.getSupplier().getName() + "'?")) {
            return;
        }

        try {
            User user = requireCurrentUser();
            int poId = inventoryService.createPOFromSuggestion(selected, user.getUserId());
            AlertUtil.showInfo("PO Created", "Purchase order #" + poId + " created successfully.");
            loadSuggestions();
        } catch (Exception e) {
            AlertUtil.showError("Create PO Failed", e.getMessage());
        }
    }

    private void createAllPOs() {
        if (suggestions.isEmpty()) {
            AlertUtil.showError("No Suggestions", "There are no suggested purchase orders to create.");
            return;
        }

        if (!AlertUtil.showConfirm("Create All Purchase Orders",
                "Create " + suggestions.size() + " purchase order(s) for all suggested suppliers?")) {
            return;
        }

        try {
            User user = requireCurrentUser();
            int created = 0;
            for (InventoryService.SuggestedPO suggestion : suggestions) {
                inventoryService.createPOFromSuggestion(suggestion, user.getUserId());
                created++;
            }
            AlertUtil.showInfo("POs Created", created + " purchase order(s) created successfully.");
            loadSuggestions();
        } catch (Exception e) {
            AlertUtil.showError("Create All Failed", e.getMessage());
        }
    }

    private void updateSummary() {
        int supplierCount = suggestions.size();
        int medicineCount = 0;
        int criticalCount = 0;
        double totalCost = 0;

        for (InventoryService.SuggestedPO suggestion : suggestions) {
            medicineCount += suggestion.getLines().size();
            totalCost += suggestion.getTotalCost();
            for (InventoryService.SuggestedPO.LineItem line : suggestion.getLines()) {
                if (line.getCurrentStock() <= 0 || line.getCurrentStock() <= line.getReorderLevel()) {
                    criticalCount++;
                }
            }
        }

        supplierCountLabel.setText(String.valueOf(supplierCount));
        medicineCountLabel.setText(String.valueOf(medicineCount));
        totalCostLabel.setText(String.format("Rs %,.2f", totalCost));
        criticalCountLabel.setText(String.valueOf(criticalCount));
    }

    private User requireCurrentUser() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No active user session found.");
        }
        return user;
    }

    private <T> javafx.util.Callback<TableColumn<T, Number>, TableCell<T, Number>> moneyCellFactory() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("Rs %,.2f", item.doubleValue()));
            }
        };
    }

    private String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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

    private static Label valueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }
}

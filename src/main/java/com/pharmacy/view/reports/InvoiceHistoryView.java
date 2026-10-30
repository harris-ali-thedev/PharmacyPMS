package com.pharmacy.view.reports;
import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;
import com.pharmacy.service.InvoiceService;
import com.pharmacy.service.StockService;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.util.SessionManager;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.view.AppUIManager;

import com.pharmacy.view.PharmacyDesignSystem;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;


public class InvoiceHistoryView {
    private final SaleDAO saleDAO = new SaleDAO();
    private final InvoiceService invoiceService = new InvoiceService();
    private final StockService stockService = new StockService();
    private TableView<Sale> table;
    private final ObservableList<Sale> masterData = FXCollections.observableArrayList();

    private TextField searchField;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;

    public ScrollPane getView(){
        VBox root = AppUIManager.pageContainer("Invoice History", " View and manage sales records");
        root.setSpacing(20);
        root.setPadding(new Insets(0, 0, 20, 0));

        VBox content = new VBox(20);
        content.setPadding(new Insets(0, 28, 28, 28));


        HBox filters = createFilterBar();

        createTable();
        VBox tableContainer = AppUIManager.card();
        tableContainer.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(filters, tableContainer);
        root.getChildren().add(content);

        loadData();

        return AppUIManager.scrollWrap(root);
    }

    private HBox createFilterBar(){
        HBox bar = new HBox(15);
        bar.setAlignment(Pos.BOTTOM_LEFT);

        VBox searchBox = new VBox(5);
        searchField = AppUIManager.styledField("Search invoice ID, patient or cashier...");
        searchField.setPrefWidth(250);
        searchField.textProperty().addListener((obs, old, value) -> applyFilters());
        searchBox.getChildren().addAll(AppUIManager.fieldLabel("Search"), searchField);

        VBox startBox = new VBox(5);
        startDatePicker = AppUIManager.styledDatePicker();
        startDatePicker.setPromptText("Start Date");
        startDatePicker.setOnAction(e -> applyFilters());
        startBox.getChildren().addAll(AppUIManager.fieldLabel("From Date"), startDatePicker);

        VBox endBox = new VBox(5);
        endDatePicker = AppUIManager.styledDatePicker();
        endDatePicker.setPromptText("End Date");
        endDatePicker.setOnAction(e -> applyFilters());
        endBox.getChildren().addAll(AppUIManager.fieldLabel("To Date"), endDatePicker);

        Button resetBtn = AppUIManager.outlineButton("Reset");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
            applyFilters();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = AppUIManager.primaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());

        bar.getChildren().addAll(searchBox, startBox, endBox, resetBtn, spacer, refreshBtn);

        return bar;
    }

    private void createTable(){
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setFixedCellSize(34);

        TableColumn<Sale, Number> idCol = new TableColumn<>("Invoice ID");
        idCol.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getSaleId()));
        idCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number invoiceId, boolean empty) {
                super.updateItem(invoiceId, empty);
                setText(empty || invoiceId == null ? null : " " + invoiceId.intValue());
            }
        });
        idCol.setPrefWidth(100);

        TableColumn<Sale, String> dateCol = new TableColumn<>("Date & Time");
        dateCol.setCellValueFactory(cellData -> {
            if(cellData.getValue().getSaleDate() != null)
                return new SimpleStringProperty(cellData.getValue().getSaleDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            return new SimpleStringProperty("");
        });
        dateCol.setPrefWidth(150);

        TableColumn<Sale, String> patientCol = new TableColumn<>("Patient");
        patientCol.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        patientCol.setPrefWidth(120);

        TableColumn<Sale, String> cashierCol = new TableColumn<>("Cashier");
        cashierCol.setCellValueFactory(new PropertyValueFactory<>("cashierName"));
        cashierCol.setPrefWidth(120);

        TableColumn<Sale, String> totalCol = new TableColumn<>("Grand Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("grandTotal"));
        totalCol.setPrefWidth(100);
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT");

        TableColumn<Sale, String> methodCol = new TableColumn<>("Payment");
        methodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        methodCol.setPrefWidth(100);

        TableColumn<Sale, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>(){
            @Override
            protected void updateItem(String item, boolean isEmpty){
                super.updateItem(item, isEmpty);
                if(isEmpty || item == null){
                    setGraphic(null);
                    setText(null);
                }
                else{
                    Label l;
                    if("VOIDED".equalsIgnoreCase(item)){
                         l = AppUIManager.badge(item, AppUIManager.COLOR_DANGER);
                    }
                    else{
                        l = AppUIManager.badge(item, AppUIManager.COLOR_SUCCESS);
                    }
                    setGraphic(l);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Sale, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(230);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = PharmacyDesignSystem.ghostButton("Details");
            private final Button printBtn = new Button("Print");
            private final Button voidBtn = new Button("Void");
            private final HBox container = new HBox(8, viewBtn, printBtn, voidBtn);

            {
                printBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #185FA5; -fx-border-color: #185FA5; "
                        + "-fx-border-radius: 4; -fx-cursor: hand; -fx-font-size: 12px;");
                voidBtn.setStyle("-fx-background-color: #FEF2F2; -fx-text-fill: #DC2626; -fx-border-color: #FECACA; "
                        + "-fx-border-radius: 4; -fx-cursor: hand; -fx-font-size: 12px;");
                container.setAlignment(Pos.CENTER);
                viewBtn.setOnAction(e -> showDetails(getTableView().getItems().get(getIndex())));
                printBtn.setOnAction(e -> handleReprint(getTableView().getItems().get(getIndex())));
                voidBtn.setOnAction(e -> handleVoid(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Sale sale = getTableView().getItems().get(getIndex());
                boolean allowed = canVoid() && !sale.isVoided();
                voidBtn.setVisible(allowed);
                voidBtn.setManaged(allowed);
                setGraphic(container);
            }
        });

        table.getColumns().addAll(idCol, dateCol, patientCol, cashierCol, totalCol, methodCol, statusCol, actionCol);
    }

    private void loadData() {
        try {
            List<Sale> sales = saleDAO.getRecentSales(500);
            masterData.setAll(sales);
            applyFilters();
        } catch (Exception e) {
            AlertUtil.showError("Data Error", "Could not load invoice history: " + e.getMessage());
        }
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        List<Sale> filtered = masterData.stream().filter(s -> {
            boolean matchesSearch = search.isEmpty() ||
                    String.valueOf(s.getSaleId()).contains(search) ||
                    (s.getPatientName() != null && s.getPatientName().toLowerCase().contains(search)) ||
                    (s.getCashierName() != null && s.getCashierName().toLowerCase().contains(search));

            if (s.getSaleDate() == null) return matchesSearch && start == null && end == null;

            LocalDate saleDate = s.getSaleDate().toLocalDate();
            boolean matchesStart = start == null || !saleDate.isBefore(start);
            boolean matchesEnd = end == null || !saleDate.isAfter(end);

            return matchesSearch && matchesStart && matchesEnd;
        }).collect(Collectors.toList());

        table.setItems(FXCollections.observableArrayList(filtered));
    }

    private void showDetails(Sale sale) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice Details - #" + sale.getSaleId());
        dialog.setMinWidth(600);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: white;");

        // Info Header
        GridPane info = new GridPane();
        info.setHgap(30);
        info.setVgap(12);
        info.add(AppUIManager.fieldLabel("Invoice ID:"), 0, 0);
        info.add(new Label("#" + sale.getSaleId()), 1, 0);
        info.add(AppUIManager.fieldLabel("Date/Time:"), 0, 1);
        info.add(new Label(sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))), 1, 1);

        info.add(AppUIManager.fieldLabel("Patient:"), 2, 0);
        info.add(new Label(sale.getPatientName() != null ? sale.getPatientName() : "Walk-in"), 3, 0);
        info.add(AppUIManager.fieldLabel("Payment Method:"), 2, 1);
        info.add(new Label(sale.getPaymentMethod()), 3, 1);

        // Items Table
        TableView<SaleItem> itemsTable = new TableView<>();
        AppUIManager.styleTable(itemsTable);
        itemsTable.setPrefHeight(250);

        TableColumn<SaleItem, String> medCol = new TableColumn<>("Medicine Name");
        medCol.setCellValueFactory(new PropertyValueFactory<>("medicineName"));
        medCol.setPrefWidth(250);

        TableColumn<SaleItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<SaleItem, Double> priceCol = new TableColumn<>("Unit Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));

        TableColumn<SaleItem, Double> subCol = new TableColumn<>("Subtotal");
        subCol.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        itemsTable.getColumns().addAll(medCol, qtyCol, priceCol, subCol);

        try {
            itemsTable.getItems().setAll(saleDAO.getItemsBySale(sale.getSaleId()));
        } catch (Exception e) {
            content.getChildren().add(new Label("Error loading items: " + e.getMessage()));
        }

        // Totals summary
        VBox summary = new VBox(8);
        summary.setAlignment(Pos.CENTER_RIGHT);
        summary.getChildren().addAll(
                new Label("Subtotal: Rs " + String.format("%.2f", sale.getSubtotal())),
                new Label("Discount: Rs " + String.format("%.2f", sale.getDiscountAmount())),
                new Label("Tax: Rs " + String.format("%.2f", sale.getTaxAmount())),
                new Label("Grand Total: Rs " + String.format("%.2f", sale.getGrandTotal()))
        );
        summary.getChildren().get(3).setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1E3A8A;");

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button closeBtn = AppUIManager.primaryButton("Close");
        closeBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(closeBtn);

        content.getChildren().addAll(info, itemsTable, summary, footer);
        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }

    private void handleReprint(Sale sale) {
        try {
            List<SaleItem> items = saleDAO.getItemsBySale(sale.getSaleId());
            String path = invoiceService.generateInvoice(sale, items);
            AlertUtil.showInfo("Invoice Generated", "Invoice PDF saved to: " + path);
        } catch (Exception e) {
            AlertUtil.showError("Reprint Error", "Failed to generate invoice: " + e.getMessage());
        }
    }

    private static boolean canVoid() {
        SessionManager session = SessionManager.getInstance();
        return session.isAdmin() || session.isPharmacist();
    }

    /** Voids a sale; every unit returns to the lot it was sold from, in one transaction. */
    private void handleVoid(Sale sale) {
        if (!canVoid()) {
            AlertUtil.showError("Not allowed", "Only a pharmacist or administrator can void a sale.");
            return;
        }
        TextInputDialog reasonDialog = new TextInputDialog();
        reasonDialog.setTitle("Void Sale #" + sale.getSaleId());
        reasonDialog.setHeaderText("Voiding returns all items to stock. This cannot be undone.");
        reasonDialog.setContentText("Reason:");
        String reason = reasonDialog.showAndWait().map(String::trim).orElse("");
        if (reason.isEmpty()) {
            return;
        }
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                stockService.voidSale(con, sale.getSaleId(), reason);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
            AlertUtil.showInfo("Sale Voided", "Sale #" + sale.getSaleId() + " was voided and stock restored.");
            loadData();
        } catch (Exception e) {
            AlertUtil.showError("Void Failed", e.getMessage());
        }
    }
}

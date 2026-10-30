package com.pharmacy.view;

import com.pharmacy.controller.POSController;
import com.pharmacy.dao.PatientDAO;
import com.pharmacy.dao.ProductDAO;
import com.pharmacy.model.CartItem;
import com.pharmacy.model.Patient;
import com.pharmacy.model.Prescription;
import com.pharmacy.model.Product;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class POSView {

    private TableView<CartItem> cartTable;
    private TextField searchField;
    private TextField patientSearchField;
    private Label subtotalLabel, discountLabel, taxLabel, grandTotalLabel, changeLabel;
    private TextField amountPaidField, discountValueField;
    private ComboBox<String> discountTypeBox, paymentMethodBox;
    private Label errorLabel;
    private Button checkoutBtn;
    private ComboBox<Prescription> prescriptionBox;
    private POSController controller;
    private final ProductDAO productDAO = new ProductDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private Label patientLabel;

    public BorderPane getView() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #F3F4F6;");

        // ── Left: search + cart ───────────────────────────────
        VBox leftPanel = new VBox(12);
        leftPanel.setPadding(new Insets(20));
        leftPanel.setPrefWidth(700);

        Label title = new Label("Point of Sale");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        // Search row
        searchField = new TextField();
        searchField.setPromptText("Search by name, category, or scan barcode...");
        searchField.setPrefHeight(36);
        searchField.setStyle("-fx-font-size: 13px;");

        Button addBtn = new Button("+ Add to Cart");
        addBtn.setPrefHeight(36);
        addBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");

        HBox searchRow = new HBox(8, searchField, addBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Cart table
        cartTable = buildCartTable();
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        leftPanel.getChildren().addAll(title, searchRow, cartTable, errorLabel);

        // ── Right: totals + payment ───────────────────────────
        VBox rightPanel = new VBox(14);
        rightPanel.setPadding(new Insets(20));
        rightPanel.setPrefWidth(320);
        rightPanel.setStyle("-fx-background-color: white; -fx-border-color: #E5E7EB; -fx-border-width: 0 0 0 1;");

        Label payTitle = new Label("Payment");
        payTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Patient selection
        Label patientSectionLabel = new Label("Patient");
        patientSectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        patientSearchField = new TextField();
        patientSearchField.setPromptText("Search name, phone, NIC, or code…");
        patientSearchField.setPrefHeight(30);
        patientSearchField.setMaxWidth(Double.MAX_VALUE);

        Button clearPatientBtn = new Button("Clear");
        clearPatientBtn.setPrefHeight(30);
        clearPatientBtn.setStyle("-fx-cursor: hand; -fx-background-radius: 6;");

        HBox patientSearchRow = new HBox(8, patientSearchField, clearPatientBtn);
        HBox.setHgrow(patientSearchField, Priority.ALWAYS);
        patientSearchRow.setAlignment(Pos.CENTER_LEFT);

        patientLabel = new Label("Walk-in customer (no patient selected)");
        patientLabel.setWrapText(true);
        patientLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        // Prescription — filtered by selected patient
//        Label rxLabel = new Label("Prescription (for selected patient)");
//        rxLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        prescriptionBox = new ComboBox<>();
        prescriptionBox.setPromptText("Select a patient first…");
        prescriptionBox.setMaxWidth(Double.MAX_VALUE);
        prescriptionBox.setDisable(true);

        // Discount
        Label discLabel2 = new Label("Discount");
        discLabel2.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        discountTypeBox = new ComboBox<>();
        discountTypeBox.getItems().addAll("None", "Percentage (%)", "Fixed (Rs)");
        discountTypeBox.setValue("None");
        discountTypeBox.setMaxWidth(Double.MAX_VALUE);
        discountValueField = new TextField("0");
        discountValueField.setPromptText("Discount value");
        discountValueField.setPrefWidth(100);

        HBox discRow = new HBox(8, discountTypeBox, discountValueField);
        HBox.setHgrow(discountTypeBox, Priority.ALWAYS);

        // Tax
//        Label taxLabel2 = new Label("Tax (GST %)");
//        taxLabel2.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
//        TextField taxField = new TextField("0");
//        taxField.setPrefHeight(34);

        // Totals display
        Separator sep = new Separator();

        subtotalLabel  = totalRow("Subtotal");
        discountLabel  = totalRow("Discount");
        taxLabel       = totalRow("Tax");
        grandTotalLabel= totalRow("Grand Total");
        grandTotalLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #185FA5;");

        // Payment mode
        Label pmLabel = new Label("Payment Method");
        pmLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        paymentMethodBox = new ComboBox<>();
        paymentMethodBox.getItems().addAll("CASH", "CARD", "DIGITAL_WALLET");
        paymentMethodBox.setValue("CASH");
        paymentMethodBox.setMaxWidth(Double.MAX_VALUE);

        Label paidLabel = new Label("Amount Paid (Rs)");
        paidLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        amountPaidField = new TextField();
        amountPaidField.setPromptText("0.00");
        amountPaidField.setPrefHeight(36);

        changeLabel = new Label("Change: Rs 0.00");
        changeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #059669; -fx-font-weight: bold;");

        checkoutBtn = new Button("✓  Checkout & Print Invoice");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setPrefHeight(44);
        checkoutBtn.setStyle("-fx-background-color: #19a337; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setStyle("-fx-background-color: #e2e2e2; -fx-text-fill: #bb1a1a; -fx-cursor: hand; -fx-font-weight: bold");

        rightPanel.getChildren().addAll(
                payTitle,
                patientSectionLabel, patientSearchRow, patientLabel,
                prescriptionBox,
                discLabel2, discRow,
                sep,
                buildTotalRow("Subtotal:", subtotalLabel),
                buildTotalRow("Discount:", discountLabel),
                buildTotalRow("Tax:", taxLabel),
                buildTotalRow("Grand Total:", grandTotalLabel),
                new Separator(),
                pmLabel, paymentMethodBox,
                paidLabel, amountPaidField,
                changeLabel,
                checkoutBtn, clearBtn
        );//                taxLabel2, taxField,

        ScrollPane rightScroll = new ScrollPane(rightPanel);
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroll.setStyle("-fx-background-color: white; -fx-background: grey; -fx-border-color: transparent;");

        pane.setCenter(leftPanel);
        pane.setRight(rightScroll);

        // ── Create Controller FIRST ───────────────────────────────
        controller = new POSController(
                cartTable, searchField, subtotalLabel, discountLabel,
                taxLabel, grandTotalLabel, changeLabel, patientLabel,
                amountPaidField, discountValueField, discountTypeBox,
                paymentMethodBox, prescriptionBox, errorLabel);

        // ── Setup Search Suggestions (BEFORE button wire-up) ────────
        ContextMenu searchSuggestions = new ContextMenu();
        searchSuggestions.getStyleClass().add("context-menu");

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue.trim();

            if (query.isEmpty()) {
                searchSuggestions.hide();
                return;
            }

            try {
                List<Product> matches = productDAO.search(query);

                if (matches.isEmpty()) {
                    searchSuggestions.hide();
                    return;
                }

                // Build the suggestions box ONCE outside the loop
                VBox suggestionsBox = new VBox(0);
                suggestionsBox.setStyle("-fx-background-color: white;");

                for (Product product : matches) {
                    HBox row = new HBox(15);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));
                    row.setStyle("-fx-background-color: white; -fx-cursor: hand;");

                    row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #F3F4F6; -fx-cursor: hand;"));
                    row.setOnMouseExited(e -> row.setStyle("-fx-background-color: white; -fx-cursor: hand;"));

                    Label nameLabel = new Label(product.getName());
                    nameLabel.setPrefWidth(160);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1F2937;");

                    Label genericLabel = new Label(product.getBrand() == null ? product.getCategoryName() : product.getBrand());
                    genericLabel.setPrefWidth(180);
                    genericLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12px;");

                    Label stockLabel = new Label("Stock: " + product.getStockQty());
                    stockLabel.setPrefWidth(70);
                    stockLabel.setStyle(product.getStockQty() <= 10
                            ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                            : "-fx-text-fill: #059669;");

                    Label priceLabel = new Label(String.format("Rs %.2f", product.getUnitPrice()));
                    priceLabel.setPrefWidth(80);
                    priceLabel.setStyle("-fx-text-fill: #185FA5; -fx-font-weight: bold;");

                    row.getChildren().addAll(nameLabel, genericLabel, stockLabel, priceLabel);

                    final Product selectedProduct = product;
                    row.setOnMousePressed(e -> {
                        controller.addToCart(selectedProduct);
                        searchField.clear();
                        searchSuggestions.hide();
                    });

                    suggestionsBox.getChildren().add(row);

                    // Add separator between rows (not after the last)
                    if (matches.indexOf(product) < matches.size() - 1) {
                        Separator separator = new Separator();
                        separator.setStyle("-fx-background-color: #E5E7EB;");
                        suggestionsBox.getChildren().add(separator);
                    }
                }

                // Build ScrollPane ONCE outside the loop
                ScrollPane scrollPane = new ScrollPane(suggestionsBox);
                scrollPane.setPrefHeight(Math.min(400, matches.size() * 44.0));
                scrollPane.setMaxHeight(400);
                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scrollPane.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: transparent;");

                // Build MenuItem ONCE outside the loop
                searchSuggestions.getItems().clear();
                MenuItem containerItem = new MenuItem();
                containerItem.setGraphic(scrollPane);
                containerItem.setStyle("-fx-padding: 0; -fx-background-color: transparent;");
                // No setOnAction on containerItem — row clicks handle selection
                searchSuggestions.getItems().add(containerItem);

                if (!searchSuggestions.isShowing()) {
                    searchSuggestions.show(searchField, Side.BOTTOM, 0, 2);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        searchField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) searchSuggestions.hide();
        });

        // ── Patient search suggestions ───────────────────────────
        setupPatientSearchSuggestions(clearPatientBtn);

        // ── Wire up button actions ───────────────────────────────
        addBtn.setOnAction(e -> controller.addToCart());
        searchField.setOnAction(e -> controller.addToCart());
        discountTypeBox.setOnAction(e -> controller.recalculate());
        discountValueField.textProperty().addListener((o, ov, nv) -> controller.recalculate());
//        taxField.textProperty().addListener((o, ov, nv) -> controller.recalculate());
        amountPaidField.textProperty().addListener((o, ov, nv) -> controller.updateChange());
        checkoutBtn.setOnAction(e -> controller.checkout());
        clearBtn.setOnAction(e -> {
            controller.clearCart();
            patientSearchField.clear();
        });

        controller.loadPrescriptionsForPatient(null);

        pane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch(event.getCode()) {
                case F2 -> { searchField.requestFocus(); searchField.selectAll(); }
                case F3 -> { patientSearchField.requestFocus(); patientSearchField.selectAll(); }
                case F4 -> {
                    CartItem selected=cartTable.getSelectionModel().getSelectedItem();
                    if(selected==null) { com.pharmacy.util.AlertUtil.showError("Quantity", "Select a cart item first."); break; }
                    TextInputDialog dialog=new TextInputDialog(Integer.toString(selected.getQuantity()));
                    dialog.setTitle("Edit Quantity");dialog.setHeaderText(selected.getMedicineName());
                    dialog.showAndWait().ifPresent(value -> {
                        try {
                            int quantity=Integer.parseInt(value.trim());
                            if(quantity<1 || quantity>selected.getStock())throw new NumberFormatException();
                            selected.setQuantity(quantity);cartTable.refresh();controller.recalculate();
                        } catch(Exception ex) {com.pharmacy.util.AlertUtil.showError("Quantity","Enter a whole number between 1 and available stock (" + selected.getStock() + ").");}
                    });
                }
                case F9 -> checkoutBtn.fire();
                default -> { return; }
            }
            event.consume();
        });
        leftPanel.getChildren().add(new Label("F2 Search / Scan   |   F3 Patient   |   F4 Edit selected quantity   |   F9 Checkout"));
        return pane;
    }

    private void setupPatientSearchSuggestions(Button clearPatientBtn) {
        ContextMenu patientSuggestions = new ContextMenu();

        patientSearchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim();
            if (query.length() < 2) {
                patientSuggestions.hide();
                return;
            }
            try {
                List<Patient> matches = patientDAO.search(query);
                if (matches.isEmpty()) {
                    patientSuggestions.hide();
                    return;
                }

                VBox box = new VBox(0);
                box.setStyle("-fx-background-color: white;");

                for (int i = 0; i < matches.size(); i++) {
                    Patient patient = matches.get(i);
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));
                    row.setStyle("-fx-background-color: white; -fx-cursor: hand;");
                    row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;"));
                    row.setOnMouseExited(e -> row.setStyle("-fx-background-color: white; -fx-cursor: hand;"));

                    Label name = new Label(patient.getDisplayName());
                    name.setPrefWidth(150);
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1F2937;");

                    Label phone = new Label(patient.getPhone() != null ? patient.getPhone() : "");
                    phone.setPrefWidth(110);
                    phone.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12px;");

                    Label code = new Label(patient.getPatientCode() != null ? patient.getPatientCode() : "");
                    code.setPrefWidth(90);
                    code.setStyle("-fx-text-fill: #185FA5; -fx-font-size: 11px; -fx-font-weight: bold;");

                    row.getChildren().addAll(name, phone, code);

                    final Patient selected = patient;
                    row.setOnMousePressed(e -> {
                        controller.setPatient(selected);
                        patientSearchField.setText(selected.getDisplayName());
                        patientSuggestions.hide();
                    });

                    box.getChildren().add(row);
                    if (i < matches.size() - 1) {
                        Separator sep = new Separator();
                        sep.setStyle("-fx-background-color: #E5E7EB;");
                        box.getChildren().add(sep);
                    }
                }

                ScrollPane scroll = new ScrollPane(box);
                scroll.setPrefHeight(Math.min(280, matches.size() * 42.0));
                scroll.setMaxHeight(280);
                scroll.setFitToWidth(true);
                scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scroll.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: transparent;");

                patientSuggestions.getItems().clear();
                MenuItem item = new MenuItem();
                item.setGraphic(scroll);
                item.setStyle("-fx-padding: 0; -fx-background-color: transparent;");
                patientSuggestions.getItems().add(item);

                if (!patientSuggestions.isShowing()) {
                    patientSuggestions.show(patientSearchField, Side.BOTTOM, 0, 2);
                }
            } catch (Exception ex) {
                patientSuggestions.hide();
            }
        });

        patientSearchField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) patientSuggestions.hide();
        });

        clearPatientBtn.setOnAction(e -> {
            controller.clearPatient();
            patientSearchField.clear();
        });
    }

    private TableView<CartItem> buildCartTable() {
        TableView<CartItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Cart is empty. Search and add products above."));

        TableColumn<CartItem, String> nameCol = new TableColumn<>("Item");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMedicineName()));
        nameCol.setPrefWidth(240);

        TableColumn<CartItem, Double> priceCol = new TableColumn<>("Unit Price");
        priceCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getUnitPrice()).asObject());
        priceCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("Rs %.2f", v));
            }
        });

        TableColumn<CartItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantity()).asObject());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                CartItem item = getTableView().getItems().get(getIndex());
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER);
                Button minus = new Button("−");
                minus.setStyle("-fx-background-radius: 50%; -fx-cursor: hand;");
                Label qty = new Label(String.valueOf(v));
                qty.setStyle("-fx-font-weight: bold; -fx-min-width: 24; -fx-alignment: CENTER;");
                Button plus = new Button("+");
                plus.setStyle("-fx-background-radius: 50%; -fx-cursor: hand;");
                minus.setOnAction(e -> {
                    if (item.getQuantity() > 1) {
                        item.setQuantity(item.getQuantity() - 1);
                        getTableView().refresh();
                        if (controller != null) controller.recalculate();
                    }
                });
                plus.setOnAction(e -> {
                    if (item.getQuantity() < item.getStock()) {
                        item.setQuantity(item.getQuantity() + 1);
                        getTableView().refresh();
                        if (controller != null) controller.recalculate();
                    }
                });
                box.getChildren().addAll(minus, qty, plus);
                setGraphic(box);
                setText(null);
            }
        });

        TableColumn<CartItem, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStock()).asObject());
        stockCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                setText(String.valueOf(v));
                setStyle(v <= 10 ? "-fx-text-fill: #DC2626;" : "-fx-text-fill: #059669;");
            }
        });

        TableColumn<CartItem, Double> subCol = new TableColumn<>("Subtotal");
        subCol.setCellValueFactory(c -> c.getValue().subtotalProperty().asObject());
        subCol.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("Rs %.2f", v));
            }
        });

        TableColumn<CartItem, Void> removeCol = new TableColumn<>("");
        removeCol.setPrefWidth(60);
        removeCol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #DC2626; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    getTableView().getItems().remove(getIndex());
                    if (controller != null) controller.recalculate();
                });
            }
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(nameCol, priceCol, qtyCol, stockCol, subCol, removeCol);
        return table;
    }

    private Label totalRow(String text) {
        Label l = new Label("Rs 0.00");
        l.setStyle("-fx-font-size: 14px; -fx-text-fill: #374151;");
        return l;
    }

    private HBox buildTotalRow(String labelText, Label valueLabel) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B7280;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(spacer, lbl, new Label("  "), valueLabel);
    }
}

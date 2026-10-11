package com.pharmacy.view;

import com.pharmacy.controller.POSController;
import com.pharmacy.model.CartItem;
import com.pharmacy.model.Medicine;
import com.pharmacy.dao.MedicineDAO;

import java.util.List;

import com.pharmacy.model.Prescription;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class POSView {

    private TableView<CartItem> cartTable;
    private TextField searchField;
    private Label subtotalLabel, discountLabel, taxLabel, grandTotalLabel, changeLabel;
    private TextField amountPaidField, discountValueField;
    private ComboBox<String> discountTypeBox, paymentMethodBox;
    private Label errorLabel;
    private Button checkoutBtn;
    private ComboBox<Prescription> prescriptionBox;
    private POSController controller;  // Added controller field
    private final MedicineDAO medicineDAO = new MedicineDAO();  // Added for performance
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

        // Prescription (optional)
        Label rxLabel = new Label("Prescription (optional)");
        rxLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        prescriptionBox = new ComboBox<>();
        prescriptionBox.setPromptText("Select prescription...");
        prescriptionBox.setMaxWidth(Double.MAX_VALUE);

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
        Label taxLabel2 = new Label("Tax (GST %)");
        taxLabel2.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        TextField taxField = new TextField("0");
        taxField.setPrefHeight(34);

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
        checkoutBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setStyle("-fx-background-color: #AAAAAA; -fx-text-fill: #DC2626; -fx-cursor: hand; -fx-font-weight: bold");

        rightPanel.getChildren().addAll(
                payTitle, rxLabel, prescriptionBox,
                discLabel2, discRow,
                taxLabel2, taxField,
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
        );

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
                paymentMethodBox, prescriptionBox, taxField, errorLabel);

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
                List<Medicine> matches = medicineDAO.search(query);

                if (matches.isEmpty()) {
                    searchSuggestions.hide();
                    return;
                }

                // Build the suggestions box ONCE outside the loop
                VBox suggestionsBox = new VBox(0);
                suggestionsBox.setStyle("-fx-background-color: white;");

                for (Medicine med : matches) {
                    HBox row = new HBox(15);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));
                    row.setStyle("-fx-background-color: white; -fx-cursor: hand;");

                    row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #F3F4F6; -fx-cursor: hand;"));
                    row.setOnMouseExited(e -> row.setStyle("-fx-background-color: white; -fx-cursor: hand;"));

                    Label nameLabel = new Label(med.getName());
                    nameLabel.setPrefWidth(160);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1F2937;");

                    Label genericLabel = new Label(med.getGenericName());
                    genericLabel.setPrefWidth(180);
                    genericLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12px;");

                    Label stockLabel = new Label("Stock: " + med.getStockQty());
                    stockLabel.setPrefWidth(70);
                    stockLabel.setStyle(med.getStockQty() <= 10
                            ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                            : "-fx-text-fill: #059669;");

                    Label priceLabel = new Label(String.format("Rs %.2f", med.getUnitPrice()));
                    priceLabel.setPrefWidth(80);
                    priceLabel.setStyle("-fx-text-fill: #185FA5; -fx-font-weight: bold;");

                    row.getChildren().addAll(nameLabel, genericLabel, stockLabel, priceLabel);

                    // Capture med in a final variable so the lambda captures the right one
                    final Medicine selectedMed = med;
                    row.setOnMousePressed(e -> {
                        controller.addToCart(selectedMed);
                        searchField.clear();
                        searchSuggestions.hide();
                    });

                    suggestionsBox.getChildren().add(row);

                    // Add separator between rows (not after the last)
                    if (matches.indexOf(med) < matches.size() - 1) {
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

        // ── Wire up button actions ───────────────────────────────
        addBtn.setOnAction(e -> controller.addToCart());
        searchField.setOnAction(e -> controller.addToCart());
        discountTypeBox.setOnAction(e -> controller.recalculate());
        discountValueField.textProperty().addListener((o, ov, nv) -> controller.recalculate());
        taxField.textProperty().addListener((o, ov, nv) -> controller.recalculate());
        amountPaidField.textProperty().addListener((o, ov, nv) -> controller.updateChange());
        checkoutBtn.setOnAction(e -> controller.checkout());
        clearBtn.setOnAction(e -> controller.clearCart());

        controller.loadPrescriptions();

        return pane;
    }

    private TableView<CartItem> buildCartTable() {
        TableView<CartItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Cart is empty. Search and add medicines above."));

        TableColumn<CartItem, String> nameCol = new TableColumn<>("Medicine");
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

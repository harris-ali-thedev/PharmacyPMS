//package com.pharmacy.view.billing;
//
//import com.pharmacy.controller.POSController;
//import com.pharmacy.dao.MedicineDAO;
//import com.pharmacy.dao.PatientDAO;
//import com.pharmacy.model.CartItem;
//import com.pharmacy.model.Medicine;
//import com.pharmacy.model.Patient;
//import com.pharmacy.util.AppConstants;
//import com.pharmacy.util.SessionManager;
//import com.pharmacy.view.AppUIManager;
//import com.pharmacy.view.dialogs.BarcodeScannerDialog;
//import com.pharmacy.view.dialogs.MessageDialog;
//import javafx.beans.property.SimpleStringProperty;
//import javafx.collections.FXCollections;
//import javafx.collections.ObservableList;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.Node;
//import javafx.scene.control.*;
//import javafx.scene.layout.*;
//
//import java.util.List;
//
///**
// * BillingView — POS-style billing interface.
// * Left panel: product search and cart.
// * Right panel: customer, totals, payment.
// */
//public class BillingView {
//
//    private final MedicineDAO medDAO = new MedicineDAO();
//    private final PatientDAO patientDAO = new PatientDAO();
//
//    private final ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
//    private TableView<CartItem> cartTable;
//    private ComboBox<Patient> patientCombo;
//    private TextField discountField, taxField;
//    private Label subtotalLabel, discountAmtLabel, taxAmtLabel, totalLabel;
//    private ComboBox<String> paymentCombo;
//
//    public Node getView() {
//        HBox root = new HBox(0);
//        root.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");
//
//        // Left: product search + cart
//        VBox left = buildLeftPanel();
//        HBox.setHgrow(left, Priority.ALWAYS);
//
//        // Right: totals + payment
//        VBox right = buildRightPanel();
//        right.setPrefWidth(360);
//        right.setMinWidth(340);
//
//        root.getChildren().addAll(left, right);
//        return root;
//    }
//
//    // ── Left panel ────────────────────────────────────────────
//    private VBox buildLeftPanel() {
//        VBox panel = new VBox(0);
//        panel.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");
//
//        // Header
//        HBox header = new HBox(12);
//        header.setPadding(new Insets(18, 20, 14, 20));
//        header.setAlignment(Pos.CENTER_LEFT);
//        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
//        Label title = AppUIManager.pageTitle("🛒  Point of Sale");
//        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
//        Button scanBtn = AppUIManager.outlineButton("▦  Scan Barcode");
//        scanBtn.setOnAction(e -> new BarcodeScannerDialog(this::searchByBarcode).show());
//        header.getChildren().addAll(title, sp, scanBtn);
//        panel.getChildren().add(header);
//
//        // Product search
//        HBox searchRow = new HBox(10);
//        searchRow.setPadding(new Insets(14, 20, 12, 20));
//        searchRow.setAlignment(Pos.CENTER_LEFT);
//        searchRow.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
//
//        TextField searchField = new TextField();
//        searchField.setPromptText("🔍  Search medicine by name, generic, or barcode…");
//        searchField.setPrefHeight(38);
//        searchField.setStyle("-fx-background-radius:6; -fx-border-radius:6; -fx-border-color:#CBD5E1; -fx-border-width:1; -fx-font-size:13px; -fx-padding:4 10;");
//        HBox.setHgrow(searchField, Priority.ALWAYS);
//
//        Button searchBtn = AppUIManager.primaryButton("Add to Cart");
//        searchBtn.setOnAction(e -> addItemFromSearch(searchField.getText()));
//        searchField.setOnAction(e -> addItemFromSearch(searchField.getText()));
//
//        searchRow.getChildren().addAll(searchField, searchBtn);
//        panel.getChildren().add(searchRow);
//
//        // Cart table
//        VBox cartWrap = new VBox(0);
//        cartWrap.setPadding(new Insets(16, 20, 16, 20));
//        VBox.setVgrow(cartWrap, Priority.ALWAYS);
//
//        Label cartTitle = AppUIManager.sectionTitle("Shopping Cart");
//        VBox.setMargin(cartTitle, new Insets(0, 0, 10, 0));
//
//        cartTable = buildCartTable();
//        VBox.setVgrow(cartTable, Priority.ALWAYS);
//
//        // Cart action row
//        HBox cartActions = new HBox(10);
//        cartActions.setPadding(new Insets(10, 0, 0, 0));
//        Button removeBtn = AppUIManager.dangerButton("Remove Item");
//        removeBtn.setOnAction(e -> removeSelected());
//        Button clearBtn = AppUIManager.outlineButton("🗑  Clear Cart");
//        clearBtn.setOnAction(e -> clearCart());
//        cartActions.getChildren().addAll(removeBtn, clearBtn);
//
//        cartWrap.getChildren().addAll(cartTitle, cartTable, cartActions);
//        panel.getChildren().add(cartWrap);
//
//        return panel;
//    }
//
//    private TableView<CartItem> buildCartTable() {
//        TableView<CartItem> t = new TableView<>(cartItems);
//        AppUIManager.styleTable(t);
//        t.setPlaceholder(new Label("Cart is empty — search for a medicine above"));
//        t.setFixedCellSize(36);
//
//        TableColumn<CartItem, String> nameCol = new TableColumn<>("Medicine");
//        nameCol.setPrefWidth(200);
//        nameCol.setCellValueFactory(d -> new SimpleStringProperty(
//                d.getValue().getMedicineName() == null ? "—" : d.getValue().getMedicineName()));
//
//        TableColumn<CartItem, String> qtyCol = new TableColumn<>("Qty");
//        qtyCol.setPrefWidth(60);
//        qtyCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantity())));
//        qtyCol.setCellFactory(tc -> buildEditableQtyCell());
//
//        TableColumn<CartItem, String> priceCol = new TableColumn<>("Unit Price");
//        priceCol.setPrefWidth(100);
//        priceCol.setCellValueFactory(d -> new SimpleStringProperty(
//                String.format("Rs %.2f", d.getValue().getUnitPrice())));
//
//        TableColumn<CartItem, String> totalCol = new TableColumn<>("Subtotal");
//        totalCol.setPrefWidth(110);
//        totalCol.setCellValueFactory(d -> new SimpleStringProperty(
//                String.format("Rs %.2f", d.getValue().getSubtotal())));
//
//        t.getColumns().addAll(nameCol, qtyCol, priceCol, totalCol);
//        cartItems.addListener((javafx.collections.ListChangeListener<CartItem>) c -> refreshTotals());
//        return t;
//    }
//
//    private TableCell<CartItem, String> buildEditableQtyCell() {
//        return new TableCell<>() {
//            private final TextField field = new TextField();
//            { field.setPrefWidth(50); field.setStyle("-fx-font-size:12px; -fx-padding:2 4;"); }
//
//            @Override protected void updateItem(String v, boolean empty) {
//                super.updateItem(v, empty);
//                if (empty) { setGraphic(null); return; }
//                field.setText(v);
//                field.setOnAction(e -> {
//                    try {
//                        int qty = Integer.parseInt(field.getText().trim());
//                        CartItem item = getTableView().getItems().get(getIndex());
//                        item.setQuantity(qty);
//                        cartItems.set(getIndex(), item);
//                        refreshTotals();
//                    } catch (NumberFormatException ignored) {}
//                });
//                setGraphic(field);
//            }
//        };
//    }
//
//    // ── Right panel ───────────────────────────────────────────
//    private VBox buildRightPanel() {
//        VBox panel = new VBox(0);
//        panel.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent transparent " + AppUIManager.COLOR_BORDER + "; -fx-border-width:0 0 0 1;");
//
//        ScrollPane scroll = new ScrollPane(buildOrderSummary());
//        scroll.setFitToWidth(true);
//        scroll.setStyle("-fx-background-color:white; -fx-background:white;");
//        VBox.setVgrow(scroll, Priority.ALWAYS);
//        panel.getChildren().add(scroll);
//        return panel;
//    }
//
//    private VBox buildOrderSummary() {
//        VBox summary = new VBox(16);
//        summary.setPadding(new Insets(20));
//
//        // Customer
//        VBox custCard = AppUIManager.card();
//        custCard.getChildren().add(AppUIManager.sectionTitle("Customer"));
//        patientCombo = new ComboBox<>();
//        patientCombo.setMaxWidth(Double.MAX_VALUE);
//        patientCombo.setPromptText("Walk-in customer (optional)");
//        try {
//            patientCombo.getItems().setAll(patientDAO.getAll());
//            patientCombo.setConverter(new javafx.util.StringConverter<>() {
//                public String toString(Patient p) { return p == null ? "" : p.getFirstName() + " " + p.getLastName(); }
//                public Patient fromString(String s) { return null; }
//            });
//        } catch (Exception ignored) {}
//        custCard.getChildren().add(patientCombo);
//
//        // Totals card
//        VBox totalsCard = AppUIManager.card();
//        totalsCard.getChildren().add(AppUIManager.sectionTitle("Order Summary"));
//
//        GridPane totals = new GridPane();
//        totals.setHgap(12); totals.setVgap(8);
//        ColumnConstraints cl = new ColumnConstraints(); cl.setHgrow(Priority.ALWAYS);
//        ColumnConstraints cr = new ColumnConstraints(); cr.setMinWidth(100); cr.setHalignment(javafx.geometry.HPos.RIGHT);
//        totals.getColumnConstraints().addAll(cl, cr);
//
//        subtotalLabel    = valueLabel("Rs 0.00");
//        discountAmtLabel = valueLabel("Rs 0.00");
//        taxAmtLabel      = valueLabel("Rs 0.00");
//        totalLabel       = new Label("Rs 0.00");
//        totalLabel.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:" + AppUIManager.COLOR_PRIMARY + ";");
//
//        totals.add(label("Subtotal"),    0, 0); totals.add(subtotalLabel,    1, 0);
//        totals.add(label("Discount"),    0, 1); totals.add(discountAmtLabel, 1, 1);
//        totals.add(label("Tax"),         0, 2); totals.add(taxAmtLabel,      1, 2);
//
//        Region div = new Region(); div.setMinHeight(1); div.setStyle("-fx-background-color:" + AppUIManager.COLOR_BORDER + ";");
//        totals.add(div, 0, 3, 2, 1);
//
//        totals.add(label("TOTAL"),       0, 4); totals.add(totalLabel,       1, 4);
//
//        // Discount row
//        HBox discRow = new HBox(10);
//        discRow.setAlignment(Pos.CENTER_LEFT);
//        Label discLbl = new Label("Discount %");
//        discLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");
//        discountField = new TextField("0");
//        discountField.setPrefWidth(70); discountField.setPrefHeight(32);
//        discountField.setStyle("-fx-background-radius:4; -fx-border-radius:4; -fx-border-color:#CBD5E1; -fx-border-width:1;");
//        discountField.textProperty().addListener((obs, o, v) -> refreshTotals());
//
//        Label taxLbl = new Label("Tax %");
//        taxLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";");
//        taxField = new TextField(String.valueOf(AppConstants.DEFAULT_TAX_PERCENT));
//        taxField.setPrefWidth(60); taxField.setPrefHeight(32);
//        taxField.setStyle("-fx-background-radius:4; -fx-border-radius:4; -fx-border-color:#CBD5E1; -fx-border-width:1;");
//        taxField.textProperty().addListener((obs, o, v) -> refreshTotals());
//
//        discRow.getChildren().addAll(discLbl, discountField, taxLbl, taxField);
//
//        totalsCard.getChildren().addAll(totals, discRow);
//
//        // Payment method
//        VBox payCard = AppUIManager.card();
//        payCard.getChildren().add(AppUIManager.sectionTitle("Payment Method"));
//        paymentCombo = AppUIManager.styledComboBox("CASH", "CARD", "MOBILE_WALLET", "INSURANCE", "CREDIT");
//        paymentCombo.setValue("CASH");
//        payCard.getChildren().add(paymentCombo);
//
//        // Checkout button
//        Button checkoutBtn = AppUIManager.successButton("✔  Complete Sale");
//        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
//        checkoutBtn.setPrefHeight(48);
//        checkoutBtn.setStyle(checkoutBtn.getStyle() + "-fx-font-size:15px;");
//        checkoutBtn.setOnAction(e -> handleCheckout());
//
//        Button holdBtn = AppUIManager.outlineButton("⏸  Hold Order");
//        holdBtn.setMaxWidth(Double.MAX_VALUE);
//
//        summary.getChildren().addAll(custCard, totalsCard, payCard, checkoutBtn, holdBtn);
//        return summary;
//    }
//
//    // ── Logic ─────────────────────────────────────────────────
//    private void addItemFromSearch(String query) {
//        if (query == null || query.isBlank()) return;
//        try {
//            List<Medicine> results = medDAO.search(query);
//            if (results.isEmpty()) { MessageDialog.showWarning("Not Found", "No medicine found for: " + query); return; }
//            Medicine m = results.get(0);
//            if (m.getStockQty() <= 0) { MessageDialog.showWarning("Out of Stock", m.getMedicineName() + " is out of stock."); return; }
//            CartItem item = new CartItem();
//            item.setMedId(m.getMedId());
//            item.setName(m.getMedicineName());
//            item.setUnitPrice(m.getUnitPrice());
//            item.setQuantity(1);
//            cartItems.add(item);
//        } catch (Exception ex) {
//            MessageDialog.showError("Error", ex.getMessage());
//        }
//    }
//
//    private void searchByBarcode(String barcode) { addItemFromSearch(barcode); }
//
//    private void removeSelected() {
//        CartItem selected = cartTable.getSelectionModel().getSelectedItem();
//        if (selected != null) cartItems.remove(selected);
//    }
//
//    private void clearCart() { cartItems.clear(); refreshTotals(); }
//
//    private void refreshTotals() {
//        double subtotal = cartItems.stream().mapToDouble(CartItem::getSubtotal).sum();
//        double discPct = parseDouble(discountField.getText(), 0);
//        double taxPct  = parseDouble(taxField.getText(), 0);
//        double discAmt = subtotal * discPct / 100;
//        double taxAmt  = (subtotal - discAmt) * taxPct / 100;
//        double total   = subtotal - discAmt + taxAmt;
//
//        subtotalLabel.setText(String.format("Rs %.2f", subtotal));
//        discountAmtLabel.setText(String.format("- Rs %.2f", discAmt));
//        taxAmtLabel.setText(String.format("+ Rs %.2f", taxAmt));
//        totalLabel.setText(String.format("Rs %.2f", total));
//    }
//
//    private void handleCheckout() {
//        if (cartItems.isEmpty()) { MessageDialog.showWarning("Empty Cart", "Please add items to the cart first."); return; }
//        MessageDialog.showSuccess("Sale Completed",
//                "Sale processed successfully!\nTotal: " + totalLabel.getText() +
//                        "\nPayment: " + paymentCombo.getValue() + "\nConnect POSController.processSale() for full processing.");
//        clearCart();
//    }
//
//    private double parseDouble(String s, double def) {
//        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
//    }
//
//    private Label label(String text) {
//        Label l = new Label(text); l.setStyle("-fx-font-size:13px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + ";"); return l;
//    }
//    private Label valueLabel(String text) {
//        Label l = new Label(text); l.setStyle("-fx-font-size:13px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_DARK + ";"); return l;
//    }
//}
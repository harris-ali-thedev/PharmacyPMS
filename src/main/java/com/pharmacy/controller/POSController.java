package com.pharmacy.controller;

import com.pharmacy.dao.PrescriptionDAO;
import com.pharmacy.dao.ProductDAO;
import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.*;
import com.pharmacy.service.*;
import com.pharmacy.util.*;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class POSController {

    private static final int ERROR_DISPLAY_SECONDS = 6;

    // ── UI References ────────────────────────────────────────
    private final TableView<CartItem>     cartTable;
    private final TextField               searchField, amountPaidField, discountValueField;//, taxField;
    private final Label                   subtotalLabel, discountLabel, taxLabel,
            grandTotalLabel, changeLabel, errorLabel,
            patientLabel;
    private final ComboBox<String>        discountTypeBox, paymentMethodBox;
    private final ComboBox<Prescription>  prescriptionBox;

    // ── State ────────────────────────────────────────────────
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private Patient selectedPatient = null;

    // ── Services & DAOs ──────────────────────────────────────
    private final ProductDAO       productDAO    = new ProductDAO();
    private final SaleDAO          saleDAO       = new SaleDAO();
    private final PrescriptionDAO  rxDAO         = new PrescriptionDAO();
    private final DrugSafetyService safetyService = new DrugSafetyService();
    private final PrescriptionService prescriptionService = new PrescriptionService();
    private final InvoiceService   invoiceService = new InvoiceService();
    private final LabelPrintingService labelService = new LabelPrintingService();

    private PauseTransition errorHideTimer;

    public POSController(TableView<CartItem> cartTable, TextField searchField,
                         Label subtotalLabel, Label discountLabel, Label taxLabel,
                         Label grandTotalLabel, Label changeLabel, Label patientLabel,
                         TextField amountPaidField, TextField discountValueField,
                         ComboBox<String> discountTypeBox, ComboBox<String> paymentMethodBox,
                         ComboBox<Prescription> prescriptionBox, Label errorLabel) {
        this.cartTable = cartTable;
        this.searchField = searchField;
        this.subtotalLabel = subtotalLabel;
        this.discountLabel = discountLabel;
        this.taxLabel = taxLabel;
        this.grandTotalLabel = grandTotalLabel;
        this.changeLabel = changeLabel;
        this.patientLabel = patientLabel;
        this.amountPaidField = amountPaidField;
        this.discountValueField = discountValueField;
        this.discountTypeBox = discountTypeBox;
        this.paymentMethodBox = paymentMethodBox;
        this.prescriptionBox = prescriptionBox;
//        this.taxField = taxField;
        this.errorLabel = errorLabel;

        cartTable.setItems(cart);
        setupErrorAutoHide();
    }

    // ── Patient Selection ────────────────────────────────────

    public void setPatient(Patient patient) {
        this.selectedPatient = patient;
        if (patientLabel != null) {
            if (patient != null) {
                String allergies = (patient.getAllergies() != null && !patient.getAllergies().isEmpty())
                        ? " | ⚠ " + String.join(", ", patient.getAllergies())
                        : "";
                patientLabel.setText(patient.getDisplayName()
                        + " · " + nullSafe(patient.getPhone())
                        + (patient.getLoyaltyPoints() > 0 ? " · Pts " + patient.getLoyaltyPoints() : "")
                        + allergies);
                patientLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #185FA5;");
            } else {
                patientLabel.setText("Walk-in customer (no patient selected)");
                patientLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
            }
        }
        loadPrescriptionsForPatient(patient);
        SessionManager.getInstance().refreshActivity();
    }

    public Patient getSelectedPatient() {
        return selectedPatient;
    }

    public void clearPatient() {
        setPatient(null);
    }

    // ── Medicine Search & Add ────────────────────────────────

    public void addToCart() {
        try {
            SessionManager.getInstance().requireActiveSession();
        } catch (SecurityException e) {
            showError("Session expired. Please log in again.");
            return;
        }

        String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        hideError();

        try {
            List<Product> results = productDAO.search(query);
            if (results == null || results.isEmpty()) {
                showError("No product found for: " + query);
                return;
            }

            List<Product> exact = results.stream().filter(p -> query.equals(p.getBarcode())).toList();
            if (exact.size() > 1) { showError("Barcode belongs to multiple products. Correct the catalogue before selling."); return; }
            Product selected = exact.size() == 1 ? exact.get(0) : (results.size() == 1) ? results.get(0) : promptSelect(results);
            if (selected == null) return;

            addProductToCart(selected);

        } catch (Exception e) {
            showError("Error: " + e.getMessage());
        }
    }

    public void addToCart(Medicine med) {
        try {
            SessionManager.getInstance().requireActiveSession();
            hideError();
            Product product = productDAO.getByMedicineId(med.getMedId());
            if (product == null) {
                showError("This medicine is missing from the unified product catalog.");
                return;
            }
            addProductToCart(product);
        } catch (SecurityException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            showError("Could not load product: " + e.getMessage());
        }
    }

    public void addToCart(Product product) {
        try {
            SessionManager.getInstance().requireActiveSession();
            hideError();
            addProductToCart(product);
        } catch (SecurityException e) {
            showError(e.getMessage());
        }
    }

    private void addProductToCart(Product product) {
        // Shift validation
        if (!SessionManager.getInstance().hasOpenShift()) {
            showError("You must open a shift before processing sales. Go to: Shift → Open Shift.");
            return;
        }

        if (product == null || !product.isActive()) {
            showError("This product is not available for sale.");
            return;
        }

        // Out of stock
        if (product.getStockQty() <= 0) {
            showError("Out of stock: " + product.getName());
            return;
        }

        Medicine med = product.getMedicine();
        boolean prescriptionRequired = product.isMedicine()
                && (product.isRequiresPrescription() || med.isNarcotics());
        if (prescriptionRequired) {
            Prescription rx = prescriptionBox.getValue();
            if (rx == null) {
                showError(product.getName() + " requires a prescription. Attach a verified prescription first.");
                return;
            }
            User user = SessionManager.getInstance().getCurrentUser();
            if (!user.isPharmacist() && !user.isAdmin()) {
                showError("Only a pharmacist can dispense prescription medicines.");
                return;
            }
            if (med.isNarcotics()) {
                if (!user.canDispenseNarcotics()) {
                    showError("Only a licensed pharmacist can dispense narcotic substances.");
                    return;
                }
                if (selectedPatient == null || selectedPatient.getNicNumber() == null
                        || selectedPatient.getNicNumber().isBlank()) {
                    showError("Patient CNIC is required for dispensing narcotic substances (DRAP reg.).");
                    return;
                }
            }
        }

        // Cart quantity limit
        int cartQty = cart.stream()
                .filter(ci -> ci.getProductId() == product.getProductId())
                .mapToInt(CartItem::getQuantity).sum();

        if (cartQty + 1 > product.getStockQty()) {
            showError("Cannot exceed available stock (" + product.getStockQty() + ") for " + product.getName());
            return;
        }

        // Add or increment
        cart.stream().filter(ci -> ci.getProductId() == product.getProductId()).findFirst()
                .ifPresentOrElse(
                        ci -> { ci.setQuantity(ci.getQuantity() + 1); cartTable.refresh(); },
                        () -> cart.add(new CartItem(product, 1))
                );

        recalculate();
        searchField.clear();
        SessionManager.getInstance().refreshActivity();
    }

    // ── Checkout ─────────────────────────────────────────────

    public void checkout() {
        try {
            SessionManager.getInstance().requireActiveSession();
        } catch (SecurityException e) {
            showError(e.getMessage());
            return;
        }

        if (cart.isEmpty()) { showError("Cart is empty."); return; }

//        // Shift validation
//        if (!SessionManager.getInstance().hasOpenShift()) {
//            showError("You must open a shift before processing sales. Go to: Shift → Open Shift.");
//            return;
//        }

        // Amount paid validation for cash
        double grand = parseGrandTotal();
        double paid  = parseDouble(amountPaidField.getText());

        if ("CASH".equals(paymentMethodBox.getValue()) && paid < grand) {
            showError(String.format("Amount paid (Rs %.2f) is less than grand total (Rs %.2f).", paid, grand));
            return;
        }

        // ── Drug Safety Checks ───────────────────────────────
        List<Medicine> cartMedicines = cart.stream()
                .map(CartItem::getMedicine).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        Integer patientId = selectedPatient != null ? selectedPatient.getPatientId() : null;
        List<DrugInteractionAlert> alerts = cartMedicines.isEmpty()
                ? List.of() : safetyService.checkAll(patientId, cartMedicines);

        // Block on CONTRAINDICATED; warn on MAJOR/MODERATE
        List<DrugInteractionAlert> critical = alerts.stream()
                .filter(a -> a.getSeverity() == DrugInteractionAlert.Severity.CONTRAINDICATED)
                .collect(Collectors.toList());

        if (!critical.isEmpty()) {
            StringBuilder msg = new StringBuilder("DISPENSING BLOCKED — Safety alerts:\n");
            critical.forEach(a -> msg.append("• ").append(a.getDescription()).append("\n"));
            AlertUtil.showError("Drug Safety Alert", msg.toString());
            AuditLogger.log("SAFETY_BLOCK", "sales", null,
                    "Checkout blocked by safety check: " + msg);
            return;
        }

        // Warn on major/moderate alerts but allow override
        List<DrugInteractionAlert> warnings = alerts.stream()
                .filter(a -> a.getSeverity() == DrugInteractionAlert.Severity.MAJOR
                        || a.getSeverity() == DrugInteractionAlert.Severity.MODERATE)
                .collect(Collectors.toList());

        if (!warnings.isEmpty()) {
            StringBuilder msg = new StringBuilder("Safety warnings detected:\n");
            warnings.forEach(a -> msg.append("• ").append(a.getSeverityLabel())
                    .append(": ").append(a.getDescription()).append("\n"));
            msg.append("\nDo you wish to continue?");
            if (!AlertUtil.showConfirm("Drug Interaction Warning", msg.toString())) return;
        }

        // ── Prescription Validation ──────────────────────────
        Prescription rx = prescriptionBox.getValue();
        List<CartItem> prescriptionItems = cart.stream().filter(this::requiresPrescription).toList();
        if (!prescriptionItems.isEmpty() && rx == null) {
            showError("Select a valid prescription for the medicine in this cart.");
            return;
        }
        if (!prescriptionItems.isEmpty()) {
            try {
                prescriptionService.validateForDispensing(rx);
            } catch (PrescriptionService.PrescriptionException e) {
                showError(e.getMessage());
                return;
            }
        }

        // ── Confirmation ─────────────────────────────────────
        if (!AlertUtil.showConfirm("Confirm Checkout",
                String.format("Process sale of Rs %.2f for %s?",
                        grand, selectedPatient != null ? selectedPatient.getDisplayName() : "walk-in"))) {
            return;
        }

        // ── Build Sale Object ─────────────────────────────────
        try {
                Sale sale = buildSale(prescriptionItems.isEmpty() ? null : rx, paid);
                List<SaleItem> items = buildSaleItems();
                sale.setItems(items);
                List<SaleItem> prescriptionSaleItems = items.stream()
                        .filter(item -> prescriptionItems.stream()
                                .anyMatch(cartItem -> cartItem.getProductId() == item.getProductId()))
                        .toList();

                // Persist sale (FIFO batch deduction happens inside SaleDAO)
                new com.pharmacy.service.CheckoutService().complete(sale, prescriptionItems.isEmpty() ? null : rx, prescriptionSaleItems);
                clearCart();

                // Post-sale: show invoice and (optionally) print labels
                try {
                InvoicePreview previewDialog = new InvoicePreview(sale, items);
                previewDialog.showAndWait();

                if (!prescriptionItems.isEmpty()) {
                    List<PrescriptionService.DispensingLabel> labels =
                            prescriptionService.generateLabels(sale, rx, prescriptionSaleItems);
                    // Offer label printing to the user
                    if (AlertUtil.showConfirm("Print Labels",
                            "Print dispensing label(s) for " + labels.size() + " item(s)?")) {
                        for (PrescriptionService.DispensingLabel label : labels) {
                            labelService.generateLabelPdf(label, AppConstants.DEFAULT_LABEL_DIR);
                        }
                    }
                }

                } catch(Exception outputError) {
                    AlertUtil.showError("Sale saved", "Invoice or labels could not be produced. Reprint invoice #" + sale.getSaleId() + " from Invoice History. " + outputError.getMessage());
                }
            } catch (Exception e) {
                showError("Checkout failed: " + e.getMessage());
                AlertUtil.showError("Checkout Failed", e.getMessage());
                AuditLogger.log("CHECKOUT_FAILED", "sales", null, e.getMessage());
        }
    }

    // ── Recalculate ──────────────────────────────────────────

    public void recalculate() {
        SaleTotals totals = calculateTotals();
        double subtotal = totals.subtotal();

        double discAmount = totals.discount();
        double taxAmount = totals.tax();
        double grand = totals.grandTotal();

        subtotalLabel.setText(String.format("Rs %.2f", subtotal));
        discountLabel.setText(discAmount > 0
                ? String.format("- Rs %.2f", discAmount) : "Rs 0.00");
        discountLabel.setStyle(discAmount > 0
                ? "-fx-text-fill: #059669;" : "-fx-text-fill: #6B7280;");
        taxLabel.setText(String.format("Rs %.2f", taxAmount));
        grandTotalLabel.setText(String.format("Rs %.2f", grand));

        updateChange();
    }

    public void updateChange() {
        double paid  = parseDouble(amountPaidField.getText());
        double grand = parseGrandTotal();
        double change = paid - grand;

        if (change >= 0) {
            changeLabel.setText(String.format("Change: Rs %.2f", change));
            changeLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 13px;");
        } else {
            changeLabel.setText(String.format("Short: Rs %.2f", Math.abs(change)));
            changeLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 13px;");
        }
    }

    // ── Clear Cart ───────────────────────────────────────────

    public void clearCart() {
        cart.clear();
        amountPaidField.clear();
        discountValueField.setText("0");
        discountTypeBox.setValue("None");
        subtotalLabel.setText("Rs 0.00");
        discountLabel.setText("Rs 0.00");
        taxLabel.setText("Rs 0.00");
        grandTotalLabel.setText("Rs 0.00");
        changeLabel.setText("Change: Rs 0.00");
        clearPatient();
        hideError();
    }

    /** @deprecated Prefer {@link #loadPrescriptionsForPatient(Patient)} after selecting a patient. */
    public void loadPrescriptions() {
        loadPrescriptionsForPatient(selectedPatient);
    }

    public void loadPrescriptionsForPatient(Patient patient) {
        if (prescriptionBox == null) return;
        prescriptionBox.getSelectionModel().clearSelection();
        prescriptionBox.getItems().clear();

        if (patient == null) {
            prescriptionBox.setPromptText("Select a patient first…");
            prescriptionBox.setDisable(true);
            return;
        }

        prescriptionBox.setDisable(false);
        try {
            List<Prescription> rxList = rxDAO.getByPatientId(patient.getPatientId());
            prescriptionBox.setItems(FXCollections.observableArrayList(rxList));
            if (rxList == null || rxList.isEmpty()) {
                prescriptionBox.setPromptText("No open prescriptions for this patient");
            } else {
                prescriptionBox.setPromptText("Select prescription (" + rxList.size() + ")…");
            }
        } catch (Exception e) {
            prescriptionBox.setPromptText("Could not load prescriptions");
            showError("Could not load prescriptions: " + e.getMessage());
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    // ── Helpers ──────────────────────────────────────────────

    private Sale buildSale(Prescription rx, double paid) {
        User user = SessionManager.getInstance().getCurrentUser();
        Sale sale = new Sale();
        sale.setUserId(user.getUserId());
        sale.setCashierName(user.getFullName());
        if (selectedPatient != null) sale.setPatientId(selectedPatient.getPatientId());
        if (rx != null) sale.setPrescriptionId(rx.getPrescriptionId());

        SaleTotals totals = calculateTotals();
        double subtotal = totals.subtotal();
        String discType  = discountTypeBox.getValue();
        double discValue = parseDouble(discountValueField.getText());
        String discCode = "NONE".equalsIgnoreCase(discType) || "None".equals(discType) ? "NONE" : "FIXED";

        if ("Percentage (%)".equals(discType)) {
            discValue = totals.discountableSubtotal() == 0 ? 0
                    : totals.cartDiscount() * 100 / totals.discountableSubtotal();
            discCode = "PERCENT";
        }

        sale.setSubtotal(subtotal);
        sale.setDiscountType(discCode);
        sale.setDiscountValue(discValue);
        sale.setDiscountAmount(totals.discount());
        sale.setTaxPercent(totals.taxableBase() == 0 ? 0 : totals.tax() * 100 / totals.taxableBase());
        sale.setTaxAmount(totals.tax());
        sale.setGrandTotal(totals.grandTotal());
        sale.setAmountPaid(paid);
        sale.setChangeReturned(Math.max(0, paid - totals.grandTotal()));
        sale.setPaymentMethod(paymentMethodBox.getValue());
        sale.setSessionId(SessionManager.getInstance().getCurrentShiftSessionId());
        return sale;
    }

    private List<SaleItem> buildSaleItems() {
        var totals = decimalTotals();
        List<SaleItem> result = new java.util.ArrayList<>();
        for (int index = 0; index < cart.size(); index++) {
            var ci = cart.get(index);
            var line = totals.lines().get(index);
            SaleItem si = new SaleItem();
            si.setMedId(ci.getMedId());
            si.setProductId(ci.getProductId());
            si.setQuantity(ci.getQuantity());
            si.setUnitPrice(ci.getUnitPrice());
            si.setSubtotal(ci.getSubtotal());
            si.setMedicineName(ci.getMedicineName());
            si.setNarcotic(ci.isNarcotics());
            si.setSubtotal(line.gross().doubleValue());
            si.setLineDiscount(line.discount().doubleValue());
            si.setTaxPercent(com.pharmacy.service.SettingsService.current().taxMode() == com.pharmacy.service.SettingsService.TaxMode.NO_TAX ? 0 : ci.getProduct().getTaxPercent());
            si.setTaxAmount(line.tax().doubleValue());
            result.add(si);
        }
        return result;
    }

    private Product promptSelect(List<Product> results) {
        ChoiceDialog<Product> dialog = new ChoiceDialog<>(results.get(0), results);
        dialog.setTitle("Select Product");
        dialog.setHeaderText("Multiple matches found:");
        dialog.setContentText("Choose:");
        dialog.setGraphic(null);
        return dialog.showAndWait().orElse(null);
    }

    private boolean requiresPrescription(CartItem item) {
        Product product = item.getProduct();
        return product.isMedicine() && (product.isRequiresPrescription() || item.isNarcotics());
    }

    private SaleTotals calculateTotals() {
        var t = decimalTotals();
        return new SaleTotals(t.subtotal().doubleValue(), t.discount().doubleValue(), t.tax().doubleValue(),
                t.taxableBase().doubleValue(), t.cartDiscount().doubleValue(), t.discountableSubtotal().doubleValue(), t.grandTotal().doubleValue());
    }

    private com.pharmacy.service.CheckoutTotals.Totals decimalTotals() {
        String discType = discountTypeBox.getValue();
        java.math.BigDecimal value;
        try { value = new java.math.BigDecimal(discountValueField.getText().trim()).max(java.math.BigDecimal.ZERO); }
        catch (Exception ex) { value = java.math.BigDecimal.ZERO; }
        User user = SessionManager.getInstance().getCurrentUser();
        double maxDiscount = user != null && user.isAdmin()
                ? AppConstants.MAX_DISCOUNT_PERCENT_ADMIN
                : user != null && user.isPharmacist()
                ? AppConstants.MAX_DISCOUNT_PERCENT_PHARMACIST
                : AppConstants.MAX_DISCOUNT_PERCENT_CASHIER;
        var mode = "Percentage (%)".equals(discType) ? com.pharmacy.service.CheckoutTotals.Discount.PERCENT
                : "Fixed (Rs)".equals(discType) ? com.pharmacy.service.CheckoutTotals.Discount.FIXED : com.pharmacy.service.CheckoutTotals.Discount.NONE;
        var lines = cart.stream().map(item -> new com.pharmacy.service.CheckoutTotals.Input(
                java.math.BigDecimal.valueOf(item.getUnitPrice()), item.getQuantity(),
                java.math.BigDecimal.valueOf(item.getProduct().getDiscountPercent()),
                com.pharmacy.service.SettingsService.current().taxMode() == com.pharmacy.service.SettingsService.TaxMode.NO_TAX
                        ? java.math.BigDecimal.ZERO : java.math.BigDecimal.valueOf(item.getProduct().getTaxPercent()))).toList();
        return com.pharmacy.service.CheckoutTotals.calculate(lines, mode, value, java.math.BigDecimal.valueOf(maxDiscount));
    }

    private record SaleTotals(double subtotal, double discount, double tax, double taxableBase,
                              double cartDiscount, double discountableSubtotal, double grandTotal) {}

    private void setupErrorAutoHide() {
        errorHideTimer = new PauseTransition(Duration.seconds(ERROR_DISPLAY_SECONDS));
        errorHideTimer.setOnFinished(e -> hideError());
    }

    private double parseDouble(String s) {
        try { double value=Double.parseDouble(s.trim()); return Double.isFinite(value) ? value : 0; }
        catch (Exception e) { return 0; }
    }

    private double parseGrandTotal() {
        return parseDouble(grandTotalLabel.getText().replace("Rs", "").trim());
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorHideTimer.playFromStart();
    }

    private void hideError() { errorLabel.setVisible(false); }

}

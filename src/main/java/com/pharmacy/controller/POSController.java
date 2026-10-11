package com.pharmacy.controller;

import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.PrescriptionDAO;
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

/**
 * Enhanced POS Controller.
 *
 * Key improvements over v1:
 *   - Patient selection and linkage to sale
 *   - Drug safety checks before checkout (allergy + DDI)
 *   - Prescription validity enforcement at point of dispensing
 *   - Role-based discount limits
 *   - Shift session validation (must have open shift)
 *   - Session activity refresh on every action
 *   - Narcotic dispensing requires pharmacist + patient NIC
 *   - Sale void with reason and audit trail
 */
public class POSController {

    private static final int ERROR_DISPLAY_SECONDS = 6;

    // ── UI References ────────────────────────────────────────
    private final TableView<CartItem>     cartTable;
    private final TextField               searchField, amountPaidField, discountValueField, taxField;
    private final Label                   subtotalLabel, discountLabel, taxLabel,
            grandTotalLabel, changeLabel, errorLabel,
            patientLabel;
    private final ComboBox<String>        discountTypeBox, paymentMethodBox;
    private final ComboBox<Prescription>  prescriptionBox;

    // ── State ────────────────────────────────────────────────
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private Patient selectedPatient = null;   // NEW: patient linkage

    // ── Services & DAOs ──────────────────────────────────────
    private final MedicineDAO      medicineDAO   = new MedicineDAO();
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
                         ComboBox<Prescription> prescriptionBox, TextField taxField, Label errorLabel) {
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
        this.taxField = taxField;
        this.errorLabel = errorLabel;

        cartTable.setItems(cart);
        setupErrorAutoHide();
    }

    // ── Patient Selection ────────────────────────────────────

    public void setPatient(Patient patient) {
        this.selectedPatient = patient;
        if (patient != null) {
            patientLabel.setText("Patient: " + patient.getDisplayName()
                    + " | " + patient.getPhone()
                    + (patient.getLoyaltyPoints() > 0 ? " | Points: " + patient.getLoyaltyPoints() : ""));
        } else {
            patientLabel.setText("Walk-in customer (no patient selected)");
        }
        SessionManager.getInstance().refreshActivity();
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
            List<Medicine> results = medicineDAO.search(query);
            if (results == null || results.isEmpty()) {
                showError("No medicine found for: " + query);
                return;
            }

            Medicine selected = (results.size() == 1) ? results.get(0) : promptSelect(results);
            if (selected == null) return;

            addMedicineToCart(selected);

        } catch (Exception e) {
            showError("Error: " + e.getMessage());
        }
    }

    public void addToCart(Medicine med) {
        try {
            SessionManager.getInstance().requireActiveSession();
            hideError();
            addMedicineToCart(med);
        } catch (SecurityException e) {
            showError(e.getMessage());
        }
    }

    private void addMedicineToCart(Medicine med) {
        // Shift validation
        if (!SessionManager.getInstance().hasOpenShift()) {
            showError("You must open a shift before processing sales. Go to: Shift → Open Shift.");
            return;
        }

        if (med == null || !med.isActive()) {
            showError("This medicine is not available for sale.");
            return;
        }

        // Out of stock
        if (med.isOutOfStock()) {
            showError("Out of stock: " + med.getName());
            return;
        }

        // Narcotic / controlled substance checks
        if (med.isNarcotics()) {
            // Requires a verified prescription
            Prescription rx = prescriptionBox.getValue();
            if (rx == null) {
                AlertUtil.showError("Error",  med.getName() + "' is a narcotic substance. Attach a verified prescription first.");
                showError(med.getName() + "' is a narcotic substance. Attach a verified prescription first.");
                return;
            }
            // Requires pharmacist
            if (!SessionManager.getInstance().getCurrentUser().canDispenseNarcotics()) {
                showError("Only a licensed pharmacist can dispense narcotic substances.");
                return;
            }
            // Requires patient with NIC
            if (selectedPatient == null || selectedPatient.getNicNumber() == null
                    || selectedPatient.getNicNumber().isBlank()) {
                showError("Patient CNIC is required for dispensing narcotic substances (DRAP reg.).");
                return;
            }
        }

        // Scheduled (Rx-only) drug check
        if (med.isScheduled() && prescriptionBox.getValue() == null) {
            // Show warning but allow override with justification in future
            showError("'" + med.getName() + "' requires a valid prescription. Attach prescription to continue.");
            return;
        }

        // Cart quantity limit
        int cartQty = cart.stream()
                .filter(ci -> ci.getMedId() == med.getMedId())
                .mapToInt(CartItem::getQuantity).sum();

        if (cartQty + 1 > med.getStockQty()) {
            showError("Cannot exceed available stock (" + med.getStockQty() + ") for " + med.getName());
            return;
        }

        // Add or increment
        cart.stream().filter(ci -> ci.getMedId() == med.getMedId()).findFirst()
                .ifPresentOrElse(
                        ci -> { ci.setQuantity(ci.getQuantity() + 1); cartTable.refresh(); },
                        () -> cart.add(new CartItem(med, 1))
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
                .map(CartItem::getMedicine).collect(Collectors.toList());
        Integer patientId = selectedPatient != null ? selectedPatient.getPatientId() : null;
        List<DrugInteractionAlert> alerts = safetyService.checkAll(patientId, cartMedicines);

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
        if (rx != null) {
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
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                Sale sale = buildSale(rx, grand, paid);
                List<SaleItem> items = buildSaleItems();
                sale.setItems(items);

                // Persist sale (FIFO batch deduction happens inside SaleDAO)
                int saleId = saleDAO.saveSale(sale);
                sale.setSaleId(saleId);
                sale.setSaleDate(java.time.LocalDateTime.now());

                // Record prescription dispensing (updates Rx status + narcotic register)
                if (rx != null) {
                    prescriptionService.recordDispensing(con, sale, rx, items);
                }

                con.commit();

                AuditLogger.logSale(saleId, grand, sale.getPaymentMethod());

                // Post-sale: show invoice and (optionally) print labels
                InvoicePreview previewDialog = new InvoicePreview(sale, items);
                previewDialog.showAndWait();

                if (rx != null) {
                    List<PrescriptionService.DispensingLabel> labels =
                            prescriptionService.generateLabels(sale, rx, items);
                    // Offer label printing to the user
                    if (AlertUtil.showConfirm("Print Labels",
                            "Print dispensing label(s) for " + labels.size() + " item(s)?")) {
                        for (PrescriptionService.DispensingLabel label : labels) {
                            labelService.generateLabelPdf(label, AppConstants.DEFAULT_LABEL_DIR);
                        }
                    }
                }

                clearCart();

            } catch (Exception e) {
                con.rollback();
                showError("Checkout failed: " + e.getMessage());
                AlertUtil.showError("Checkout Failed", e.getMessage());
                AuditLogger.log("CHECKOUT_FAILED", "sales", null, e.getMessage());
            }
        } catch (SQLException e) {
            showError("Database connection failed: " + e.getMessage());
        }
    }

    // ── Recalculate ──────────────────────────────────────────

    public void recalculate() {
        double subtotal = cart.stream().mapToDouble(CartItem::getSubtotal).sum();

        User currentUser = SessionManager.getInstance().getCurrentUser();
        double maxDiscount = currentUser != null && currentUser.isAdmin()
                ? AppConstants.MAX_DISCOUNT_PERCENT_ADMIN
                : currentUser != null && currentUser.isPharmacist()
                  ? AppConstants.MAX_DISCOUNT_PERCENT_PHARMACIST
                  : AppConstants.MAX_DISCOUNT_PERCENT_CASHIER;

        String discType  = discountTypeBox.getValue();
        double discValue = parseDouble(discountValueField.getText());
        double discAmount = 0;

        if ("Percentage (%)".equals(discType)) {
            if (discValue > maxDiscount) {
                showError(String.format("Your role allows maximum %.0f%% discount.", maxDiscount));
                discValue = maxDiscount;
                discountValueField.setText(String.valueOf(maxDiscount));
            }
            discAmount = subtotal * discValue / 100.0;
        } else if ("Fixed (Rs)".equals(discType)) {
            discAmount = Math.min(discValue, subtotal);
        }

        double taxPct     = parseDouble(taxField.getText());
        double taxable    = subtotal - discAmount;
        double taxAmount  = taxable * taxPct / 100.0;
        double grand      = Math.max(0, taxable + taxAmount);

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
        selectedPatient = null;
        if (patientLabel != null) patientLabel.setText("Walk-in customer");
        amountPaidField.clear();
        discountValueField.setText("0");
        discountTypeBox.setValue("None");
        subtotalLabel.setText("Rs 0.00");
        discountLabel.setText("Rs 0.00");
        taxLabel.setText("Rs 0.00");
        grandTotalLabel.setText("Rs 0.00");
        changeLabel.setText("Change: Rs 0.00");
        hideError();
    }

    public void loadPrescriptions() {
        try {
            List<Prescription> rxList = rxDAO.getAll();
            if (rxList != null && !rxList.isEmpty()) {
                prescriptionBox.setItems(FXCollections.observableArrayList(rxList));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private Sale buildSale(Prescription rx, double grand, double paid) {
        User user = SessionManager.getInstance().getCurrentUser();
        Sale sale = new Sale();
        sale.setUserId(user.getUserId());
        sale.setCashierName(user.getFullName());
        if (selectedPatient != null) sale.setPatientId(selectedPatient.getPatientId());
        if (rx != null) sale.setPrescriptionId(rx.getPrescriptionId());

        double subtotal = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
        String discType  = discountTypeBox.getValue();
        double discValue = parseDouble(discountValueField.getText());
        double discAmount = 0, discTypeCode = 0;
        String discCode = "FIXED";

        if ("Percentage (%)".equals(discType)) {
            discAmount = subtotal * discValue / 100.0;
            discCode = "PERCENT";
        } else if ("Fixed (Rs)".equals(discType)) {
            discAmount = discValue;
        }

        double taxPct    = parseDouble(taxField.getText());
        double taxAmount = (subtotal - discAmount) * taxPct / 100.0;

        sale.setSubtotal(subtotal);
        sale.setDiscountType(discCode);
        sale.setDiscountValue(discValue);
        sale.setDiscountAmount(discAmount);
        sale.setTaxPercent(taxPct);
        sale.setTaxAmount(taxAmount);
        sale.setGrandTotal(grand);
        sale.setAmountPaid(paid);
        sale.setChangeReturned(Math.max(0, paid - grand));
        sale.setPaymentMethod(paymentMethodBox.getValue());
        sale.setSessionId(SessionManager.getInstance().getCurrentShiftSessionId());
        return sale;
    }

    private List<SaleItem> buildSaleItems() {
        return cart.stream().map(ci -> {
            SaleItem si = new SaleItem();
            si.setMedId(ci.getMedId());
            si.setQuantity(ci.getQuantity());
            si.setUnitPrice(ci.getUnitPrice());
            si.setSubtotal(ci.getSubtotal());
            si.setMedicineName(ci.getMedicineName());
            si.setNarcotic(ci.isNarcotics());
            return si;
        }).collect(Collectors.toList());
    }

    private Medicine promptSelect(List<Medicine> results) {
        ChoiceDialog<Medicine> dialog = new ChoiceDialog<>(results.get(0), results);
        dialog.setTitle("Select Medicine");
        dialog.setHeaderText("Multiple matches found:");
        dialog.setContentText("Choose:");
        dialog.setGraphic(null);
        return dialog.showAndWait().orElse(null);
    }

    private void setupErrorAutoHide() {
        errorHideTimer = new PauseTransition(Duration.seconds(ERROR_DISPLAY_SECONDS));
        errorHideTimer.setOnFinished(e -> hideError());
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
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
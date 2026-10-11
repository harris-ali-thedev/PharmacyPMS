package com.pharmacy.service;

import com.pharmacy.dao.NarcoticRegisterDAO;
import com.pharmacy.dao.PatientDAO;
import com.pharmacy.dao.PrescriptionDAO;
import com.pharmacy.model.*;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.util.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * Prescription Processing Service.
 *
 * Enforces the professional dispensing workflow:
 *   1. Validate prescription (not expired, not exhausted)
 *   2. Pharmacist verification (digital sign-off)
 *   3. Drug safety checks (allergy + DDI)
 *   4. Dispensing label generation
 *   5. Narcotic register entry (if applicable)
 *   6. Refill counter update
 *
 * This is a business-logic service — it coordinates multiple DAOs
 * and should be called from controllers, never called directly by the UI.
 */
public class PrescriptionService {

    private static final Logger LOG = Logger.getLogger(PrescriptionService.class.getName());

    private final PrescriptionDAO      rxDAO       = new PrescriptionDAO();
    private final PatientDAO           patientDAO  = new PatientDAO();
    private final NarcoticRegisterDAO  narcoticDAO = new NarcoticRegisterDAO();
    private final DrugSafetyService    safetyService = new DrugSafetyService();
    private final LabelPrintingService labelService  = new LabelPrintingService();

    // ── Workflow Step 1: Validate ────────────────────────────

    /**
     * Validates a prescription before dispensing.
     * Throws PrescriptionException with a clear message if invalid.
     */
    public void validateForDispensing(Prescription rx) throws PrescriptionException {
        if (rx == null) throw new PrescriptionException("Prescription not found");

        // Check status
        if (Prescription.STATUS_CANCELLED.equals(rx.getStatus())) {
            throw new PrescriptionException("Prescription #" + rx.getPrescriptionNumber()
                    + " has been cancelled");
        }
        if (Prescription.STATUS_DISPENSED.equals(rx.getStatus())) {
            throw new PrescriptionException("Prescription #" + rx.getPrescriptionNumber()
                    + " has already been fully dispensed");
        }

        // Check expiry
        if (rx.isExpired()) {
            throw new PrescriptionException("Prescription #" + rx.getPrescriptionNumber()
                    + " expired on " + rx.getExpiryDate()
                    + ". A new prescription is required.");
        }

        // Check refills
        if (!rx.canRefill() && rx.getRefillsUsed() > 0) {
            throw new PrescriptionException("No refills remaining on prescription #"
                    + rx.getPrescriptionNumber()
                    + " (" + rx.getRefillsUsed() + "/" + rx.getMaxRefills() + " used)");
        }
    }

    // ── Workflow Step 2: Verify ───────────────────────────────

    /**
     * Records pharmacist verification of a prescription.
     * Only users with PHARMACIST or ADMIN role can verify.
     */
    public void verifyPrescription(int prescriptionId, int pharmacistUserId) throws SQLException {
        User pharmacist = SessionManager.getInstance().getCurrentUser();
        if (pharmacist == null || !pharmacist.canVerifyPrescriptions()) {
            throw new SecurityException("Only pharmacists and administrators can verify prescriptions");
        }

        rxDAO.verify(prescriptionId, pharmacistUserId);
        AuditLogger.log("RX_VERIFIED", "prescriptions", prescriptionId,
                "Verified by pharmacist user_id=" + pharmacistUserId);
    }

    // ── Workflow Step 3: Safety Check ────────────────────────

    /**
     * Runs drug safety checks for the prescription.
     * Returns all alerts — the caller decides whether to block or warn.
     */
    public List<DrugInteractionAlert> runSafetyChecks(
            Prescription rx, List<Medicine> medicines) {

        return safetyService.checkAll(
                rx.getPatientId() > 0 ? rx.getPatientId() : null,
                medicines
        );
    }

    // ── Workflow Step 4: Record Dispensing ───────────────────

    /**
     * Records the dispensing of prescription items.
     * Updates refill count, prescription status, and (if narcotic) the narcotic register.
     * Must be called inside the same DB transaction as the sale.
     */
    public void recordDispensing(Connection con, Sale sale, Prescription rx,
                                 List<SaleItem> dispensedItems) throws SQLException {

        // Update prescription refill count and status
        int totalPrescribed = rx.getItems().stream()
                .mapToInt(Prescription.PrescriptionItem::getQtyPrescribed).sum();
        int totalDispensed = dispensedItems.stream()
                .mapToInt(SaleItem::getQuantity).sum();

        String newStatus = (totalDispensed >= totalPrescribed)
                ? Prescription.STATUS_DISPENSED : Prescription.STATUS_PARTIAL;

        updatePrescriptionAfterDispensing(con, rx.getPrescriptionId(),
                newStatus, rx.getRefillsUsed() + 1);

        // Record narcotic register entries for any narcotic items
        for (SaleItem item : dispensedItems) {
            if (item.isNarcotic()) {
                recordNarcoticDispensing(con, sale, rx, item);
            }
        }

        AuditLogger.log("RX_DISPENSED", "prescriptions", rx.getPrescriptionId(),
                "Prescription dispensed. Sale ID: " + sale.getSaleId()
                        + ". Status: " + newStatus);
    }

    // ── Workflow Step 5: Label Generation ───────────────────

    /**
     * Generates dispensing labels for each item.
     * Returns the label content — actual printing is handled by LabelPrintingService.
     */
    public List<DispensingLabel> generateLabels(Sale sale, Prescription rx,
                                                List<SaleItem> items) {
        return labelService.generateLabels(sale, rx, items);
    }

    // ── Prescription Creation ────────────────────────────────

    /**
     * Creates a new prescription with auto-generated number and computed expiry date.
     */
    public int createPrescription(Prescription rx) throws SQLException {
        // Set computed expiry from validity days
        if (rx.getIssuedDate() != null && rx.getValidityDays() > 0
                && rx.getExpiryDate() == null) {
            rx.setExpiryDate(rx.getIssuedDate().plusDays(rx.getValidityDays()));
        }

        // Default validity
        if (rx.getExpiryDate() == null) {
            rx.setExpiryDate(LocalDate.now().plusDays(30));
        }

        rx.setStatus(Prescription.STATUS_PENDING);

        int id = rxDAO.insert(rx);
        AuditLogger.log("RX_CREATED", "prescriptions", id,
                "New prescription for patient: " + rx.getPatientName());
        return id;
    }

    // ── Helpers ──────────────────────────────────────────────

    private void updatePrescriptionAfterDispensing(Connection con, int rxId,
                                                   String status, int refillsUsed) throws SQLException {
        String sql = "UPDATE prescriptions SET status=?, refills_used=?, updated_at=NOW() " +
                "WHERE prescription_id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, refillsUsed);
            ps.setInt(3, rxId);
            ps.executeUpdate();
        }
    }

    private void recordNarcoticDispensing(Connection con, Sale sale,
                                          Prescription rx, SaleItem item) throws SQLException {

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || !currentUser.canDispenseNarcotics()) {
            throw new SecurityException("Only pharmacists can dispense narcotic drugs");
        }

        // Patient NIC is mandatory for narcotic dispensing
        if (rx.getPatientNic() == null || rx.getPatientNic().isBlank()) {
            throw new SQLException("Patient NIC/CNIC is required for narcotic drug dispensing (DRAP regulation)");
        }

        NarcoticRegisterEntry entry = new NarcoticRegisterEntry();
        entry.setMedId(item.getMedId());
        entry.setMedicineName(item.getMedicineName());
        entry.setTransactionType(NarcoticRegisterEntry.TYPE_DISPENSED);
        entry.setQtyIn(0);
        entry.setQtyOut(item.getQuantity());
        entry.setPrescriptionId(rx.getPrescriptionId());
        entry.setPatientId(rx.getPatientId() > 0 ? rx.getPatientId() : null);
        entry.setPatientName(rx.getPatientName());
        entry.setPatientNic(rx.getPatientNic());
        entry.setDoctorName(rx.getDoctorName());
        entry.setDoctorRegNo(rx.getDoctorRegNo());
        entry.setSaleId(sale.getSaleId());
        entry.setDispensedBy(currentUser.getUserId());
        entry.setDispensedByName(currentUser.getFullName());

        narcoticDAO.insert(con, entry);
    }

    // ── DTOs ─────────────────────────────────────────────────

    /** Content for a dispensing label */
    public record DispensingLabel(
            String pharmacyName, String pharmacyAddress, String pharmacyPhone,
            String patientName, String medicineName, String strength,
            String dosage, String frequency, String duration, String instructions,
            String prescribedBy, String dispensedDate, String expiryDate,
            int refillsRemaining, boolean narcotic, String barcodeData
    ) {}

    /** Thrown when a prescription fails validation */
    public static class PrescriptionException extends RuntimeException {
        public PrescriptionException(String message) { super(message); }
    }
}

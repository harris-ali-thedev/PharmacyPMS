package com.pharmacy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Enhanced Prescription model.
 * Added: patient FK, validity period, refill count enforcement,
 * dispensing status, e-prescription flag, and prescription items.
 */
public class Prescription {

    // ── Status constants ─────────────────────────────────────
    public static final String STATUS_PENDING    = "PENDING";
    public static final String STATUS_VERIFIED   = "VERIFIED";
    public static final String STATUS_PARTIAL    = "PARTIAL";    // Some items dispensed
    public static final String STATUS_DISPENSED  = "DISPENSED";  // Fully dispensed
    public static final String STATUS_EXPIRED    = "EXPIRED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    private int    prescriptionId;
    private String prescriptionNumber;  // Auto-generated: RX-2024-000001

    // Patient linkage (was just a string in v1)
    private int    patientId;
    private String patientName;         // Denormalized for display
    private String patientNic;          // Required for narcotics

    // Prescriber
    private String doctorName;
    private String doctorRegNo;         // PMC / medical council registration
    private String doctorPhone;
    private String clinicName;

    // Prescription details
    private LocalDate issuedDate;
    private LocalDate expiryDate;       // Validity end — typically issued + 30/60/90 days
    private int    validityDays;        // Default 30 for standard, 0 for one-time only
    private int    maxRefills;          // 0 = no refills allowed
    private int    refillsUsed;
    private String diagnosis;
    private String notes;

    // Items on the prescription
    private List<PrescriptionItem> items = new ArrayList<>();

    // Verification
    private Integer verifiedBy;
    private String  verifiedByName;
    private LocalDateTime verifiedAt;

    // Source
    private boolean ePrescription;     // Received electronically
    private String  imagePath;         // Scanned image path (physical Rx)
    private String  status;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;

    public Prescription() {
        this.status = STATUS_PENDING;
        this.validityDays = 30;
        this.maxRefills = 0;
        this.refillsUsed = 0;
    }

    // ── Business logic ───────────────────────────────────────

    public boolean isExpired() {
        if (expiryDate == null) return false;
        return LocalDate.now().isAfter(expiryDate);
    }

    public boolean canRefill() {
        return !isExpired()
                && refillsUsed < maxRefills
                && !STATUS_CANCELLED.equals(status)
                && !STATUS_DISPENSED.equals(status);
    }

    public int getRefillsRemaining() {
        return Math.max(0, maxRefills - refillsUsed);
    }

    public boolean isVerified() {
        return verifiedBy != null;
    }

    public boolean requiresPrescription() {
        // True if any item on the Rx is a scheduled/narcotic drug
        return items.stream().anyMatch(i -> i.isScheduled() || i.isNarcotic());
    }

    // ── Getters / Setters ────────────────────────────────────

    public int getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(int v) { prescriptionId = v; }

    public String getPrescriptionNumber() { return prescriptionNumber; }
    public void setPrescriptionNumber(String v) { prescriptionNumber = v; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int v) { patientId = v; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { patientName = v; }

    public String getPatientNic() { return patientNic; }
    public void setPatientNic(String v) { patientNic = v; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String v) { doctorName = v; }

    public String getDoctorRegNo() { return doctorRegNo; }
    public void setDoctorRegNo(String v) { doctorRegNo = v; }

    public String getDoctorPhone() { return doctorPhone; }
    public void setDoctorPhone(String v) { doctorPhone = v; }

    public String getClinicName() { return clinicName; }
    public void setClinicName(String v) { clinicName = v; }

    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate v) { issuedDate = v; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { expiryDate = v; }

    public int getValidityDays() { return validityDays; }
    public void setValidityDays(int v) { validityDays = v; }

    public int getMaxRefills() { return maxRefills; }
    public void setMaxRefills(int v) { maxRefills = v; }

    public int getRefillsUsed() { return refillsUsed; }
    public void setRefillsUsed(int v) { refillsUsed = v; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String v) { diagnosis = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }

    public List<PrescriptionItem> getItems() { return items; }
    public void setItems(List<PrescriptionItem> v) { items = v != null ? v : new ArrayList<>(); }

    public Integer getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Integer v) { verifiedBy = v; }

    public String getVerifiedByName() { return verifiedByName; }
    public void setVerifiedByName(String v) { verifiedByName = v; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime v) { verifiedAt = v; }

    public boolean isEPrescription() { return ePrescription; }
    public void setEPrescription(boolean v) { ePrescription = v; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String v) { imagePath = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer v) { createdBy = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Prescription)) return false;
        return prescriptionId == ((Prescription) o).prescriptionId;
    }

    @Override public int hashCode() { return Objects.hash(prescriptionId); }

    @Override
    public String toString() {
        return (prescriptionNumber != null ? prescriptionNumber : "#" + prescriptionId)
                + " — " + patientName + " / Dr. " + doctorName;
    }

    // ── Inner class: Prescription line item ──────────────────
    public static class PrescriptionItem {
        private int    itemId;
        private int    medId;
        private String medicineName;
        private String dosage;         // e.g. "1 tablet"
        private String frequency;      // e.g. "Twice daily"
        private String duration;       // e.g. "7 days"
        private int    qtyPrescribed;
        private int    qtyDispensed;
        private boolean scheduled;
        private boolean narcotic;
        private String  instructions;

        public boolean isFullyDispensed() { return qtyDispensed >= qtyPrescribed; }

        public int getItemId() { return itemId; }
        public void setItemId(int v) { itemId = v; }
        public int getMedId() { return medId; }
        public void setMedId(int v) { medId = v; }
        public String getMedicineName() { return medicineName; }
        public void setMedicineName(String v) { medicineName = v; }
        public String getDosage() { return dosage; }
        public void setDosage(String v) { dosage = v; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String v) { frequency = v; }
        public String getDuration() { return duration; }
        public void setDuration(String v) { duration = v; }
        public int getQtyPrescribed() { return qtyPrescribed; }
        public void setQtyPrescribed(int v) { qtyPrescribed = v; }
        public int getQtyDispensed() { return qtyDispensed; }
        public void setQtyDispensed(int v) { qtyDispensed = v; }
        public boolean isScheduled() { return scheduled; }
        public void setScheduled(boolean v) { scheduled = v; }
        public boolean isNarcotic() { return narcotic; }
        public void setNarcotic(boolean v) { narcotic = v; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String v) { instructions = v; }
    }
}

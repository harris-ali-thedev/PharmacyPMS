package com.pharmacy.model;

import java.time.LocalDateTime;

/**
 * Narcotic / Controlled Substance Dispensing Register.
 * Every dispensing of a narcotic drug must be recorded here with
 * a running balance — required by DRAP (Pakistan) and equivalent
 * drug regulatory bodies in every jurisdiction.
 *
 * The register forms the legal audit trail for controlled substances
 * and is presented to drug inspectors on demand.
 */
public class NarcoticRegisterEntry {

    // Transaction types
    public static final String TYPE_DISPENSED   = "DISPENSED";
    public static final String TYPE_RECEIVED    = "RECEIVED";   // PO receipt
    public static final String TYPE_RETURNED    = "RETURNED";   // Returned to supplier
    public static final String TYPE_DESTROYED   = "DESTROYED";  // Expired destruction
    public static final String TYPE_ADJUSTMENT  = "ADJUSTMENT"; // Physical stock-take

    private int    entryId;
    private int    medId;
    private String medicineName;
    private String batchNumber;
    private String transactionType;

    // Quantity tracking
    private int    qtyIn;           // For RECEIVED
    private int    qtyOut;          // For DISPENSED / RETURNED / DESTROYED
    private int    runningBalance;  // Maintained automatically

    // Patient / Prescription linkage (mandatory for DISPENSED)
    private Integer prescriptionId;
    private Integer patientId;
    private String  patientName;
    private String  patientNic;     // National ID — legally required
    private String  doctorName;
    private String  doctorRegNo;

    // Sale linkage
    private Integer saleId;

    // Witness (required for DESTROYED entries)
    private String witnessName;
    private String witnessDesignation;

    // Pharmacist who made the entry
    private int    dispensedBy;
    private String dispensedByName;

    private String notes;
    private LocalDateTime entryDate;

    public NarcoticRegisterEntry() {
        this.entryDate = LocalDateTime.now();
    }

    // ── Getters / Setters ────────────────────────────────────

    public int getEntryId() { return entryId; }
    public void setEntryId(int v) { entryId = v; }

    public int getMedId() { return medId; }
    public void setMedId(int v) { medId = v; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String v) { medicineName = v; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String v) { batchNumber = v; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { transactionType = v; }

    public int getQtyIn() { return qtyIn; }
    public void setQtyIn(int v) { qtyIn = v; }

    public int getQtyOut() { return qtyOut; }
    public void setQtyOut(int v) { qtyOut = v; }

    public int getRunningBalance() { return runningBalance; }
    public void setRunningBalance(int v) { runningBalance = v; }

    public Integer getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Integer v) { prescriptionId = v; }

    public Integer getPatientId() { return patientId; }
    public void setPatientId(Integer v) { patientId = v; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { patientName = v; }

    public String getPatientNic() { return patientNic; }
    public void setPatientNic(String v) { patientNic = v; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String v) { doctorName = v; }

    public String getDoctorRegNo() { return doctorRegNo; }
    public void setDoctorRegNo(String v) { doctorRegNo = v; }

    public Integer getSaleId() { return saleId; }
    public void setSaleId(Integer v) { saleId = v; }

    public String getWitnessName() { return witnessName; }
    public void setWitnessName(String v) { witnessName = v; }

    public String getWitnessDesignation() { return witnessDesignation; }
    public void setWitnessDesignation(String v) { witnessDesignation = v; }

    public int getDispensedBy() { return dispensedBy; }
    public void setDispensedBy(int v) { dispensedBy = v; }

    public String getDispensedByName() { return dispensedByName; }
    public void setDispensedByName(String v) { dispensedByName = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }

    public LocalDateTime getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDateTime v) { entryDate = v; }
}

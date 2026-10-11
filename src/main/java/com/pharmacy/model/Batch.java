package com.pharmacy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// ─── Batch ────────────────────────────────────────────────────
public class Batch {
    private int batchId;
    private int medId;
    private String batchNumber;
    private int qtyReceived;
    private int qtyRemaining;
    private LocalDate expiryDate;
    private double unitCost;
    private double packCost;
    private LocalDateTime receivedAt;

    public int getBatchId() { return batchId; }
    public void setBatchId(int v) { batchId = v; }
    public int getMedId() { return medId; }
    public void setMedId(int v) { medId = v; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String v) { batchNumber = v; }
    public int getQtyReceived() { return qtyReceived; }
    public void setQtyReceived(int v) { qtyReceived = v; }
    public int getQtyRemaining() { return qtyRemaining; }
    public void setQtyRemaining(int v) { qtyRemaining = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { expiryDate = v; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double v) { unitCost = v; }
    public double getPackCost() { return packCost; }
    public void setPackCost(double v) { packCost = v; }
    public double getPurchaseCost() { return getUnitCost(); }
    public void setPurchaseCost(double v) { setUnitCost(v); }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime v) { receivedAt = v; }

    public boolean isExpiringSoon(int days) {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now().plusDays(days));
    }
}

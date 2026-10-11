package com.pharmacy.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Sale model.
 * Added: patient_id linkage, session_id for shift reconciliation,
 * void support with reason and auditor, and gross profit computation.
 */
public class Sale {

    private int    saleId;
    private int    userId;
    private int    patientId;          // NEW: patient linkage (0 = walk-in)
    private Integer sessionId;         // NEW: shift session
    private Integer prescriptionId;
    private LocalDateTime saleDate;

    private double subtotal;
    private String discountType;       // PERCENT | FIXED | NONE
    private double discountValue;
    private double discountAmount;
    private double taxPercent;
    private double taxAmount;
    private double grandTotal;
    private double amountPaid;
    private double changeReturned;
    private String paymentMethod;      // CASH | CARD | DIGITAL_WALLET
    private String status;             // COMPLETED | VOIDED

    // Void fields (NEW)
    private String voidReason;
    private Integer voidedBy;
    private LocalDateTime voidedAt;

    private String cashierName;
    private String patientName;        // Denormalized for display/invoice

    private List<SaleItem> items = new ArrayList<>();

    public Sale() {
        this.status = "COMPLETED";
    }

    // ── Business logic ───────────────────────────────────────

    /** Gross profit = revenue - COGS (requires purchase_cost on SaleItems) */
    public double getGrossProfit() {
        return items.stream()
                .mapToDouble(i -> (i.getUnitPrice() - i.getPurchaseCost()) * i.getQuantity())
                .sum() - discountAmount;
    }

    public boolean isWalkIn() { return patientId <= 0; }
    public boolean isVoided() { return "VOIDED".equals(status); }

    // ── Getters / Setters ────────────────────────────────────

    public int getSaleId() { return saleId; }
    public void setSaleId(int v) { saleId = v; }

    public int getUserId() { return userId; }
    public void setUserId(int v) { userId = v; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int v) { patientId = v; }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer v) { sessionId = v; }

    public Integer getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Integer v) { prescriptionId = v; }

    public LocalDateTime getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDateTime v) { saleDate = v; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double v) { subtotal = v; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String v) { discountType = v; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double v) { discountValue = v; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double v) { discountAmount = v; }

    public double getTaxPercent() { return taxPercent; }
    public void setTaxPercent(double v) { taxPercent = v; }

    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double v) { taxAmount = v; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double v) { grandTotal = v; }

    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double v) { amountPaid = v; }

    public double getChangeReturned() { return changeReturned; }
    public void setChangeReturned(double v) { changeReturned = v; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String v) { paymentMethod = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String v) { voidReason = v; }

    public Integer getVoidedBy() { return voidedBy; }
    public void setVoidedBy(Integer v) { voidedBy = v; }

    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime v) { voidedAt = v; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String v) { cashierName = v; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { patientName = v; }

    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> v) { items = v != null ? v : new ArrayList<>(); }
}

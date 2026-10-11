package com.pharmacy.model;

import java.time.LocalDateTime;

/**
 * Represents a cashier shift session.
 * Enables end-of-day cash drawer reconciliation — a day-one
 * cash management requirement for any retail pharmacy.
 */
public class ShiftSession {

    public static final String STATUS_OPEN   = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    private int    sessionId;
    private int    userId;
    private String cashierName;

    // Drawer floats
    private double openingFloat;     // Cash in drawer at shift start
    private double closingFloat;     // Physical cash counted at shift end

    // Computed totals (populated on close)
    private double totalCashSales;
    private double totalCardSales;
    private double totalDigitalSales;
    private double totalSales;
    private double totalRefunds;
    private int    transactionCount;

    // Reconciliation
    private double expectedCash;     // openingFloat + totalCashSales - totalRefunds(cash)
    private double variance;         // closingFloat - expectedCash (negative = short)
    private String varianceNotes;

    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    public ShiftSession() {
        this.status = STATUS_OPEN;
        this.openedAt = LocalDateTime.now();
    }

    // ── Business logic ───────────────────────────────────────

    /** Call when closing the shift to compute variance */
    public void computeReconciliation() {
        this.totalSales = totalCashSales + totalCardSales + totalDigitalSales;
        this.expectedCash = openingFloat + totalCashSales - totalRefunds;
        this.variance = closingFloat - expectedCash;
    }

    public boolean isBalanced() { return Math.abs(variance) < 1.0; }

    // ── Getters / Setters ────────────────────────────────────

    public int getSessionId() { return sessionId; }
    public void setSessionId(int v) { sessionId = v; }
    public int getUserId() { return userId; }
    public void setUserId(int v) { userId = v; }
    public String getCashierName() { return cashierName; }
    public void setCashierName(String v) { cashierName = v; }
    public double getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(double v) { openingFloat = v; }
    public double getClosingFloat() { return closingFloat; }
    public void setClosingFloat(double v) { closingFloat = v; }
    public double getTotalCashSales() { return totalCashSales; }
    public void setTotalCashSales(double v) { totalCashSales = v; }
    public double getTotalCardSales() { return totalCardSales; }
    public void setTotalCardSales(double v) { totalCardSales = v; }
    public double getTotalDigitalSales() { return totalDigitalSales; }
    public void setTotalDigitalSales(double v) { totalDigitalSales = v; }
    public double getTotalSales() { return totalSales; }
    public void setTotalSales(double v) { totalSales = v; }
    public double getTotalRefunds() { return totalRefunds; }
    public void setTotalRefunds(double v) { totalRefunds = v; }
    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int v) { transactionCount = v; }
    public double getExpectedCash() { return expectedCash; }
    public void setExpectedCash(double v) { expectedCash = v; }
    public double getVariance() { return variance; }
    public void setVariance(double v) { variance = v; }
    public String getVarianceNotes() { return varianceNotes; }
    public void setVarianceNotes(String v) { varianceNotes = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime v) { openedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { closedAt = v; }
}

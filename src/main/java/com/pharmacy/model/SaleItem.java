package com.pharmacy.model;

/**
 * Enhanced SaleItem.
 * Added: purchase_cost (for profit margin), is_narcotic flag.
 */
public class SaleItem {
    private int    saleItemId;
    private int    saleId;
    private int    medId;
    private Integer batchId;
    private int    quantity;
    private double unitPrice;
    private double purchaseCost;     // NEW: filled from batch.purchase_cost at sale time
    private double subtotal;
    private String medicineName;
    private boolean narcotic;        // NEW: for narcotic register trigger

    public double getGrossProfit() {
        return (unitPrice - purchaseCost) * quantity;
    }

    public int getSaleItemId() { return saleItemId; }
    public void setSaleItemId(int v) { saleItemId = v; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int v) { saleId = v; }
    public int getMedId() { return medId; }
    public void setMedId(int v) { medId = v; }
    public Integer getBatchId() { return batchId; }
    public void setBatchId(Integer v) { batchId = v; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int v) { quantity = v; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double v) { unitPrice = v; }
    public double getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(double v) { purchaseCost = v; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double v) { subtotal = v; }
    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String v) { medicineName = v; }
    public boolean isNarcotic() { return narcotic; }
    public void setNarcotic(boolean v) { narcotic = v; }
}

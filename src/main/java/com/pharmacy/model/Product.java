package com.pharmacy.model;

import java.util.Locale;

/** A saleable catalog item; medicine-specific data remains linked to Medicine when present. */
public class Product {
    private int productId;
    private Integer medicineId;
    private String categoryCode;
    private String categoryName;
    private String name;
    private String brand;
    private String sizeOrWeight;
    private String flavor;
    private String barcode;
    private double unitPrice;
    private double unitCost;
    private int stockQty;
    private int reorderLevel;
    private double taxPercent;
    private double discountPercent;
    private boolean tracksExpiry;
    private boolean requiresPrescription;
    private boolean active;
    private int supplierId;
    private Medicine medicine;

    public int getProductId() { return productId; }
    public void setProductId(int value) { productId = value; }
    public Integer getMedicineId() { return medicineId; }
    public void setMedicineId(Integer value) { medicineId = value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { categoryCode = value; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String value) { categoryName = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getBrand() { return brand; }
    public void setBrand(String value) { brand = value; }
    public String getSizeOrWeight() { return sizeOrWeight; }
    public void setSizeOrWeight(String value) { sizeOrWeight = value; }
    public String getFlavor() { return flavor; }
    public void setFlavor(String value) { flavor = value; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String value) { barcode = value; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double value) { unitPrice = value; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double value) { unitCost = value; }
    public int getStockQty() { return stockQty; }
    public void setStockQty(int value) { stockQty = value; }
    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int value) { reorderLevel = value; }
    public double getTaxPercent() { return taxPercent; }
    public void setTaxPercent(double value) { taxPercent = value; }
    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double value) { discountPercent = value; }
    public boolean isTracksExpiry() { return tracksExpiry; }
    public void setTracksExpiry(boolean value) { tracksExpiry = value; }
    public boolean isRequiresPrescription() { return requiresPrescription; }
    public void setRequiresPrescription(boolean value) { requiresPrescription = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int value) { supplierId = value; }
    public Medicine getMedicine() { return medicine; }
    public void setMedicine(Medicine value) { medicine = value; }
    public boolean isMedicine() { return medicineId != null; }

    @Override
    public String toString() {
        String category = categoryName == null ? categoryCode : categoryName;
        return String.format(Locale.ROOT, "%s · %s · %d in stock", name, category, stockQty);
    }
}

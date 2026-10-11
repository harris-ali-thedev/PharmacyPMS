package com.pharmacy.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Enhanced Medicine model.
 * Added: drug classification, dosage form, strength, route of administration,
 * generic substitution flag, manufacturer, storage conditions, and
 * proper isScheduled/isNarcotic distinction.
 */
public class Medicine {

    private final IntegerProperty medId        = new SimpleIntegerProperty();
    private final StringProperty  name          = new SimpleStringProperty();
    private final StringProperty  genericName   = new SimpleStringProperty();
    private final StringProperty  category      = new SimpleStringProperty();
    private final StringProperty  barcode       = new SimpleStringProperty();
    private final DoubleProperty  unitPrice     = new SimpleDoubleProperty();
    private final DoubleProperty  unitCost      = new SimpleDoubleProperty();
    private final DoubleProperty  packCost      = new SimpleDoubleProperty();
    private final IntegerProperty stockQty      = new SimpleIntegerProperty();
    private final IntegerProperty reorderLevel  = new SimpleIntegerProperty();
    private final BooleanProperty scheduled     = new SimpleBooleanProperty();  // Rx-only
    private final BooleanProperty narcotics     = new SimpleBooleanProperty();  // Schedule II+
    private final BooleanProperty active        = new SimpleBooleanProperty(true);

    // Drug profile fields
    private String dosageForm;       // Tablet, Capsule, Syrup, Injection, Cream, etc.
    private String strength;         // e.g. "500mg", "250mg/5ml"
    private String routeOfAdmin;     // Oral, Topical, IV, IM, Sublingual
    private String manufacturer;
    private String storageCondition; // Room Temp, Refrigerated, Frozen
    private boolean genericSubstitutionAllowed = true;
    private String therapeuticClass; // ATC code level 3, e.g. "Antibacterials for systemic use"

    // Interaction / safety
    private String allergyClass;     // e.g. "Penicillin" — used for cross-allergy checks
    private String drugInteractionGroups; // Comma-separated group codes

    // Supplier linkage
    private int    supplierId;
    private String supplierName;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;

    public Medicine() {}

    // ── JavaFX Property accessors ────────────────────────────
    public IntegerProperty medIdProperty()       { return medId; }
    public StringProperty  nameProperty()         { return name; }
    public StringProperty  genericNameProperty()  { return genericName; }
    public StringProperty  categoryProperty()     { return category; }
    public StringProperty  barcodeProperty()      { return barcode; }
    public DoubleProperty  unitPriceProperty()    { return unitPrice; }
    public DoubleProperty  unitCostProperty()     { return unitCost; }
    public DoubleProperty  packCostProperty()     { return packCost; }
    public DoubleProperty  purchaseCostProperty() { return unitCost; }
    public IntegerProperty stockQtyProperty()     { return stockQty; }
    public IntegerProperty reorderLevelProperty() { return reorderLevel; }
    public BooleanProperty scheduledProperty()    { return scheduled; }
    public BooleanProperty narcoticsProperty()    { return narcotics; }
    public BooleanProperty activeProperty()       { return active; }

    // ── Standard getters / setters ───────────────────────────
    public int    getMedId()        { return medId.get(); }
    public void   setMedId(int v)   { medId.set(v); }
    public String getName()         { return name.get(); }
    public void   setName(String v) { name.set(v); }
    public String getGenericName()         { return genericName.get(); }
    public void   setGenericName(String v) { genericName.set(v); }
    public String getCategory()          { return category.get(); }
    public void   setCategory(String v)  { category.set(v); }
    public String getBarcode()           { return barcode.get(); }
    public void   setBarcode(String v)   { barcode.set(v); }
    public double getUnitPrice()         { return unitPrice.get(); }
    public void   setUnitPrice(double v) { unitPrice.set(v); }
    public double getUnitCost()             { return unitCost.get(); }
    public void   setUnitCost(double v)     { unitCost.set(v); }
    public double getPackCost()             { return packCost.get(); }
    public void   setPackCost(double v)     { packCost.set(v); }
    public double getPurchaseCost()         { return getUnitCost(); }
    public void   setPurchaseCost(double v) { setUnitCost(v); }
    public int    getStockQty()         { return stockQty.get(); }
    public void   setStockQty(int v)    { stockQty.set(v); }
    public int    getReorderLevel()     { return reorderLevel.get(); }
    public void   setReorderLevel(int v){ reorderLevel.set(v); }
    public boolean isScheduled()        { return scheduled.get(); }
    public void    setScheduled(boolean v){ scheduled.set(v); }
    public boolean isNarcotics()        { return narcotics.get(); }
    public void    setNarcotics(boolean v){ narcotics.set(v); }
    public boolean isActive()           { return active.get(); }
    public void    setActive(boolean v) { active.set(v); }

    public String getDosageForm()         { return dosageForm; }
    public void   setDosageForm(String v) { dosageForm = v; }
    public String getStrength()           { return strength; }
    public void   setStrength(String v)   { strength = v; }
    public String getRouteOfAdmin()       { return routeOfAdmin; }
    public void   setRouteOfAdmin(String v){ routeOfAdmin = v; }
    public String getManufacturer()       { return manufacturer; }
    public void   setManufacturer(String v){ manufacturer = v; }
    public String getStorageCondition()         { return storageCondition; }
    public void   setStorageCondition(String v) { storageCondition = v; }
    public boolean isGenericSubstitutionAllowed()        { return genericSubstitutionAllowed; }
    public void    setGenericSubstitutionAllowed(boolean v){ genericSubstitutionAllowed = v; }
    public String getTherapeuticClass()         { return therapeuticClass; }
    public void   setTherapeuticClass(String v) { therapeuticClass = v; }
    public String getAllergyClass()         { return allergyClass; }
    public void   setAllergyClass(String v) { allergyClass = v; }
    public String getDrugInteractionGroups()         { return drugInteractionGroups; }
    public void   setDrugInteractionGroups(String v) { drugInteractionGroups = v; }

    public int    getSupplierId()     { return supplierId; }
    public void   setSupplierId(int v){ supplierId = v; }
    public String getSupplierName()          { return supplierName; }
    public void   setSupplierName(String v)  { supplierName = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v){ createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v){ updatedAt = v; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer v){ createdBy = v; }

    // ── Business logic ───────────────────────────────────────
    public boolean isLowStock() { return stockQty.get() <= reorderLevel.get(); }
    public boolean isOutOfStock() { return stockQty.get() <= 0; }
    public double getMargin() { return unitPrice.get() - unitCost.get(); }
    public double getMarginPercent() {
        if (unitCost.get() == 0) return 0;
        return (getMargin() / unitCost.get()) * 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Medicine)) return false;
        return getMedId() == ((Medicine) o).getMedId();
    }
    @Override public int hashCode() { return Objects.hash(getMedId()); }

    @Override
    public String toString() {
        return name.get() + (strength != null ? " " + strength : "") + " (" + category.get() + ")";
    }
}

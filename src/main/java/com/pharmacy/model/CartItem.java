package com.pharmacy.model;

import javafx.beans.property.*;

/**
 * Represents one line in the POS shopping cart.
 * Wraps a Medicine with a mutable quantity and computed subtotal.
 */
public class CartItem {
    private Medicine medicine;
    private final IntegerProperty quantity = new SimpleIntegerProperty(1);
    private final DoubleProperty subtotal   = new SimpleDoubleProperty();

    public CartItem(Medicine medicine, int quantity) {
        this.medicine = medicine;
        this.quantity.set(quantity);
        recalculate();
        this.quantity.addListener((obs, o, n) -> recalculate());
    }

    private void recalculate() {
        subtotal.set(medicine.getUnitPrice() * quantity.get());
    }

    public Medicine getMedicine() { return medicine; }

    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int v) { quantity.set(v); }
    public IntegerProperty quantityProperty() { return quantity; }

    public double getSubtotal() { return subtotal.get(); }
    public DoubleProperty subtotalProperty() { return subtotal; }

    // Delegate Medicine properties for TableView column binding
    public String getMedicineName() { return medicine.getName(); }
    public double getUnitPrice()    { return medicine.getUnitPrice(); }
    public int getStock()           { return medicine.getStockQty(); }
    public int getMedId()           { return medicine.getMedId(); }
    public boolean isNarcotics()    { return medicine.isNarcotics(); }
}

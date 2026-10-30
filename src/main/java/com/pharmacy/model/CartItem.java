package com.pharmacy.model;

import javafx.beans.property.*;

/**
 * Represents one catalog product in the POS cart with a mutable quantity and computed subtotal.
 */
public class CartItem {
    private final Product product;
    private final IntegerProperty quantity = new SimpleIntegerProperty(1);
    private final DoubleProperty subtotal   = new SimpleDoubleProperty();

    public CartItem(Medicine medicine, int quantity) {
        Product item = new Product();
        item.setProductId(medicine.getMedId());
        item.setMedicineId(medicine.getMedId());
        item.setName(medicine.getName());
        item.setCategoryCode("MEDICINE");
        item.setUnitPrice(medicine.getUnitPrice());
        item.setUnitCost(medicine.getUnitCost());
        item.setStockQty(medicine.getStockQty());
        item.setActive(medicine.isActive());
        item.setMedicine(medicine);
        this.product = item;
        initializeQuantity(quantity);
    }

    public CartItem(Product product, int quantity) {
        this.product = product;
        initializeQuantity(quantity);
    }

    private void initializeQuantity(int initialQuantity) {
        this.quantity.set(initialQuantity);
        recalculate();
        this.quantity.addListener((obs, o, n) -> recalculate());
    }

    private void recalculate() {
        subtotal.set(product.getUnitPrice() * quantity.get());
    }

    public Product getProduct() { return product; }
    public Medicine getMedicine() { return product.getMedicine(); }

    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int v) { quantity.set(v); }
    public IntegerProperty quantityProperty() { return quantity; }

    public double getSubtotal() { return subtotal.get(); }
    public DoubleProperty subtotalProperty() { return subtotal; }

    // Delegate product properties for existing TableView bindings.
    public String getMedicineName() { return product.getName(); }
    public double getUnitPrice()    { return product.getUnitPrice(); }
    public int getStock()           { return product.getStockQty(); }
    public int getMedId()           { return product.getMedicineId() == null ? 0 : product.getMedicineId(); }
    public int getProductId()       { return product.getProductId(); }
    public boolean isNarcotics()    { return product.getMedicine() != null && product.getMedicine().isNarcotics(); }
}

package com.pharmacy.model;

import javafx.beans.property.*;

public class Supplier {
    private final IntegerProperty supplierId = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty contactName = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final IntegerProperty leadDays = new SimpleIntegerProperty(3);
    private final BooleanProperty active = new SimpleBooleanProperty(true);

    public IntegerProperty supplierIdProperty() { return supplierId; }
    public StringProperty nameProperty() { return name; }
    public StringProperty contactNameProperty() { return contactName; }
    public StringProperty phoneProperty() { return phone; }
    public StringProperty emailProperty() { return email; }
    public StringProperty addressProperty() { return address; }
    public IntegerProperty leadDaysProperty() { return leadDays; }
    public BooleanProperty activeProperty() { return active; }

    public int getSupplierId() { return supplierId.get(); }
    public void setSupplierId(int v) { supplierId.set(v); }
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }
    public String getContactName() { return contactName.get(); }
    public void setContactName(String v) { contactName.set(v); }
    public String getPhone() { return phone.get(); }
    public void setPhone(String v) { phone.set(v); }
    public String getEmail() { return email.get(); }
    public void setEmail(String v) { email.set(v); }
    public String getAddress() { return address.get(); }
    public void setAddress(String v) { address.set(v); }
    public int getLeadDays() { return leadDays.get(); }
    public void setLeadDays(int v) { leadDays.set(v); }
    public boolean isActive() { return active.get(); }
    public void setActive(boolean v) { active.set(v); }

    @Override
    public String toString() { return name.get(); }
}

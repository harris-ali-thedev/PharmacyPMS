package com.pharmacy.model;

import com.pharmacy.model.enums.BloodGroup;
import com.pharmacy.model.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Central patient entity.
 * All prescriptions, sales, allergy records, and medication history
 * are anchored to this entity — the critical missing link in v1.
 */
public class Patient {

    private int patientId;
    private String patientCode;          // Auto-generated: PAT-000001
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private BloodGroup bloodGroup;
    private String phone;
    private String email;
    private String nicNumber;            // National ID — required for narcotic dispensing
    private String address;
    private String emergencyContact;
    private String emergencyPhone;

    // Clinical fields
    private List<String> allergies = new ArrayList<>();     // Drug/substance names
    private List<String> chronicConditions = new ArrayList<>();
    private String clinicalNotes;

    // Insurance
    private String insurerName;
    private String policyNumber;
    private String insurerPhone;

    // Loyalty
    private int loyaltyPoints;
    private String loyaltyTier;          // BRONZE / SILVER / GOLD

    // Audit
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;

    private String notes;

    public Patient() {}

    public Patient(int patientId, String firstName, String lastName, String phone) {
        this.patientId = patientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.fullName = firstName + " " + lastName;
    }

    // ── Business logic ───────────────────────────────────────

    /** Returns age in years, or -1 if DOB not set. */
    public int getAge() {
        if (dateOfBirth == null) return -1;
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }

    public boolean hasAllergy(String substance) {
        if (substance == null || allergies == null) return false;
        return allergies.stream()
                .anyMatch(a -> a.equalsIgnoreCase(substance.trim()));
    }

    public String getDisplayName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    // ── Getters / Setters ────────────────────────────────────

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getPatientCode() { return patientCode; }
    public void setPatientCode(String patientCode) { this.patientCode = patientCode; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }

    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies != null ? allergies : new ArrayList<>(); }

    public List<String> getChronicConditions() { return chronicConditions; }
    public void setChronicConditions(List<String> chronicConditions) { this.chronicConditions = chronicConditions != null ? chronicConditions : new ArrayList<>(); }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }

    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }

    public String getInsurerPhone() { return insurerPhone; }
    public void setInsurerPhone(String insurerPhone) { this.insurerPhone = insurerPhone; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public String getLoyaltyTier() { return loyaltyTier; }
    public void setLoyaltyTier(String loyaltyTier) { this.loyaltyTier = loyaltyTier; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient)) return false;
        Patient patient = (Patient) o;
        return patientId == patient.patientId;
    }

    @Override
    public int hashCode() { return Objects.hash(patientId); }

    @Override
    public String toString() {
        return patientCode + " — " + getDisplayName() + " (" + phone + ")";
    }
}

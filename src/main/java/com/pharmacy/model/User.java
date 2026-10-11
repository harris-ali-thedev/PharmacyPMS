package com.pharmacy.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Enhanced User model.
 * Added: last login tracking, failed login counter, account lockout,
 * password change enforcement, and fine-grained permission flags.
 */
public class User {

    // Role constants
    public static final String ROLE_MAIN_ADMIN  = "MAIN_ADMIN";
    public static final String ROLE_ADMIN       = "ADMIN";
    public static final String ROLE_PHARMACIST  = "PHARMACIST";
    public static final String ROLE_TECHNICIAN  = "TECHNICIAN";
    public static final String ROLE_CASHIER     = "CASHIER";

    private int    userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String role;
    private String email;
    private String phone;
    private String licenseNumber;       // For pharmacists — PMC license

    // Account state
    private boolean active;
    private boolean mustChangePassword; // Force change on next login
    private int     failedLoginCount;
    private boolean locked;             // Locked after N failed attempts
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String        lastLoginIp;

    // Session
    private LocalDateTime lastActivityAt; // For auto-logout

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;

    public User() {}

    public User(int userId, String username, String fullName, String role, boolean active) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
    }

    // ── Business logic ───────────────────────────────────────

    public boolean isMainAdmin()   { return ROLE_MAIN_ADMIN.equals(role); }
    public boolean isAdmin()       { return ROLE_ADMIN.equals(role) || isMainAdmin(); }
    public boolean isPharmacist()  { return ROLE_PHARMACIST.equals(role); }
    public boolean isTechnician()  { return ROLE_TECHNICIAN.equals(role); }
    public boolean isCashier()     { return ROLE_CASHIER.equals(role); }

    public boolean canVerifyPrescriptions()  { return isPharmacist() || isAdmin(); }
    public boolean canDispenseNarcotics()    { return isPharmacist() || isAdmin(); }
    public boolean canViewCostPrice()        { return isAdmin() || isPharmacist(); }
    public boolean canApplyDiscount()        { return !isCashier() || isAdmin(); }
    public boolean canVoidSale()             { return isAdmin() || isPharmacist(); }
    public boolean canManageInventory()      { return isAdmin() || isPharmacist() || isTechnician(); }
    public boolean canManageUsers()          { return isAdmin(); }
    public boolean canViewReports()          { return isAdmin() || isPharmacist(); }
    public boolean canAccessNarcoticRegister() { return isAdmin() || isPharmacist(); }

    public boolean isCurrentlyLocked() {
        if (!locked) return false;
        if (lockedUntil == null) return true;
        return LocalDateTime.now().isBefore(lockedUntil);
    }

    public void recordFailedLogin() {
        failedLoginCount++;
        if (failedLoginCount >= 5) {
            locked = true;
            lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    public void recordSuccessfulLogin(String ipAddress) {
        failedLoginCount = 0;
        locked = false;
        lockedUntil = null;
        lastLoginAt = LocalDateTime.now();
        lastLoginIp = ipAddress;
        lastActivityAt = LocalDateTime.now();
    }

    public boolean isSessionExpired(int timeoutMinutes) {
        if (lastActivityAt == null) return true;
        return LocalDateTime.now().isAfter(lastActivityAt.plusMinutes(timeoutMinutes));
    }

    public void refreshActivity() {
        lastActivityAt = LocalDateTime.now();
    }

    // ── Getters / Setters ────────────────────────────────────

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return userId == ((User) o).userId;
    }

    @Override public int hashCode() { return Objects.hash(userId); }

    @Override
    public String toString() { return fullName + " (" + role + ")"; }
}

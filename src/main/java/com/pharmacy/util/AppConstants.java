package com.pharmacy.util;

/**
 * Application-wide constants. Business rules and configurable thresholds live here.
 */
public final class AppConstants {

    private AppConstants() {}

    // ── Store identity ────────────────────────────────────────
    public static final String APP_NAME          = "MediCare Store";
    public static final String PHARMACY_NAME     = "MediCare Store";
    public static final String PHARMACY_ADDRESS  = "Blue Area, Islamabad, Pakistan";
    public static final String PHARMACY_PHONE    = "051-1234567";

    // ── POS rules ─────────────────────────────────────────────
    /** Maximum cart-level discount (%) per role. */
    public static final double MAX_DISCOUNT_PERCENT_CASHIER    = 10.0;
    public static final double MAX_DISCOUNT_PERCENT_PHARMACIST = 20.0;
    public static final double MAX_DISCOUNT_PERCENT_ADMIN      = 30.0;

    // ── Inventory thresholds ──────────────────────────────────
    public static final int EXPIRY_ALERT_DAYS    = 60;
    public static final int EXPIRY_CRITICAL_DAYS = 7;

    // ── Security ──────────────────────────────────────────────
    public static final int BCRYPT_COST = 12;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int ACCOUNT_LOCKOUT_MINUTES = 30;
    public static final int SESSION_TIMEOUT_MINUTES = 15;
    /** First-run password for the seeded admin account; a change is forced at first login. */
    public static final String BOOTSTRAP_ADMIN_PASSWORD = "Admin@123";

    // ── Backup / notifications ────────────────────────────────
    public static final int BACKUP_RETENTION_COUNT = 30;
    public static final int REFILL_REMINDER_DAYS_BEFORE = 3;

    // ── File paths ────────────────────────────────────────────
    private static final String BASE_DIR = System.getProperty("user.home") + "/MediCare";
    public static final String DEFAULT_INVOICE_DIR = BASE_DIR + "/Invoices";
    public static final String DEFAULT_LABEL_DIR   = BASE_DIR + "/Labels";
    public static final String DEFAULT_BACKUP_DIR  = BASE_DIR + "/Backups";
}

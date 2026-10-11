package com.pharmacy.util;

/**
 * Application-wide constants.
 * All business rules and configurable thresholds live here —
 * change a value here without touching logic code.
 */
public final class AppConstants {

    private AppConstants() {}

    // ── Pharmacy Identity ──────────────────────────────────────
    public static final String PHARMACY_NAME     = "MediCare Pharmacy";
    public static final String PHARMACY_ADDRESS  = "Blue Area, Islamabad, Pakistan";
    public static final String PHARMACY_PHONE    = "051-1234567";
    public static final String PHARMACY_EMAIL    = "info@medicare.pk";
    public static final String PHARMACY_LICENSE  = "DRAP-LIC-2024-001";
    public static final String PHARMACY_NTN      = "1234567-8";   // Tax ID

    // ── Application Window ────────────────────────────────────
    public static final String APP_TITLE    = "MediCare PMS — Professional Edition";
    public static final double WINDOW_WIDTH  = 1280;
    public static final double WINDOW_HEIGHT = 800;
    public static final double MIN_WIDTH     = 1024;
    public static final double MIN_HEIGHT    = 650;

    // ── POS Rules ─────────────────────────────────────────────
    /** Maximum discount a Cashier can apply (%) — enforced in POSController */
    public static final double MAX_DISCOUNT_PERCENT_CASHIER    = 10.0;
    /** Maximum discount a Pharmacist can apply (%) */
    public static final double MAX_DISCOUNT_PERCENT_PHARMACIST = 20.0;
    /** Maximum discount an Admin can apply (%) */
    public static final double MAX_DISCOUNT_PERCENT_ADMIN      = 30.0;

    /** Default GST / sales tax percentage */
    public static final double DEFAULT_TAX_PERCENT = 0.0;

    /** Loyalty points awarded per Rs 100 spent */
    public static final int LOYALTY_POINTS_PER_100 = 1;

    // ── Inventory Thresholds ──────────────────────────────────
    /** Days before expiry to show a WARNING alert */
    public static final int EXPIRY_ALERT_DAYS = 60;
    /** Days before expiry to show a CRITICAL alert */
    public static final int EXPIRY_CRITICAL_DAYS = 7;

    // ── Report Defaults ───────────────────────────────────────
    public static final int RECENT_SALES_LIMIT     = 100;
    public static final int TOP_MEDICINES_COUNT    = 10;
    public static final int SLOW_MOVER_DAYS        = 90;    // Flag items with 0 sales in 90 days
    public static final int SLOW_MOVER_MAX_SALES   = 5;     // Threshold for "slow moving"

    // ── Prescription ──────────────────────────────────────────
    /** Default prescription validity in days */
    public static final int RX_DEFAULT_VALIDITY_DAYS = 30;
    /** Max validity for narcotic prescriptions */
    public static final int RX_NARCOTIC_VALIDITY_DAYS = 7;

    // ── Security ──────────────────────────────────────────────
    /** BCrypt cost factor (12 = ~300ms on modern hardware — good balance) */
    public static final int BCRYPT_COST = 12;
    /** Minimum password length */
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** Max failed login attempts before account lockout */
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    /** Lockout duration in minutes */
    public static final int ACCOUNT_LOCKOUT_MINUTES = 30;
    /** Session inactivity timeout in minutes */
    public static final int SESSION_TIMEOUT_MINUTES = 15;

    // ── Backup ────────────────────────────────────────────────
    /** Number of daily backup files to retain before oldest is deleted */
    public static final int BACKUP_RETENTION_DAYS = 30;

    // ── Notifications ─────────────────────────────────────────
    /** Days before refill due to send SMS reminder */
    public static final int REFILL_REMINDER_DAYS_BEFORE = 3;

    // ── File Paths ────────────────────────────────────────────
    public static final String DEFAULT_INVOICE_DIR = System.getProperty("user.home") + "/MediCare/Invoices";
    public static final String DEFAULT_REPORT_DIR  = System.getProperty("user.home") + "/MediCare/Reports";
    public static final String DEFAULT_LABEL_DIR   = System.getProperty("user.home") + "/MediCare/Labels";
    public static final String DEFAULT_BACKUP_DIR  = System.getProperty("user.home") + "/MediCare/Backups";
    public static final String DEFAULT_LOG_DIR     = System.getProperty("user.home") + "/MediCare/Logs";

    // ── Roles ─────────────────────────────────────────────────
    public static final String[] ALL_ROLES = {
            "MAIN_ADMIN", "ADMIN", "PHARMACIST", "TECHNICIAN", "CASHIER"
    };
}

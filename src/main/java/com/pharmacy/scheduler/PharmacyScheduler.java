package com.pharmacy.scheduler;

import com.pharmacy.service.InventoryService;
import com.pharmacy.service.NotificationService;
import com.pharmacy.service.ReportingService;
import com.pharmacy.service.ShiftSessionService;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background Job Scheduler.
 *
 * Runs nightly maintenance jobs:
 *   1. Expiry alert generation
 *   2. Low-stock notification generation
 *   3. Refill reminders (SMS + in-app)
 *   4. Auto PO suggestion creation
 *   5. Database backup
 *   6. Audit log purge (data retention policy)
 *   7. Slow-mover report generation
 *
 * Jobs are scheduled using a ScheduledExecutorService running in
 * a daemon thread — they automatically stop when the JVM exits.
 *
 * Usage: Call PharmacyScheduler.getInstance().start() from Main.java
 */
public class PharmacyScheduler {

    private static final Logger LOG = Logger.getLogger(PharmacyScheduler.class.getName());

    private static PharmacyScheduler instance;
    private ScheduledExecutorService scheduler;

    private final NotificationService notificationService = new NotificationService();
    private final InventoryService    inventoryService    = new InventoryService();

    private PharmacyScheduler() {}

    public static synchronized PharmacyScheduler getInstance() {
        if (instance == null) instance = new PharmacyScheduler();
        return instance;
    }

    /**
     * Starts all background jobs.
     * Call from Main.java after the database connection is established.
     */
    public void start() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "PharmacyScheduler");
            t.setDaemon(true);
            return t;
        });

        // Calculate initial delay until next 2:00 AM
        long initialDelaySeconds = secondsUntil(LocalTime.of(2, 0));

        // Nightly job: runs every 24 hours starting at 2:00 AM
        scheduler.scheduleAtFixedRate(
                this::runNightlyJobs,
                initialDelaySeconds,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );

        // Expiry check: every 6 hours (more frequent for critical safety)
        scheduler.scheduleAtFixedRate(
                this::runExpiryCheck,
                60,           // 1 min initial delay
                6 * 3600,     // every 6 hours
                TimeUnit.SECONDS
        );

        LOG.info("PharmacyScheduler started. Nightly jobs at 02:00. Next run in "
                + (initialDelaySeconds / 60) + " minutes.");
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            LOG.info("PharmacyScheduler stopped.");
        }
    }

    // ── Nightly Job Bundle ───────────────────────────────────

    private void runNightlyJobs() {
        LOG.info("=== Nightly pharmacy jobs starting ===");

        runLowStockNotifications();
        runRefillReminders();
        runAuditLogPurge();
        runDatabaseBackup();

        LOG.info("=== Nightly pharmacy jobs completed ===");
        AuditLogger.log("SCHEDULER_NIGHTLY_COMPLETE", "system", null,
                "Nightly jobs completed for " + LocalDate.now());
    }

    private void runExpiryCheck() {
        try {
            int created = notificationService.generateExpiryNotifications();
            if (created > 0) {
                LOG.info("Expiry check: " + created + " new notifications created");
                AuditLogger.log("SCHEDULER_EXPIRY_CHECK", "system", null,
                        created + " expiry notifications generated");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Expiry check failed", e);
        }
    }

    private void runLowStockNotifications() {
        try {
            int created = notificationService.generateLowStockNotifications();
            LOG.info("Low-stock check: " + created + " notifications created");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Low-stock notification job failed", e);
        }
    }

    private void runRefillReminders() {
        try {
            int sent = notificationService.generateRefillReminders();
            LOG.info("Refill reminders: " + sent + " sent");
            AuditLogger.log("SCHEDULER_REFILL_REMINDERS", "system", null,
                    sent + " refill reminders sent");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Refill reminder job failed", e);
        }
    }

    /**
     * Purges audit log entries older than the retention period.
     * Complies with PECA / GDPR data retention policies.
     * Keeps narcotic register entries indefinitely (legal requirement).
     */
    private void runAuditLogPurge() {
        int retentionDays = 365 * 2; // 2 years default — adjust per local regulations
        String sql = "DELETE FROM audit_log WHERE logged_at < DATE_SUB(NOW(), INTERVAL ? DAY) " +
                "AND action NOT LIKE 'NARCOTIC%'";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, retentionDays);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOG.info("Audit log purge: " + deleted + " entries deleted (>" + retentionDays + " days)");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Audit log purge failed", e);
        }
    }

    private void runDatabaseBackup() {
        // Delegate to BackupService
        try {
            BackupScheduler.performBackup(AppConstants.DEFAULT_BACKUP_DIR);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Scheduled backup failed", e);
        }
    }

    // ── Helper ───────────────────────────────────────────────

    private long secondsUntil(LocalTime target) {
        LocalTime now = LocalTime.now();
        long seconds = now.until(target, java.time.temporal.ChronoUnit.SECONDS);
        if (seconds < 0) seconds += 86400; // Target is tomorrow
        if (seconds < 60) seconds = 60;    // Minimum 1-minute delay
        return seconds;
    }
}

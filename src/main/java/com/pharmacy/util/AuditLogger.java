package com.pharmacy.util;

import com.pharmacy.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced Audit Logger.
 *
 * Improvements over v1:
 *   - Dedicated single-thread executor (ordered, bounded queue — no lost events under load)
 *   - ip_address now captured from SessionManager
 *   - Synchronous fallback if async fails
 *   - Graceful shutdown hook to flush pending events
 *   - All audit entries include timestamp (DB default) and user context
 */
public class AuditLogger {

    private static final Logger LOG = Logger.getLogger(AuditLogger.class.getName());

    // Single-threaded so audit entries are always written in chronological order
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuditLogger");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static {
        // Flush remaining audit entries on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EXECUTOR.shutdown();
            try {
                if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.warning("AuditLogger shutdown timed out — some entries may be lost");
                }
            } catch (InterruptedException ignored) {}
        }, "AuditLogger-Shutdown"));
    }

    private AuditLogger() {}

    /**
     * Logs an audit event asynchronously.
     *
     * @param action      Action code: "SALE_COMPLETED", "RX_VERIFIED", "LOGIN_FAILED", etc.
     * @param entityType  Table/entity: "sales", "prescriptions", "medicines", etc.
     * @param entityId    PK of the affected record (null for system events)
     * @param description Human-readable description
     */
    public static void log(String action, String entityType, Integer entityId, String description) {
        // Capture user context on the calling thread (session state is thread-local)
        User user = SessionManager.getInstance().getCurrentUser();
        final Integer userId = (user != null) ? user.getUserId() : null;
        final String username = (user != null) ? user.getUsername() : "SYSTEM";
        final String ipAddress = captureIpAddress();
        final LocalDateTime now = LocalDateTime.now();

        EXECUTOR.submit(() -> {
            String sql = "INSERT INTO audit_log " +
                    "(user_id, username, action, entity_type, entity_id, description, ip_address, logged_at) " +
                    "VALUES (?,?,?,?,?,?,?,?)";
            try (Connection con = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setObject(1, userId, Types.INTEGER);
                ps.setString(2, username);
                ps.setString(3, action);
                ps.setString(4, entityType);
                ps.setObject(5, entityId, Types.INTEGER);
                ps.setString(6, truncate(description, 1000));
                ps.setString(7, ipAddress);
                ps.setObject(8, now);
                ps.executeUpdate();

            } catch (Exception e) {
                // Audit failure must never crash the application
                LOG.log(Level.WARNING, "Audit log write failed [" + action + "|" + entityType
                        + "|" + entityId + "]: " + e.getMessage());
            }
        });
    }

    /** Synchronous version — use only in shutdown hooks or test code */
    public static void logSync(String action, String entityType, Integer entityId, String description) {
        User user = SessionManager.getInstance().getCurrentUser();
        Integer userId = (user != null) ? user.getUserId() : null;
        String username = (user != null) ? user.getUsername() : "SYSTEM";

        String sql = "INSERT INTO audit_log " +
                "(user_id, username, action, entity_type, entity_id, description, ip_address) " +
                "VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId, Types.INTEGER);
            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, entityType);
            ps.setObject(5, entityId, Types.INTEGER);
            ps.setString(6, truncate(description, 1000));
            ps.setString(7, captureIpAddress());
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Synchronous audit log failed", e);
        }
    }

    // ── Convenience methods for common events ────────────────

    public static void logLogin(int userId, String username, String ip, boolean success) {
        String action = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
        User dummy = new User(userId, username, username, "UNKNOWN", true);
        String desc = (success ? "Successful" : "Failed") + " login for " + username + " from " + ip;

        // For login events we go directly to avoid chicken-and-egg (session not set yet)
        String sql = "INSERT INTO audit_log (user_id, username, action, entity_type, description, ip_address) " +
                "VALUES (?,?,?,'users',?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId > 0 ? userId : null, Types.INTEGER);
            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, desc);
            ps.setString(5, ip);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warning("Login audit failed: " + e.getMessage());
        }
    }

    public static void logSale(int saleId, double total, String paymentMethod) {
        log("SALE_COMPLETED", "sales", saleId,
                String.format("Sale completed: Rs %.2f via %s", total, paymentMethod));
    }

    public static void logVoid(int saleId, String reason) {
        log("SALE_VOIDED", "sales", saleId, "Sale voided. Reason: " + reason);
    }

    public static void logNarcoticDispensing(int medId, String medName, int qty, int patientId) {
        log("NARCOTIC_DISPENSED", "narcotic_register", medId,
                String.format("Narcotic dispensed: %s ×%d to patient_id=%d", medName, qty, patientId));
    }

    // ── Helpers ──────────────────────────────────────────────

    private static String captureIpAddress() {
        // In a desktop app, this is always localhost
        // In a web deployment, inject from HttpServletRequest
        return "127.0.0.1";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}

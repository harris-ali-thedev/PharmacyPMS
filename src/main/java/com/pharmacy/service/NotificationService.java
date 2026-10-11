package com.pharmacy.service;

import com.pharmacy.util.AppConstants;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notification Service.
 *
 * Handles all outbound notifications:
 *   - In-app alerts (stored in DB, shown on dashboard)
 *   - SMS notifications (via gateway — stubbed for local adaptation)
 *   - Refill reminders for chronic patients
 *   - Expiry alerts for staff
 *   - Low-stock alerts for purchasing team
 *
 * SMS integration: replace sendSms() with your local SMS gateway
 * (Twilio, Jazz SMS API, Telenor Business API for Pakistan, etc.)
 */
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());

    public enum NotificationType {
        LOW_STOCK, EXPIRY_WARNING, EXPIRY_CRITICAL,
        REFILL_REMINDER, RX_READY, SHIFT_REMINDER, SYSTEM
    }

    public enum NotificationPriority { LOW, NORMAL, HIGH, URGENT }

    // ── In-App Notifications ─────────────────────────────────

    /**
     * Creates a persisted in-app notification visible on the dashboard.
     */
    public int createNotification(NotificationType type, NotificationPriority priority,
                                  String title, String message,
                                  Integer targetUserId, Integer entityId) {
        String sql = "INSERT INTO notifications (type, priority, title, message, " +
                "target_user_id, entity_id, is_read, created_at) VALUES (?,?,?,?,?,?,0,NOW())";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.name());
            ps.setString(2, priority.name());
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setObject(5, targetUserId, Types.INTEGER);
            ps.setObject(6, entityId, Types.INTEGER);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to create notification: " + title, e);
        }
        return -1;
    }

    /** Returns unread notifications for a user (including system-wide notifications) */
    public List<Map<String, Object>> getUnreadNotifications(int userId) throws SQLException {
        String sql = """
            SELECT * FROM notifications
            WHERE (target_user_id = ? OR target_user_id IS NULL)
              AND is_read = 0
            ORDER BY priority DESC, created_at DESC
            LIMIT 50
            """;
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                list.add(row);
            }
        }
        return list;
    }

    public void markAsRead(int notificationId) {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE notifications SET is_read=1, read_at=NOW() WHERE notification_id=?")) {
            ps.setInt(1, notificationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Could not mark notification as read: " + e.getMessage());
        }
    }

    public void markAllAsRead(int userId) {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE notifications SET is_read=1, read_at=NOW() " +
                             "WHERE (target_user_id=? OR target_user_id IS NULL) AND is_read=0")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Could not mark all as read: " + e.getMessage());
        }
    }

    // ── Expiry Alerts ────────────────────────────────────────

    /**
     * Scans for expiring batches and creates notifications.
     * Called nightly by ExpiryAlertScheduler.
     */
    public int generateExpiryNotifications() throws SQLException {
        String sql = """
            SELECT b.batch_id, b.batch_number, b.expiry_date,
                   b.qty_remaining, m.name AS med_name, m.med_id,
                   DATEDIFF(b.expiry_date, CURDATE()) AS days_left
            FROM batches b
            JOIN medicines m ON b.med_id = m.med_id
            WHERE b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)
              AND b.qty_remaining > 0
              AND COALESCE(b.is_quarantined, 0) = 0
              AND NOT EXISTS (
                SELECT 1 FROM notifications n
                WHERE n.entity_id = b.batch_id
                  AND n.type IN ('EXPIRY_WARNING','EXPIRY_CRITICAL')
                  AND DATE(n.created_at) = CURDATE()
              )
            ORDER BY days_left ASC
            """;

        int created = 0;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, AppConstants.EXPIRY_ALERT_DAYS);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int daysLeft = rs.getInt("days_left");
                String medName = rs.getString("med_name");
                int batchId = rs.getInt("batch_id");
                int qty = rs.getInt("qty_remaining");

                NotificationType type = daysLeft <= AppConstants.EXPIRY_CRITICAL_DAYS
                        ? NotificationType.EXPIRY_CRITICAL : NotificationType.EXPIRY_WARNING;
                NotificationPriority priority = daysLeft <= AppConstants.EXPIRY_CRITICAL_DAYS
                        ? NotificationPriority.URGENT : NotificationPriority.HIGH;

                String title = type == NotificationType.EXPIRY_CRITICAL
                        ? "⚠ CRITICAL: " + medName + " expires in " + daysLeft + " day(s)"
                        : "Expiry Warning: " + medName + " expires in " + daysLeft + " days";

                String message = String.format(
                        "Batch %s of %s has %d units expiring on %s. Action required.",
                        rs.getString("batch_number"), medName, qty, rs.getDate("expiry_date"));

                createNotification(type, priority, title, message, null, batchId);
                created++;
            }
        }
        return created;
    }

    // ── Low Stock Alerts ─────────────────────────────────────

    /**
     * Scans for medicines below reorder level and creates notifications.
     * Called nightly by StockReorderScheduler.
     */
    public int generateLowStockNotifications() throws SQLException {
        String sql = """
            SELECT m.med_id, m.name, m.stock_qty, m.reorder_level, s.name AS supplier_name
            FROM medicines m
            LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id
            WHERE m.stock_qty <= m.reorder_level
              AND m.is_active = 1
              AND NOT EXISTS (
                SELECT 1 FROM notifications n
                WHERE n.entity_id = m.med_id
                  AND n.type = 'LOW_STOCK'
                  AND DATE(n.created_at) = CURDATE()
              )
            ORDER BY m.stock_qty ASC
            """;

        int created = 0;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String medName = rs.getString("name");
                int stock = rs.getInt("stock_qty");
                int reorder = rs.getInt("reorder_level");
                String supplier = rs.getString("supplier_name");

                boolean critical = stock == 0;
                String title = critical
                        ? "OUT OF STOCK: " + medName
                        : "Low Stock: " + medName + " (" + stock + " remaining)";

                String message = String.format(
                        "%s has only %d units left (reorder point: %d). Supplier: %s",
                        medName, stock, reorder, supplier != null ? supplier : "Not assigned");

                createNotification(
                        NotificationType.LOW_STOCK,
                        critical ? NotificationPriority.URGENT : NotificationPriority.HIGH,
                        title, message, null, rs.getInt("med_id"));
                created++;
            }
        }
        return created;
    }

    // ── Refill Reminders ─────────────────────────────────────

    /**
     * Identifies patients due for refills and sends reminders.
     * A patient is "due" when their last dispensing date + typical days supply
     * is within REFILL_REMINDER_DAYS_BEFORE of today.
     */
    public int generateRefillReminders() throws SQLException {
        String sql = """
            SELECT
                p.patient_id, p.full_name, p.phone,
                m.name AS medicine_name, m.med_id,
                MAX(s.sale_date) AS last_dispensed,
                SUM(si.quantity) AS last_qty,
                rx.doctor_name
            FROM patients p
            JOIN sales s ON s.patient_id = p.patient_id
            JOIN sale_items si ON si.sale_id = s.sale_id
            JOIN medicines m ON m.med_id = si.med_id
            LEFT JOIN prescriptions rx ON s.prescription_id = rx.prescription_id
            WHERE p.is_active = 1
              AND s.status = 'COMPLETED'
              AND m.category IN ('Antidiabetic','Antihypertensive','Statin',
                                 'Anti-diabetic','Beta Blocker','Thyroid')
            GROUP BY p.patient_id, m.med_id
            HAVING DATE_ADD(last_dispensed, INTERVAL last_qty DAY)
                   BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)
              AND NOT EXISTS (
                SELECT 1 FROM notifications n
                WHERE n.entity_id = p.patient_id
                  AND n.type = 'REFILL_REMINDER'
                  AND DATE(n.created_at) = CURDATE()
              )
            """;

        int sent = 0;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, AppConstants.REFILL_REMINDER_DAYS_BEFORE);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String patientName = rs.getString("full_name");
                String phone = rs.getString("phone");
                String medName = rs.getString("medicine_name");
                int patientId = rs.getInt("patient_id");

                String title = "Refill reminder: " + patientName + " — " + medName;
                String message = String.format(
                        "%s is due for a refill of %s. Last dispensed: %s",
                        patientName, medName, rs.getDate("last_dispensed"));

                createNotification(NotificationType.REFILL_REMINDER,
                        NotificationPriority.NORMAL, title, message, null, patientId);

                // Send SMS if phone available
                if (phone != null && !phone.isBlank()) {
                    String smsText = "Dear " + patientName + ", your " + medName
                            + " refill is due. Please visit " + AppConstants.PHARMACY_NAME
                            + " or call " + AppConstants.PHARMACY_PHONE;
                    sendSms(phone, smsText);
                }
                sent++;
            }
        }
        return sent;
    }

    // ── SMS Gateway (stub — replace with real provider) ──────

    /**
     * Sends an SMS via the configured gateway.
     * Replace with Twilio, Jazz (Pakistan), or any REST-based SMS API.
     */
    private void sendSms(String phoneNumber, String message) {
        // STUB: Log the SMS for now
        // Production: POST to https://api.twilio.com/2010-04-01/Accounts/{SID}/Messages
        LOG.info(String.format("[SMS-STUB] To: %s | Message: %s", phoneNumber, message));

        // Example Twilio integration (uncomment and configure):
        /*
        String accountSid = System.getenv("TWILIO_ACCOUNT_SID");
        String authToken  = System.getenv("TWILIO_AUTH_TOKEN");
        String fromNumber = System.getenv("TWILIO_FROM_NUMBER");
        if (accountSid == null) { LOG.warning("SMS not configured"); return; }

        HttpClient client = HttpClient.newHttpClient();
        String body = "To=" + URLEncoder.encode(phoneNumber, UTF_8)
                    + "&From=" + URLEncoder.encode(fromNumber, UTF_8)
                    + "&Body=" + URLEncoder.encode(message, UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages"))
            .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> LOG.info("SMS sent: " + resp.statusCode()));
        */
    }
}

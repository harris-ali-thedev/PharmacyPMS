package com.pharmacy.service;

import com.pharmacy.model.ShiftSession;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Shift Session & Cash Reconciliation Service.
 *
 * Manages cashier shift lifecycle:
 *   - Open shift with opening float
 *   - Track sales by payment method during shift
 *   - Close shift: record physical count, compute variance
 *   - Generate shift close report
 *
 * Required for any pharmacy with multiple cashiers or audit requirements.
 */
public class ShiftSessionService {

    private static final Logger LOG = Logger.getLogger(ShiftSessionService.class.getName());

    /** Opens a new shift for the given cashier */
    public int openShift(int userId, String cashierName, double openingFloat) throws SQLException {
        // Check for already-open shift
        ShiftSession existing = getOpenShift(userId);
        if (existing != null) {
            throw new IllegalStateException("You already have an open shift (ID: "
                    + existing.getSessionId() + "). Close it before opening a new one.");
        }

        String sql = "INSERT INTO shift_sessions (user_id, cashier_name, opening_float, status, opened_at) " +
                "VALUES (?,?,?,?,NOW())";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, cashierName);
            ps.setDouble(3, openingFloat);
            ps.setString(4, ShiftSession.STATUS_OPEN);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int sessionId = keys.getInt(1);
                AuditLogger.log("SHIFT_OPEN", "shift_sessions", sessionId,
                        cashierName + " opened shift with float Rs " + openingFloat);
                return sessionId;
            }
        }
        return -1;
    }

    /** Returns the currently open shift for a user, or null */
    public ShiftSession getOpenShift(int userId) throws SQLException {
        String sql = "SELECT * FROM shift_sessions WHERE user_id = ? AND status = 'OPEN' LIMIT 1";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    /**
     * Closes the shift: records physical cash count, computes all totals,
     * calculates variance, and saves the reconciliation record.
     */
    public ShiftSession closeShift(int sessionId, double physicalCashCount,
                                   String varianceNotes) throws SQLException {

        // Load current session
        ShiftSession session = getById(sessionId);
        if (session == null) throw new SQLException("Shift session not found: " + sessionId);
        if (!ShiftSession.STATUS_OPEN.equals(session.getStatus())) {
            throw new IllegalStateException("Shift " + sessionId + " is not open");
        }

        // Compute totals from sales in this shift
        populateSaleTotals(session);

        // Set physical count and compute reconciliation
        session.setClosingFloat(physicalCashCount);
        session.computeReconciliation();
        session.setVarianceNotes(varianceNotes);
        session.setStatus(ShiftSession.STATUS_CLOSED);

        // Persist
        String sql = """
            UPDATE shift_sessions SET
                closing_float=?, total_cash_sales=?, total_card_sales=?, total_digital_sales=?,
                total_sales=?, total_refunds=?, transaction_count=?,
                expected_cash=?, variance=?, variance_notes=?,
                status=?, closed_at=NOW()
            WHERE session_id=?
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, session.getClosingFloat());
            ps.setDouble(2, session.getTotalCashSales());
            ps.setDouble(3, session.getTotalCardSales());
            ps.setDouble(4, session.getTotalDigitalSales());
            ps.setDouble(5, session.getTotalSales());
            ps.setDouble(6, session.getTotalRefunds());
            ps.setInt(7, session.getTransactionCount());
            ps.setDouble(8, session.getExpectedCash());
            ps.setDouble(9, session.getVariance());
            ps.setString(10, session.getVarianceNotes());
            ps.setString(11, ShiftSession.STATUS_CLOSED);
            ps.setInt(12, sessionId);
            ps.executeUpdate();
        }

        AuditLogger.log("SHIFT_CLOSE", "shift_sessions", sessionId,
                session.getCashierName() + " closed shift. Total: Rs " + session.getTotalSales()
                        + " Variance: Rs " + session.getVariance());

        return session;
    }

    /** Populate sales totals for the shift period */
    private void populateSaleTotals(ShiftSession session) throws SQLException {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN payment_method='CASH' THEN grand_total ELSE 0 END), 0) AS cash_sales,
                COALESCE(SUM(CASE WHEN payment_method='CARD' THEN grand_total ELSE 0 END), 0) AS card_sales,
                COALESCE(SUM(CASE WHEN payment_method='DIGITAL_WALLET' THEN grand_total ELSE 0 END), 0) AS digital_sales,
                COUNT(*) AS txn_count
            FROM sales
            WHERE user_id = ?
              AND sale_date >= ?
              AND status = 'COMPLETED'
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, session.getUserId());
            ps.setTimestamp(2, Timestamp.valueOf(session.getOpenedAt()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                session.setTotalCashSales(rs.getDouble("cash_sales"));
                session.setTotalCardSales(rs.getDouble("card_sales"));
                session.setTotalDigitalSales(rs.getDouble("digital_sales"));
                session.setTransactionCount(rs.getInt("txn_count"));
            }
        }
    }

    public List<ShiftSession> getShiftsByDate(LocalDate date) throws SQLException {
        String sql = "SELECT * FROM shift_sessions WHERE DATE(opened_at) = ? ORDER BY opened_at DESC";
        List<ShiftSession> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public ShiftSession getById(int sessionId) throws SQLException {
        String sql = "SELECT * FROM shift_sessions WHERE session_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    private ShiftSession mapRow(ResultSet rs) throws SQLException {
        ShiftSession s = new ShiftSession();
        s.setSessionId(rs.getInt("session_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setCashierName(rs.getString("cashier_name"));
        s.setOpeningFloat(rs.getDouble("opening_float"));
        s.setClosingFloat(rs.getDouble("closing_float"));
        s.setTotalCashSales(rs.getDouble("total_cash_sales"));
        s.setTotalCardSales(rs.getDouble("total_card_sales"));
        s.setTotalDigitalSales(rs.getDouble("total_digital_sales"));
        s.setTotalSales(rs.getDouble("total_sales"));
        s.setTotalRefunds(rs.getDouble("total_refunds"));
        s.setTransactionCount(rs.getInt("transaction_count"));
        s.setExpectedCash(rs.getDouble("expected_cash"));
        s.setVariance(rs.getDouble("variance"));
        s.setVarianceNotes(rs.getString("variance_notes"));
        s.setStatus(rs.getString("status"));
        Timestamp opened = rs.getTimestamp("opened_at");
        if (opened != null) s.setOpenedAt(opened.toLocalDateTime());
        Timestamp closed = rs.getTimestamp("closed_at");
        if (closed != null) s.setClosedAt(closed.toLocalDateTime());
        return s;
    }
}

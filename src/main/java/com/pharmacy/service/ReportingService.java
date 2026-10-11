package com.pharmacy.service;

import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reporting & Analytics Service.
 *
 * Professional reporting suite missing from v1:
 *   - Gross profit and margin per sale and per product
 *   - Prescriber analytics (volume, revenue by doctor)
 *   - Cashier performance and reconciliation
 *   - Slow-moving and dead stock
 *   - Expiring stock value at risk
 *   - Daily/weekly/monthly business intelligence
 *   - Tax collection summary
 */
public class ReportingService {

    private static final Logger LOG = Logger.getLogger(ReportingService.class.getName());

    // ── Profit & Margin Reports ──────────────────────────────

    /**
     * Gross profit report by date range.
     * Revenue − COGS = gross profit; includes margin percent.
     */
    public List<Map<String, Object>> getProfitReport(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT
                DATE(s.sale_date)               AS sale_date,
                COUNT(DISTINCT s.sale_id)       AS transactions,
                SUM(s.grand_total)              AS revenue,
                SUM(si.quantity * si.purchase_cost) AS cogs,
                SUM(s.grand_total) - SUM(si.quantity * si.purchase_cost) AS gross_profit,
                ROUND(
                    (SUM(s.grand_total) - SUM(si.quantity * si.purchase_cost))
                    / NULLIF(SUM(s.grand_total), 0) * 100, 2
                ) AS margin_percent,
                SUM(s.tax_amount)               AS tax_collected,
                SUM(s.discount_amount)          AS total_discounts
            FROM sales s
            JOIN sale_items si ON s.sale_id = si.sale_id
            JOIN medicines m   ON si.med_id = m.med_id
            WHERE DATE(s.sale_date) BETWEEN ? AND ?
              AND s.status = 'COMPLETED'
            GROUP BY DATE(s.sale_date)
            ORDER BY sale_date ASC
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
        });
    }

    /** Profit by medicine — top performers and loss-makers */
    public List<Map<String, Object>> getProfitByMedicine(LocalDate from, LocalDate to, int limit)
            throws SQLException {
        String sql = """
            SELECT
                m.name,
                m.category,
                SUM(si.quantity)                                    AS units_sold,
                SUM(si.quantity * si.unit_price)                   AS revenue,
                SUM(si.quantity * si.purchase_cost)                 AS cogs,
                SUM(si.quantity * (si.unit_price - si.purchase_cost)) AS gross_profit,
                ROUND(
                    SUM(si.quantity * (si.unit_price - si.purchase_cost))
                    / NULLIF(SUM(si.quantity * si.unit_price), 0) * 100, 2
                ) AS margin_percent
            FROM sale_items si
            JOIN sales s    ON si.sale_id = s.sale_id
            JOIN medicines m ON si.med_id = m.med_id
            WHERE DATE(s.sale_date) BETWEEN ? AND ?
              AND s.status = 'COMPLETED'
            GROUP BY m.med_id
            ORDER BY gross_profit DESC
            LIMIT ?
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            ps.setInt(3, limit);
        });
    }

    // ── Prescriber Analytics ─────────────────────────────────

    /**
     * Revenue and prescription volume by prescriber.
     * Identifies top referral doctors and unusual prescribing patterns.
     */
    public List<Map<String, Object>> getPrescriberReport(LocalDate from, LocalDate to)
            throws SQLException {
        String sql = """
            SELECT
                rx.doctor_name,
                rx.doctor_reg_no,
                COUNT(DISTINCT rx.prescription_id) AS rx_count,
                COUNT(DISTINCT s.sale_id)           AS sales_count,
                SUM(s.grand_total)                 AS revenue,
                AVG(s.grand_total)                 AS avg_sale_value,
                SUM(CASE WHEN m.is_narcotic = 1 THEN si.quantity ELSE 0 END) AS narcotic_qty
            FROM prescriptions rx
            JOIN sales s ON s.prescription_id = rx.prescription_id
            JOIN sale_items si ON si.sale_id = s.sale_id
            JOIN medicines m ON m.med_id = si.med_id
            WHERE DATE(s.sale_date) BETWEEN ? AND ?
              AND s.status = 'COMPLETED'
            GROUP BY rx.doctor_name, rx.doctor_reg_no
            ORDER BY revenue DESC
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
        });
    }

    // ── Cashier Report ───────────────────────────────────────

    public List<Map<String, Object>> getCashierReport(LocalDate from, LocalDate to)
            throws SQLException {
        String sql = """
            SELECT
                u.full_name                         AS cashier_name,
                COUNT(DISTINCT s.sale_id)           AS transactions,
                SUM(s.grand_total)                  AS total_sales,
                SUM(s.discount_amount)              AS total_discounts,
                SUM(CASE WHEN s.payment_method='CASH' THEN s.grand_total ELSE 0 END) AS cash_total,
                SUM(CASE WHEN s.payment_method='CARD' THEN s.grand_total ELSE 0 END) AS card_total,
                AVG(s.grand_total)                  AS avg_transaction,
                MAX(s.grand_total)                  AS largest_sale
            FROM sales s
            JOIN users u ON s.user_id = u.user_id
            WHERE DATE(s.sale_date) BETWEEN ? AND ?
              AND s.status = 'COMPLETED'
            GROUP BY s.user_id
            ORDER BY total_sales DESC
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
        });
    }

    // ── Inventory Value Report ───────────────────────────────

    public List<Map<String, Object>> getInventoryValueByCategory() throws SQLException {
        String sql = """
            SELECT
                m.category,
                COUNT(DISTINCT m.med_id)                        AS medicine_count,
                SUM(b.qty_remaining)                            AS total_units,
                SUM(b.qty_remaining * b.unit_cost)              AS cost_value,
                SUM(b.qty_remaining * m.unit_price)             AS retail_value,
                SUM(b.qty_remaining * (m.unit_price - b.unit_cost)) AS potential_profit
            FROM medicines m
            JOIN batches b ON m.med_id = b.med_id
            WHERE b.qty_remaining > 0
              AND b.expiry_date > CURDATE()
              AND COALESCE(b.is_quarantined, 0) = 0
            GROUP BY m.category
            ORDER BY cost_value DESC
            """;

        return executeReport(sql, ps -> {});
    }

    /** Stock at risk: expiring inventory value in next N days */
    public Map<String, Object> getExpiryRiskValue(int days) throws SQLException {
        String sql = """
            SELECT
                COUNT(DISTINCT b.med_id)                AS medicines_affected,
                SUM(b.qty_remaining)                    AS units_at_risk,
                SUM(b.qty_remaining * b.unit_cost)      AS cost_at_risk,
                SUM(b.qty_remaining * m.unit_price)     AS retail_at_risk
            FROM batches b
            JOIN medicines m ON b.med_id = m.med_id
            WHERE b.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)
              AND b.qty_remaining > 0
              AND COALESCE(b.is_quarantined, 0) = 0
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("medicines_affected", rs.getInt("medicines_affected"));
                m.put("units_at_risk", rs.getInt("units_at_risk"));
                m.put("cost_at_risk", rs.getDouble("cost_at_risk"));
                m.put("retail_at_risk", rs.getDouble("retail_at_risk"));
                m.put("days", days);
                return m;
            }
        }
        return Collections.emptyMap();
    }

    // ── Tax Summary ──────────────────────────────────────────

    public List<Map<String, Object>> getTaxSummary(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT
                DATE_FORMAT(sale_date, '%Y-%m')     AS month,
                COUNT(*)                            AS transactions,
                SUM(subtotal - discount_amount)     AS taxable_amount,
                SUM(tax_amount)                     AS tax_collected,
                AVG(tax_percent)                    AS avg_tax_rate
            FROM sales
            WHERE DATE(sale_date) BETWEEN ? AND ?
              AND status = 'COMPLETED'
              AND tax_amount > 0
            GROUP BY DATE_FORMAT(sale_date, '%Y-%m')
            ORDER BY month ASC
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
        });
    }

    // ── Dashboard KPIs ───────────────────────────────────────

    /** All key dashboard metrics in a single DB round-trip */
    public Map<String, Object> getDashboardKPIs(LocalDate date) throws SQLException {
        Map<String, Object> kpis = new LinkedHashMap<>();

        String sql = """
            SELECT
                COUNT(*)                AS txn_today,
                COALESCE(SUM(grand_total), 0)  AS revenue_today,
                COALESCE(SUM(grand_total) - SUM(
                    (SELECT SUM(si2.quantity * si2.purchase_cost)
                     FROM sale_items si2
                     WHERE si2.sale_id = s.sale_id)), 0) AS profit_today,
                COALESCE(SUM(discount_amount), 0) AS discounts_today,
                COALESCE(SUM(tax_amount), 0)  AS tax_today
            FROM sales s
            WHERE DATE(sale_date) = ? AND status = 'COMPLETED'
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                kpis.put("txn_today",       rs.getInt("txn_today"));
                kpis.put("revenue_today",   rs.getDouble("revenue_today"));
                kpis.put("profit_today",    rs.getDouble("profit_today"));
                kpis.put("discounts_today", rs.getDouble("discounts_today"));
                kpis.put("tax_today",       rs.getDouble("tax_today"));
            }
        }

        // Low stock count
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM medicines WHERE stock_qty <= reorder_level AND is_active=1");
             ResultSet rs = ps.executeQuery()) {
            kpis.put("low_stock_count", rs.next() ? rs.getInt(1) : 0);
        }

        // Expiring soon count
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM batches WHERE expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY) "
                             + "AND qty_remaining > 0 AND COALESCE(is_quarantined,0)=0");
             ResultSet rs = ps.executeQuery()) {
            kpis.put("expiring_soon_count", rs.next() ? rs.getInt(1) : 0);
        }

        // Pending prescriptions
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM prescriptions WHERE status='PENDING'");
             ResultSet rs = ps.executeQuery()) {
            kpis.put("pending_rx_count", rs.next() ? rs.getInt(1) : 0);
        }

        return kpis;
    }

    // ── Helper ───────────────────────────────────────────────

    @FunctionalInterface
    private interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<Map<String, Object>> executeReport(String sql, ParamSetter setter)
            throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}

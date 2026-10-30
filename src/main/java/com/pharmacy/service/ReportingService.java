package com.pharmacy.service;

import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Reporting & Analytics Service.
 *
 * Reporting queries used by the report screens (profit, sales by category, stock valuation).
 */
public class ReportingService {


    // ── Profit & category reports ────────────────────────────

    /**
     * Gross profit report by date range.
     * Revenue − COGS = gross profit; includes margin percent.
     */
    public List<Map<String, Object>> getProfitReport(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT
                sales_by_day.sale_date          AS sale_date,
                sales_by_day.transactions       AS transactions,
                sales_by_day.revenue            AS revenue,
                sales_by_day.net_sales          AS net_sales,
                sales_by_day.tax_collected      AS tax_collected,
                sales_by_day.total_discounts    AS total_discounts,
                COALESCE(costs_by_day.cogs, 0)  AS cogs,
                sales_by_day.net_sales - COALESCE(costs_by_day.cogs, 0) AS gross_profit,
                ROUND(
                    (sales_by_day.net_sales - COALESCE(costs_by_day.cogs, 0))
                    / NULLIF(sales_by_day.net_sales, 0) * 100, 2
                ) AS margin_percent,
                ROUND(
                    COALESCE(costs_by_day.cogs, 0)
                    / NULLIF(sales_by_day.net_sales, 0) * 100, 2
                ) AS cogs_percent
            FROM (
                SELECT
                    DATE(s.sale_date) AS sale_date,
                    COUNT(*) AS transactions,
                    SUM(s.grand_total) AS revenue,
                    SUM(s.grand_total - s.tax_amount) AS net_sales,
                    SUM(s.tax_amount) AS tax_collected,
                    SUM(s.discount_amount) AS total_discounts
                FROM sales s
                WHERE DATE(s.sale_date) BETWEEN ? AND ?
                  AND s.status = 'COMPLETED'
                GROUP BY DATE(s.sale_date)
            ) sales_by_day
            LEFT JOIN (
                SELECT
                    DATE(s.sale_date) AS sale_date,
                    SUM(si.quantity * COALESCE(NULLIF(si.purchase_cost, 0), b.unit_cost,
                                              pb.unit_cost, p.unit_cost, m.unit_cost)) AS cogs
                FROM sales s
                JOIN sale_items si ON si.sale_id = s.sale_id
                LEFT JOIN batches b ON b.batch_id = si.batch_id
                LEFT JOIN product_batches pb ON pb.batch_id = si.product_batch_id
                LEFT JOIN products p ON p.product_id = si.product_id
                LEFT JOIN medicines m ON m.med_id = si.med_id
                WHERE DATE(s.sale_date) BETWEEN ? AND ?
                  AND s.status = 'COMPLETED'
                GROUP BY DATE(s.sale_date)
            ) costs_by_day ON costs_by_day.sale_date = sales_by_day.sale_date
            ORDER BY sales_by_day.sale_date ASC
            """;

        return executeReport(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            ps.setDate(3, Date.valueOf(from));
            ps.setDate(4, Date.valueOf(to));
        });
    }

    public List<Map<String, Object>> getSalesByCategory(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT pc.name AS category,
                   SUM(si.quantity) AS units_sold,
                   SUM(si.subtotal - si.line_discount) AS net_sales,
                   SUM(si.quantity * COALESCE(NULLIF(si.purchase_cost, 0), pb.unit_cost,
                                               b.unit_cost, p.unit_cost)) AS cogs,
                   SUM(si.subtotal - si.line_discount
                       - si.quantity * COALESCE(NULLIF(si.purchase_cost, 0), pb.unit_cost,
                                                 b.unit_cost, p.unit_cost)) AS gross_profit,
                   ROUND(SUM(si.subtotal - si.line_discount
                       - si.quantity * COALESCE(NULLIF(si.purchase_cost, 0), pb.unit_cost,
                                                 b.unit_cost, p.unit_cost))
                       / NULLIF(SUM(si.subtotal - si.line_discount), 0) * 100, 2) AS margin_percent,
                   SUM(si.tax_amount) AS tax_collected,
                   SUM(si.tax_amount + si.subtotal - si.line_discount) AS sales_including_tax
            FROM sale_items si
            JOIN sales s ON s.sale_id = si.sale_id
            JOIN products p ON p.product_id = si.product_id
            JOIN product_categories pc ON pc.category_id = p.category_id
            LEFT JOIN product_batches pb ON pb.batch_id = si.product_batch_id
            LEFT JOIN batches b ON b.batch_id = si.batch_id
            WHERE s.sale_date >= ? AND s.sale_date < ? AND s.status = 'COMPLETED'
            GROUP BY pc.category_id, pc.name
            ORDER BY net_sales DESC
            """;
        return executeReport(sql, ps -> {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        });
    }

    // ── Inventory value ──────────────────────────────────────

    public List<Map<String, Object>> getInventoryValueByCategory() throws SQLException {
        String sql = """
            SELECT
                pc.name AS category,
                COUNT(DISTINCT p.product_id) AS product_count,
                SUM(CASE WHEN p.tracks_expiry = 1
                         THEN COALESCE(lots.total_units, 0) ELSE p.stock_qty END) AS total_units,
                SUM(CASE WHEN p.tracks_expiry = 1
                         THEN COALESCE(lots.cost_value, 0) ELSE p.stock_qty * p.unit_cost END) AS cost_value,
                SUM(CASE WHEN p.tracks_expiry = 1
                         THEN COALESCE(lots.total_units, 0) * p.unit_price ELSE p.stock_qty * p.unit_price END) AS retail_value,
                SUM(CASE WHEN p.tracks_expiry = 1
                         THEN COALESCE(lots.total_units, 0) * p.unit_price - COALESCE(lots.cost_value, 0)
                         ELSE p.stock_qty * (p.unit_price - p.unit_cost) END) AS potential_profit
            FROM products p
            JOIN product_categories pc ON pc.category_id = p.category_id
            LEFT JOIN (
                SELECT product_id, SUM(total_units) AS total_units, SUM(cost_value) AS cost_value
                FROM (
                    SELECT product_id, qty_remaining AS total_units,
                           qty_remaining * unit_cost AS cost_value
                    FROM product_batches
                    WHERE qty_remaining > 0 AND expiry_date >= CURDATE() AND is_quarantined = 0
                    UNION ALL
                    SELECT legacy.product_id, b.qty_remaining,
                           b.qty_remaining * b.unit_cost
                    FROM batches b
                    JOIN products legacy ON legacy.legacy_med_id = b.med_id
                    WHERE b.qty_remaining > 0 AND b.expiry_date >= CURDATE()
                      AND COALESCE(b.is_quarantined, 0) = 0
                ) lot_rows
                GROUP BY product_id
            ) lots ON lots.product_id = p.product_id
            WHERE p.is_active = 1
            GROUP BY pc.category_id, pc.name
            ORDER BY cost_value DESC
            """;

        return executeReport(sql, ps -> {});
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

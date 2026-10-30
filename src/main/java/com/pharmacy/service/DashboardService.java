package com.pharmacy.service;

import com.pharmacy.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregation for the dashboard. Every number comes from the database
 * (sales, batches, products) through existing services; nothing is estimated or invented.
 */
public class DashboardService {

    public record Kpis(double salesToday, double salesYesterday, int salesCountToday,
                       double margin7, double marginPrev7, boolean hasMarginData,
                       double inventoryValue, double expiryRisk, double discountsToday) {}

    public record DayPoint(LocalDate date, double sales, double marginPercent) {}

    public enum Level { DANGER, WARNING, INFO, SUCCESS }

    public record Insight(Level level, String title, String body, String actionLabel, String viewKey) {}

    public record Snapshot(Kpis kpis, List<DayPoint> week, List<Insight> insights) {}

    private final ReportingService reporting = new ReportingService();
    private final InventoryService inventory = new InventoryService();

    public Snapshot load() throws SQLException {
        LocalDate today = LocalDate.now();

        double[] t = dayStats(today);
        double[] y = dayStats(today.minusDays(1));

        double[] cur = rangeTotals(today.minusDays(6), today);
        double[] prev = rangeTotals(today.minusDays(13), today.minusDays(7));
        double margin7 = cur[0] == 0 ? 0 : cur[1] / cur[0] * 100;
        double marginPrev7 = prev[0] == 0 ? 0 : prev[1] / prev[0] * 100;

        double inventoryValue = 0;
        for (Map<String, Object> r : reporting.getInventoryValueByCategory()) {
            inventoryValue += num(r.get("cost_value"));
        }

        List<InventoryService.ExpiryAlert> alerts = inventory.getExpiryAlerts();
        double risk = 0;
        for (InventoryService.ExpiryAlert a : alerts) {
            risk += a.batch().getQtyRemaining() * a.batch().getUnitCost();
        }

        Kpis kpis = new Kpis(t[0], y[0], (int) t[2], margin7, marginPrev7,
                cur[0] > 0 && prev[0] > 0, inventoryValue, risk, t[1]);

        return new Snapshot(kpis, weekPoints(today), buildInsights(kpis, alerts));
    }

    // ── Queries ──────────────────────────────────────────────

    private double[] dayStats(LocalDate day) throws SQLException {
        String sql = "SELECT COALESCE(SUM(grand_total),0), COALESCE(SUM(discount_amount),0), COUNT(*) "
                + "FROM sales WHERE sale_date >= ? AND sale_date < ? AND status = 'COMPLETED'";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(day.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getInt(3)};
            }
        }
    }

    /** [netSales, grossProfit] for a date range, from the same query the Profit report uses. */
    private double[] rangeTotals(LocalDate from, LocalDate to) throws SQLException {
        double net = 0, profit = 0;
        for (Map<String, Object> r : reporting.getProfitReport(from, to)) {
            net += num(r.get("net_sales"));
            profit += num(r.get("gross_profit"));
        }
        return new double[]{net, profit};
    }

    private List<DayPoint> weekPoints(LocalDate today) throws SQLException {
        Map<LocalDate, double[]> byDay = new HashMap<>();
        for (Map<String, Object> r : reporting.getProfitReport(today.minusDays(6), today)) {
            Object d = r.get("sale_date");
            LocalDate date = d instanceof java.sql.Date sd ? sd.toLocalDate()
                    : d instanceof LocalDate ld ? ld : LocalDate.parse(String.valueOf(d));
            byDay.put(date, new double[]{num(r.get("revenue")), num(r.get("margin_percent"))});
        }
        List<DayPoint> points = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            double[] v = byDay.getOrDefault(d, new double[]{0, 0});
            points.add(new DayPoint(d, v[0], v[1]));
        }
        return points;
    }

    // ── Insights ─────────────────────────────────────────────

    private List<Insight> buildInsights(Kpis k, List<InventoryService.ExpiryAlert> alerts) throws SQLException {
        List<Insight> list = new ArrayList<>();

        // 1. Margin trend: last 7 days vs the 7 days before
        if (!k.hasMarginData()) {
            list.add(new Insight(Level.INFO, "Margin trend",
                    "Not enough sales history yet to compare the last two weeks.", "Open Profit Report", "profit"));
        } else {
            double delta = k.margin7() - k.marginPrev7();
            if (delta <= -1.0) {
                list.add(new Insight(Level.DANGER,
                        String.format("Margin drop detected %.1f pts", delta),
                        String.format("Gross margin is %.1f%% over the last 7 days vs %.1f%% in the 7 days before.",
                                k.margin7(), k.marginPrev7()),
                        "Review Profit", "profit"));
            } else {
                list.add(new Insight(Level.SUCCESS,
                        String.format("Margin steady (%+.1f pts)", delta),
                        String.format("Gross margin is %.1f%% over the last 7 days vs %.1f%% before.",
                                k.margin7(), k.marginPrev7()),
                        "Open Profit Report", "profit"));
            }
        }

        // 2. Expiry exposure (medicine batches within the alert window, including expired)
        if (alerts.isEmpty()) {
            list.add(new Insight(Level.SUCCESS, "No expiry risk",
                    "No batches are expired or expiring inside the alert window.", "Expiry Report", "expiryreport"));
        } else {
            Map<Integer, Boolean> skus = new HashMap<>();
            InventoryService.ExpiryAlert top = alerts.get(0);
            double topValue = -1;
            for (InventoryService.ExpiryAlert a : alerts) {
                skus.put(a.batch().getMedId(), true);
                double v = a.batch().getQtyRemaining() * a.batch().getUnitCost();
                if (v > topValue) { topValue = v; top = a; }
            }
            String topName = top.batch().getMedName() == null ? "Medicine #" + top.batch().getMedId()
                    : top.batch().getMedName();
            list.add(new Insight(Level.WARNING, "Expiry risk: " + skus.size() + " medicine(s)",
                    String.format("Stock value at risk: PKR %,.0f. Largest exposure: %s.", k.expiryRisk(), topName),
                    "Open Expiry Report", "expiryreport"));
        }

        // 3. Low stock on products that actually sell
        int lowCount = 0, sellingLow = 0;
        String topName = null;
        long topSold = 0;
        String sql = "SELECT p.name, COALESCE(SUM(CASE WHEN s.sale_id IS NOT NULL THEN si.quantity ELSE 0 END),0) AS sold30 "
                + "FROM products p "
                + "LEFT JOIN sale_items si ON si.product_id = p.product_id "
                + "LEFT JOIN sales s ON s.sale_id = si.sale_id AND s.status = 'COMPLETED' "
                + "  AND s.sale_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) "
                + "WHERE p.is_active = 1 AND p.stock_qty <= p.reorder_level "
                + "GROUP BY p.product_id, p.name ORDER BY sold30 DESC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lowCount++;
                long sold = rs.getLong("sold30");
                if (sold > 0) {
                    sellingLow++;
                    if (topName == null) { topName = rs.getString("name"); topSold = sold; }
                }
            }
        }
        if (lowCount == 0) {
            list.add(new Insight(Level.SUCCESS, "Stock levels healthy",
                    "No active product is at or below its reorder level.", "Purchase Orders", "purchaseorders"));
        } else {
            String body = lowCount + " product(s) at or below reorder level"
                    + (sellingLow > 0 ? "; " + sellingLow + " sold in the last 30 days. Top: " + topName
                    + " (" + topSold + " units)." : ".");
            list.add(new Insight(Level.INFO, "Restock needed", body, "Prepare Order", "purchaseorders"));
        }
        return list;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}

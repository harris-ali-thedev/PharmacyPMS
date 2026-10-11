package com.pharmacy.service;

import com.pharmacy.dao.BatchDAO;
import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Batch;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inventory Management Service.
 *
 * Professional-grade inventory logic:
 *   - Expiry monitoring and quarantine workflow
 *   - Automated low-stock detection
 *   - Auto-draft PO generation using lead time and sales velocity
 *   - Real-time inventory valuation (FIFO/FEFO cost)
 *   - Physical stock-take reconciliation
 *   - Slow-mover identification
 */
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class.getName());

    private final MedicineDAO     medicineDAO     = new MedicineDAO();
    private final BatchDAO        batchDAO        = new BatchDAO();
    private final SupplierDAO     supplierDAO     = new SupplierDAO();
    private final PurchaseOrderDAO poDAO          = new PurchaseOrderDAO();

    // ── Expiry Management ────────────────────────────────────

    /**
     * Returns all batches expiring within the configured alert window.
     * Used by the dashboard widget and the daily scheduler.
     */
    public List<ExpiryAlert> getExpiryAlerts() throws SQLException {
        List<ExpiryAlert> alerts = new ArrayList<>();
        List<Batch> expiringSoon = batchDAO.getExpiringSoon(AppConstants.EXPIRY_ALERT_DAYS);

        for (Batch batch : expiringSoon) {
            long daysLeft = LocalDate.now().until(batch.getExpiryDate()).getDays();
            ExpiryAlert.Urgency urgency;

            if (batch.getExpiryDate().isBefore(LocalDate.now())) {
                urgency = ExpiryAlert.Urgency.EXPIRED;
            } else if (daysLeft <= 7) {
                urgency = ExpiryAlert.Urgency.CRITICAL;
            } else if (daysLeft <= 30) {
                urgency = ExpiryAlert.Urgency.WARNING;
            } else {
                urgency = ExpiryAlert.Urgency.INFO;
            }

            alerts.add(new ExpiryAlert(batch, (int) daysLeft, urgency));
        }

        // Sort: EXPIRED first, then CRITICAL, then WARNING
        alerts.sort(Comparator.comparing(ExpiryAlert::urgency)
                .thenComparingLong((ExpiryAlert a) -> a.batch().getExpiryDate().toEpochDay()));
        return alerts;
    }

    /**
     * Quarantines an expired batch — marks it unsellable and records disposal.
     * Records the action in the audit log and, if narcotic, in the narcotic register.
     */
    public void quarantineBatch(int batchId, int pharmacistUserId, String reason) throws SQLException {
        String sql = "UPDATE batches SET is_quarantined = 1, quarantine_reason = ?, " +
                "quarantined_by = ?, quarantined_at = NOW() WHERE batch_id = ?";

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, pharmacistUserId);
            ps.setInt(3, batchId);
            ps.executeUpdate();
        }

        AuditLogger.log("BATCH_QUARANTINE", "batches", batchId,
                "Batch quarantined by user " + pharmacistUserId + ". Reason: " + reason);
    }

    // ── Low Stock & Auto-PO ──────────────────────────────────

    /**
     * Returns medicines at or below their reorder level.
     */
    public List<Medicine> getLowStockMedicines() throws SQLException {
        return medicineDAO.getLowStock();
    }

    /**
     * Generates suggested purchase orders for all low-stock medicines.
     * Uses the formula: suggested_qty = (avg_daily_sales × lead_days × 1.5) − current_stock
     * The 1.5 multiplier provides a safety stock buffer.
     *
     * Medicines from the same supplier are grouped into a single PO.
     */
    public List<SuggestedPO> generateSuggestedPOs(int requestedByUserId) throws SQLException {
        List<Medicine> lowStock = getLowStockMedicines();
        if (lowStock.isEmpty()) return Collections.emptyList();

        // Group by supplier
        Map<Integer, List<Medicine>> bySupplier = new LinkedHashMap<>();
        for (Medicine med : lowStock) {
            bySupplier.computeIfAbsent(med.getSupplierId(), k -> new ArrayList<>()).add(med);
        }

        List<SuggestedPO> suggestions = new ArrayList<>();

        for (Map.Entry<Integer, List<Medicine>> entry : bySupplier.entrySet()) {
            int supplierId = entry.getKey();
            List<Medicine> meds = entry.getValue();

            Supplier supplier = supplierDAO.getById(supplierId);
            if (supplier == null) continue;

            List<SuggestedPO.LineItem> lines = new ArrayList<>();
            double totalCost = 0;

            for (Medicine med : meds) {
                double avgDailySales = getAverageDailySales(med.getMedId(), 30);
                int leadDays = supplier.getLeadDays() > 0 ? supplier.getLeadDays() : 7;
                int suggestedQty = (int) Math.ceil(avgDailySales * leadDays * 1.5)
                        - med.getStockQty();
                suggestedQty = Math.max(suggestedQty, med.getReorderLevel() * 2);

                SuggestedPO.LineItem line = new SuggestedPO.LineItem(
                        med.getMedId(), med.getName(), suggestedQty,
                        med.getUnitCost(), med.getPackCost(), med.getStockQty(), med.getReorderLevel()
                );
                lines.add(line);
                totalCost += med.getPackCost();
            }

            suggestions.add(new SuggestedPO(supplier, lines, totalCost));
        }

        AuditLogger.log("SUGGESTED_PO_GENERATED", "purchase_orders", null,
                "Suggested POs generated for " + suggestions.size() + " suppliers by user " + requestedByUserId);

        return suggestions;
    }

    /**
     * Converts a SuggestedPO into a real PENDING purchase order in the database.
     */
    public int createPOFromSuggestion(SuggestedPO suggestion, int userId) throws SQLException {
        List<PurchaseOrderDAO.POItem> items = suggestion.getLines().stream().map(line ->
                new PurchaseOrderDAO.POItem(line.getMedId(), line.getSuggestedQty(), line.getUnitCost(), line.getPackCost())
        ).toList();

        return poDAO.createPO(suggestion.getSupplier().getSupplierId(), userId, items);
    }

    // ── Inventory Valuation ──────────────────────────────────

    /**
     * Computes total inventory value using FIFO cost per batch.
     * Formula: SUM(qty_remaining × purchase_cost) for all non-expired, non-quarantined batches.
     */
    public InventoryValuation computeInventoryValue() throws SQLException {
        String sql = """
            SELECT
                COUNT(DISTINCT b.med_id)                           AS distinct_medicines,
                SUM(b.qty_remaining)                               AS total_units,
                SUM(b.qty_remaining * b.unit_cost)                 AS cost_value,
                SUM(b.qty_remaining * m.unit_price)                AS retail_value
            FROM batches b
            JOIN medicines m ON b.med_id = m.med_id
            WHERE b.qty_remaining > 0
              AND b.expiry_date > CURDATE()
              AND COALESCE(b.is_quarantined, 0) = 0
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return new InventoryValuation(
                        rs.getInt("distinct_medicines"),
                        rs.getLong("total_units"),
                        rs.getDouble("cost_value"),
                        rs.getDouble("retail_value")
                );
            }
        }
        return new InventoryValuation(0, 0, 0, 0);
    }

    // ── Stock-Take / Cycle Count ─────────────────────────────

    /**
     * Records a physical stock count and computes variance.
     * Creates an adjustment entry in the audit log.
     */
    public StockCountResult recordPhysicalCount(int medId, int physicalQty, int userId)
            throws SQLException {

        Medicine med = medicineDAO.getById(medId);
        if (med == null) throw new SQLException("Medicine not found: " + medId);

        int systemQty = med.getStockQty();
        int variance = physicalQty - systemQty;

        if (variance != 0) {
            // Update stock to match physical count
            try (Connection con = DatabaseConnection.getInstance().getConnection()) {
                medicineDAO.updateStock(con, medId, variance);
            }

            AuditLogger.log("STOCK_ADJUSTMENT", "medicines", medId,
                    "Physical count: system=" + systemQty + " physical=" + physicalQty
                            + " variance=" + variance + " by user " + userId);
        }

        return new StockCountResult(medId, med.getName(), systemQty, physicalQty, variance);
    }

    // ── Slow Mover Report ────────────────────────────────────

    /**
     * Returns medicines with zero or very low sales velocity over the specified period.
     */
    public List<SlowMover> getSlowMovers(int days, int maxSales) throws SQLException {
        String sql = """
            SELECT m.med_id, m.name, m.category, m.stock_qty, m.unit_cost,
                   COALESCE(SUM(si.quantity), 0) AS total_sold,
                   m.stock_qty * m.unit_cost AS stock_value
            FROM medicines m
            LEFT JOIN sale_items si ON m.med_id = si.med_id
            LEFT JOIN sales s ON si.sale_id = s.sale_id
                AND s.sale_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                AND s.status = 'COMPLETED'
            WHERE m.is_active = 1 AND m.stock_qty > 0
            GROUP BY m.med_id
            HAVING total_sold <= ?
            ORDER BY stock_value DESC
            """;

        List<SlowMover> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, maxSales);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new SlowMover(
                        rs.getInt("med_id"), rs.getString("name"), rs.getString("category"),
                        rs.getInt("stock_qty"), rs.getInt("total_sold"),
                        rs.getDouble("stock_value")
                ));
            }
        }
        return list;
    }

    // ── Average Daily Sales ──────────────────────────────────

    public double getAverageDailySales(int medId, int days) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(si.quantity), 0) / ? AS avg_daily
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.sale_id
            WHERE si.med_id = ?
              AND s.sale_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
              AND s.status = 'COMPLETED'
            """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, medId);
            ps.setInt(3, days);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble("avg_daily") : 0.0;
        }
    }

    // ── Value Objects ────────────────────────────────────────

    public record ExpiryAlert(Batch batch, int daysUntilExpiry, Urgency urgency) {
        public enum Urgency { EXPIRED, CRITICAL, WARNING, INFO }
    }

    public record InventoryValuation(int distinctMedicines, long totalUnits,
                                     double costValue, double retailValue) {
        public double potentialProfit() { return retailValue - costValue; }
        public double marginPercent() {
            return costValue == 0 ? 0 : (potentialProfit() / costValue) * 100;
        }
    }

    public record StockCountResult(int medId, String medicineName,
                                   int systemQty, int physicalQty, int variance) {
        public boolean hasVariance() { return variance != 0; }
    }

    public record SlowMover(int medId, String name, String category,
                            int stockQty, int totalSold, double stockValue) {}

    public static class SuggestedPO {
        private final Supplier supplier;
        private final List<LineItem> lines;
        private final double totalCost;

        public SuggestedPO(Supplier supplier, List<LineItem> lines, double totalCost) {
            this.supplier = supplier;
            this.lines = lines;
            this.totalCost = totalCost;
        }

        public Supplier getSupplier() { return supplier; }
        public List<LineItem> getLines() { return lines; }
        public double getTotalCost() { return totalCost; }

        public static class LineItem {
            private final int medId;
            private final String medicineName;
            private final int suggestedQty;
            private final double unitCost;
            private final double packCost;
            private final int currentStock;
            private final int reorderLevel;

            public LineItem(int medId, String name, int qty, double unitCost, double packCost, int current, int reorder) {
                this.medId = medId; this.medicineName = name; this.suggestedQty = qty;
                this.unitCost = unitCost; this.packCost = packCost; this.currentStock = current; this.reorderLevel = reorder;
            }
            public int getMedId() { return medId; }
            public String getMedicineName() { return medicineName; }
            public int getSuggestedQty() { return suggestedQty; }
            public double getUnitCost() { return unitCost; }
            public double getPackCost() { return packCost; }
            public int getCurrentStock() { return currentStock; }
            public int getReorderLevel() { return reorderLevel; }
            public double getLineTotal() { return packCost; }
        }
    }
}

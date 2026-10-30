package com.pharmacy.service;

import com.pharmacy.dao.BatchDAO;
import com.pharmacy.dao.ProductDAO;
import com.pharmacy.dao.PurchaseOrderDAO;
import com.pharmacy.dao.SupplierDAO;
import com.pharmacy.model.Batch;
import com.pharmacy.model.Product;
import com.pharmacy.model.Supplier;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Expiry alerts, quarantine and supplier reorder suggestions across ALL products. */
public class InventoryService {

    private final BatchDAO         batchDAO     = new BatchDAO();
    private final ProductDAO       productDAO   = new ProductDAO();
    private final SupplierDAO      supplierDAO  = new SupplierDAO();
    private final PurchaseOrderDAO poDAO        = new PurchaseOrderDAO();
    private final StockService     stockService = new StockService();

    // ── Expiry management (medicine batches) ─────────────────

    public List<ExpiryAlert> getExpiryAlerts() throws SQLException {
        return getExpiryAlerts(LocalDate.now().plusDays(AppConstants.EXPIRY_ALERT_DAYS));
    }

    /** Alerts through an inclusive calendar date. */
    public List<ExpiryAlert> getExpiryAlerts(LocalDate throughDate) throws SQLException {
        LocalDate today = LocalDate.now();
        List<ExpiryAlert> alerts = new ArrayList<>();
        for (Batch batch : batchDAO.getExpiringBy(throughDate)) {
            int daysLeft = (int) ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            ExpiryAlert.Urgency urgency;
            if (daysLeft < 0)                                    urgency = ExpiryAlert.Urgency.EXPIRED;
            else if (daysLeft <= AppConstants.EXPIRY_CRITICAL_DAYS) urgency = ExpiryAlert.Urgency.CRITICAL;
            else if (daysLeft <= 30)                             urgency = ExpiryAlert.Urgency.WARNING;
            else                                                 urgency = ExpiryAlert.Urgency.INFO;
            alerts.add(new ExpiryAlert(batch, daysLeft, urgency));
        }
        alerts.sort(Comparator.comparing(ExpiryAlert::urgency)
                .thenComparingLong((ExpiryAlert a) -> a.batch().getExpiryDate().toEpochDay()));
        return alerts;
    }

    /** Removes the batch from sellable stock AND from products.stock_qty atomically. */
    public void quarantineBatch(int batchId, int userId, String reason) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                stockService.quarantineLot(con, "MEDICINE_BATCH", batchId, reason, userId);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ── Reorder suggestions ──────────────────────────────────

    /**
     * Groups low-stock products (medicines and retail) by supplier.
     * suggested = max(avgDailySales x leadDays x 1.5 - stock, 2 x reorderLevel)
     */
    public List<SuggestedPO> generateSuggestedPOs(int requestedByUserId) throws SQLException {
        List<Product> lowStock = productDAO.getLowStock();
        if (lowStock.isEmpty()) return Collections.emptyList();

        Map<Integer, List<Product>> bySupplier = new LinkedHashMap<>();
        for (Product p : lowStock) {
            bySupplier.computeIfAbsent(p.getSupplierId(), k -> new ArrayList<>()).add(p);
        }

        List<SuggestedPO> suggestions = new ArrayList<>();
        for (Map.Entry<Integer, List<Product>> entry : bySupplier.entrySet()) {
            Supplier supplier = supplierDAO.getById(entry.getKey());
            if (supplier == null) continue;
            int leadDays = supplier.getLeadDays() > 0 ? supplier.getLeadDays() : 7;

            List<SuggestedPO.LineItem> lines = new ArrayList<>();
            double totalCost = 0;
            for (Product p : entry.getValue()) {
                double avgDaily = getAverageDailySales(p.getProductId(), 30);
                int qty = (int) Math.ceil(avgDaily * leadDays * 1.5) - p.getStockQty();
                qty = Math.max(qty, p.getReorderLevel() * 2);
                SuggestedPO.LineItem line = new SuggestedPO.LineItem(p.getProductId(), p.getName(), qty,
                        p.getUnitCost(), p.getUnitCost(), p.getStockQty(), p.getReorderLevel());
                lines.add(line);
                totalCost += line.getLineTotal();
            }
            suggestions.add(new SuggestedPO(supplier, lines, totalCost));
        }

        AuditLogger.log("SUGGESTED_PO_GENERATED", "purchase_orders", null,
                "Suggested POs generated for " + suggestions.size() + " suppliers by user " + requestedByUserId);
        return suggestions;
    }

    public int createPOFromSuggestion(SuggestedPO suggestion, int userId) throws SQLException {
        List<PurchaseOrderDAO.POItem> items = suggestion.getLines().stream().map(line ->
                new PurchaseOrderDAO.POItem(line.getProductId(), line.getProductName(), line.getSuggestedQty(),
                        line.getUnitCost(), line.getPackCost())).toList();
        return poDAO.createPO(suggestion.getSupplier().getSupplierId(), userId, items);
    }

    private double getAverageDailySales(int productId, int days) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(si.quantity), 0) / ? AS avg_daily
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.sale_id
            WHERE si.product_id = ?
              AND s.sale_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
              AND s.status = 'COMPLETED'
            """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, productId);
            ps.setInt(3, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("avg_daily") : 0.0;
            }
        }
    }

    // ── Value objects ────────────────────────────────────────

    public record ExpiryAlert(Batch batch, int daysUntilExpiry, Urgency urgency) {
        public enum Urgency { EXPIRED, CRITICAL, WARNING, INFO }
    }

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
            private final int productId;
            private final String productName;
            private final int suggestedQty;
            private final double unitCost;
            private final double packCost;
            private final int currentStock;
            private final int reorderLevel;

            public LineItem(int productId, String productName, int qty, double unitCost, double packCost,
                            int current, int reorder) {
                this.productId = productId; this.productName = productName; this.suggestedQty = qty;
                this.unitCost = unitCost; this.packCost = packCost;
                this.currentStock = current; this.reorderLevel = reorder;
            }
            public int getProductId() { return productId; }
            public String getProductName() { return productName; }
            public int getSuggestedQty() { return suggestedQty; }
            public double getUnitCost() { return unitCost; }
            public double getPackCost() { return packCost; }
            public int getCurrentStock() { return currentStock; }
            public int getReorderLevel() { return reorderLevel; }
            public double getLineTotal() { return suggestedQty * packCost; }
        }
    }
}

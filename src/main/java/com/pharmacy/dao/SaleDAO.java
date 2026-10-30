package com.pharmacy.dao;

import com.pharmacy.model.*;
import com.pharmacy.service.StockService;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class SaleDAO {

    private final BatchDAO batchDAO       = new BatchDAO();
    private final ProductDAO productDAO   = new ProductDAO();
    private final ProductBatchDAO productBatchDAO = new ProductBatchDAO();
    private final StockService stockService = new StockService();

    /**
     * Persists a sale inside the caller's transaction: header, lines, FIFO lot deduction,
     * products.stock_qty and the stock ledger all succeed or fail together.
     */
    public int saveSale(Connection con, Sale sale) throws SQLException {
        String saleSql = "INSERT INTO sales (user_id, patient_id, session_id, prescription_id, sale_date, subtotal, " +
                "discount_type, discount_value, discount_amount, tax_percent, tax_amount, grand_total, " +
                "amount_paid, change_returned, payment_method, status) VALUES (?,?,?,?,NOW(),?,?,?,?,?,?,?,?,?,?,?)";

        String itemSql = "INSERT INTO sale_items (sale_id, med_id, product_id, batch_id, product_batch_id, "
                + "quantity, unit_price, purchase_cost, subtotal, line_discount, tax_percent, tax_amount) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        int saleId;
        try (PreparedStatement ps = con.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sale.getUserId());
            ps.setObject(2, sale.getPatientId() > 0 ? sale.getPatientId() : null, Types.INTEGER);
            ps.setObject(3, sale.getSessionId(), Types.INTEGER);
            ps.setObject(4, sale.getPrescriptionId(), Types.INTEGER);
            ps.setBigDecimal(5, decimal(sale.getSubtotal()));
            ps.setString(6, sale.getDiscountType());
            ps.setBigDecimal(7, decimal(sale.getDiscountValue()));
            ps.setBigDecimal(8, decimal(sale.getDiscountAmount()));
            ps.setBigDecimal(9, decimal(sale.getTaxPercent()));
            ps.setBigDecimal(10, decimal(sale.getTaxAmount()));
            ps.setBigDecimal(11, decimal(sale.getGrandTotal()));
            ps.setBigDecimal(12, decimal(sale.getAmountPaid()));
            ps.setBigDecimal(13, decimal(sale.getChangeReturned()));
            ps.setString(14, sale.getPaymentMethod());
            ps.setString(15, "COMPLETED");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Sale record was not created.");
                saleId = keys.getInt(1);
            }
        }

        try (PreparedStatement ps = con.prepareStatement(itemSql)) {
            for (SaleItem item : sale.getItems()) {
                Product product = productDAO.getById(con, item.getProductId(), true);
                if (product == null || !product.isActive()) {
                    throw new SQLException("Product is unavailable (product ID " + item.getProductId() + ").");
                }
                if (product.isMedicine()) {
                    List<Batch> batches = batchDAO.getAvailableBatches(con, product.getMedicineId(), true);
                    if (batches.stream().mapToInt(Batch::getQtyRemaining).sum() < item.getQuantity()) {
                        throw new SQLException("No non-expired batch has enough stock for " + product.getName());
                    }
                    int remaining = item.getQuantity();
                    double remainingDiscount = item.getLineDiscount();
                    double remainingTax = item.getTaxAmount();
                    for (Batch batch : batches) {
                        if (remaining == 0) break;
                        int allocated = Math.min(remaining, batch.getQtyRemaining());
                        double splitDiscount = allocated == remaining
                                ? remainingDiscount : allocate(item.getLineDiscount(), allocated, item.getQuantity());
                        double splitTax = allocated == remaining
                                ? remainingTax : allocate(item.getTaxAmount(), allocated, item.getQuantity());
                        batchDAO.deductFromBatch(con, batch.getBatchId(), allocated);
                        insertSaleLine(ps, saleId, item, allocated, batch.getUnitCost(), batch.getBatchId(),
                                null, splitDiscount, splitTax);
                        remaining -= allocated;
                        remainingDiscount -= splitDiscount;
                        remainingTax -= splitTax;
                        productDAO.updateStock(con, product.getProductId(), -allocated);
                        stockService.recordSaleMovement(con, product.getProductId(), allocated,
                                "MEDICINE_BATCH", batch.getBatchId(), saleId);
                    }
                } else {
                    if (product.isTracksExpiry()) {
                        List<ProductBatchDAO.ProductBatch> batches = productBatchDAO.getAvailable(con,
                                product.getProductId(), true);
                        if (batches.stream().mapToInt(ProductBatchDAO.ProductBatch::quantity).sum()
                                < item.getQuantity()) {
                            throw new SQLException("No non-expired batch has enough stock for " + product.getName());
                        }
                        int remaining = item.getQuantity();
                        double remainingDiscount = item.getLineDiscount();
                        double remainingTax = item.getTaxAmount();
                        for (ProductBatchDAO.ProductBatch batch : batches) {
                            if (remaining == 0) break;
                            int allocated = Math.min(remaining, batch.quantity());
                            double splitDiscount = allocated == remaining
                                    ? remainingDiscount : allocate(item.getLineDiscount(), allocated, item.getQuantity());
                            double splitTax = allocated == remaining
                                    ? remainingTax : allocate(item.getTaxAmount(), allocated, item.getQuantity());
                            productBatchDAO.deduct(con, batch.batchId(), allocated);
                            insertSaleLine(ps, saleId, item, allocated, batch.unitCost(), null,
                                    batch.batchId(), splitDiscount, splitTax);
                            remaining -= allocated;
                            remainingDiscount -= splitDiscount;
                            remainingTax -= splitTax;
                            productDAO.updateStock(con, product.getProductId(), -allocated);
                            stockService.recordSaleMovement(con, product.getProductId(), allocated,
                                    "PRODUCT_BATCH", batch.batchId(), saleId);
                        }
                    } else {
                        insertSaleLine(ps, saleId, item, item.getQuantity(), product.getUnitCost(),
                                null, null, item.getLineDiscount(), item.getTaxAmount());
                        productDAO.updateStock(con, product.getProductId(), -item.getQuantity());
                        stockService.recordSaleMovement(con, product.getProductId(), item.getQuantity(),
                                "NONE", null, saleId);
                    }
                }
            }
            ps.executeBatch();
        }
        return saleId;
    }

    private void insertSaleLine(PreparedStatement ps, int saleId, SaleItem item, int quantity,
                                double purchaseCost, Integer batchId, Integer productBatchId,
                                double lineDiscount, double taxAmount) throws SQLException {
        ps.setInt(1, saleId);
        ps.setObject(2, item.getMedId() > 0 ? item.getMedId() : null, Types.INTEGER);
        ps.setInt(3, item.getProductId());
        ps.setObject(4, batchId, Types.INTEGER);
        ps.setObject(5, productBatchId, Types.INTEGER);
        ps.setInt(6, quantity);
        ps.setBigDecimal(7, decimal(item.getUnitPrice()));
        ps.setBigDecimal(8, decimal(purchaseCost));
        ps.setBigDecimal(9, decimal(item.getUnitPrice()).multiply(java.math.BigDecimal.valueOf(quantity)));
        ps.setBigDecimal(10, decimal(lineDiscount));
        ps.setBigDecimal(11, decimal(item.getTaxPercent()));
        ps.setBigDecimal(12, decimal(taxAmount));
        ps.addBatch();
    }

    private static java.math.BigDecimal decimal(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private static double allocate(double amount, int units, int quantity) {
        return decimal(amount).multiply(java.math.BigDecimal.valueOf(units))
                .divide(java.math.BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.DOWN).doubleValue();
    }

    /** Daily sales total and count */
    public Map<String, Object> getDailyStats(LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) AS txn_count, COALESCE(SUM(grand_total),0) AS revenue, " +
                     "COALESCE(SUM(grand_total - tax_amount),0) AS net " +
                     "FROM sales WHERE DATE(sale_date) = ? AND status='COMPLETED'";
        return fetchStats(sql, Date.valueOf(date));
    }

    /** Weekly sales grouped by day of week */
    public List<Map<String, Object>> getWeeklyBreakdown(LocalDate weekStart) throws SQLException {
        String sql = "SELECT DATE(sale_date) AS day, COALESCE(SUM(grand_total),0) AS revenue, COUNT(*) AS txn " +
                     "FROM sales WHERE sale_date >= ? AND sale_date < ? AND status='COMPLETED' " +
                     "GROUP BY DATE(sale_date) ORDER BY day";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(weekStart));
            ps.setDate(2, Date.valueOf(weekStart.plusDays(7)));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day", rs.getDate("day").toLocalDate());
                row.put("revenue", rs.getDouble("revenue"));
                row.put("txn", rs.getInt("txn"));
                rows.add(row);
            }
        }
        return rows;
    }

    /** Best-selling products (all categories) by units sold in the period. */
    public List<Map<String, Object>> getTopProducts(LocalDate startDate, LocalDate endDate, int limit) throws SQLException {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("A valid date range is required");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        String sql = "SELECT p.name, COALESCE(SUM(si.quantity), 0) AS qty_sold FROM products p "
                + "JOIN sale_items si ON si.product_id = p.product_id "
                + "JOIN sales s ON s.sale_id = si.sale_id AND s.status = 'COMPLETED' "
                + "AND s.sale_date >= ? AND s.sale_date < ? "
                + "WHERE p.is_active = 1 GROUP BY p.product_id, p.name ORDER BY qty_sold DESC LIMIT ?";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(startDate.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("qty", rs.getInt("qty_sold"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Recent sales list for the sales report table */
    public List<Sale> getRecentSales(int limit) throws SQLException {
        List<Sale> list = new ArrayList<>();
        String sql = "SELECT s.*, u.full_name AS cashier_name, pt.full_name AS patient_name FROM sales s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "LEFT JOIN patients pt ON pt.patient_id = s.patient_id " +
                     "ORDER BY s.sale_date DESC LIMIT ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapSaleRow(rs));
        }
        return list;
    }

    public List<SalesBucket> getSalesTrend(LocalDate from, LocalDate to, boolean groupByMonth) throws SQLException {
        String periodExpression = groupByMonth
                ? "DATE_FORMAT(s.sale_date, '%Y-%m')"
                : "DATE_FORMAT(s.sale_date, '%Y-%m-%d')";
        String sql = "SELECT " + periodExpression + " AS period, " +
                "COALESCE(SUM(s.grand_total), 0) AS revenue, COUNT(*) AS transactions " +
                "FROM sales s WHERE s.sale_date >= ? AND s.sale_date < ? AND s.status = 'COMPLETED' " +
                "GROUP BY period ORDER BY period";
        List<SalesBucket> buckets = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    buckets.add(new SalesBucket(
                            rs.getString("period"),
                            rs.getDouble("revenue"),
                            rs.getInt("transactions")
                    ));
                }
            }
        }
        return buckets;
    }

    public List<PaymentBreakdown> getPaymentBreakdown(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(payment_method, 'UNKNOWN') AS payment_method, " +
                "COALESCE(SUM(grand_total), 0) AS revenue " +
                "FROM sales WHERE sale_date >= ? AND sale_date < ? AND status = 'COMPLETED' " +
                "GROUP BY payment_method ORDER BY revenue DESC";
        List<PaymentBreakdown> breakdown = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    breakdown.add(new PaymentBreakdown(
                            rs.getString("payment_method"),
                            rs.getDouble("revenue")
                    ));
                }
            }
        }
        return breakdown;
    }

    public List<Sale> getSalesBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT s.*, u.full_name AS cashier_name, pt.full_name AS patient_name FROM sales s " +
                "JOIN users u ON s.user_id = u.user_id " +
                "LEFT JOIN patients pt ON pt.patient_id = s.patient_id " +
                "WHERE s.sale_date >= ? AND s.sale_date < ? AND s.status = 'COMPLETED' " +
                "ORDER BY s.sale_date DESC";
        List<Sale> sales = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSaleRow(rs));
                }
            }
        }
        return sales;
    }

    public record SalesBucket(String period, double revenue, int transactions) {}

    public record PaymentBreakdown(String paymentMethod, double revenue) {}

    public List<SaleItem> getItemsBySale(int saleId) throws SQLException {
        List<SaleItem> list = new ArrayList<>();
        String sql = "SELECT si.*, COALESCE(p.name, m.name, CONCAT('Product #', si.product_id)) AS med_name "
                + "FROM sale_items si LEFT JOIN products p ON p.product_id = si.product_id "
                + "LEFT JOIN medicines m ON m.med_id = si.med_id WHERE si.sale_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SaleItem item = new SaleItem();
                item.setSaleItemId(rs.getInt("sale_item_id"));
                item.setSaleId(saleId);
                item.setMedId(rs.getInt("med_id"));
                item.setProductId(rs.getInt("product_id"));
                item.setBatchId((Integer) rs.getObject("batch_id"));
                item.setProductBatchId((Integer) rs.getObject("product_batch_id"));
                item.setQuantity(rs.getInt("quantity"));
                item.setUnitPrice(rs.getDouble("unit_price"));
                item.setPurchaseCost(rs.getDouble("purchase_cost"));
                item.setSubtotal(rs.getDouble("subtotal"));
                item.setLineDiscount(rs.getDouble("line_discount"));
                item.setTaxPercent(rs.getDouble("tax_percent"));
                item.setTaxAmount(rs.getDouble("tax_amount"));
                item.setMedicineName(rs.getString("med_name"));
                list.add(item);
            }
        }
        return list;
    }

    // ─── helpers ──────────────────────────────────────────────
    private Map<String, Object> fetchStats(String sql, Date date) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, date);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("txn_count", rs.getInt("txn_count"));
                map.put("revenue",   rs.getDouble("revenue"));
                return map;
            }
        }
        return Collections.emptyMap();
    }

    private static boolean hasColumn(ResultSet rs, String name) {
        try { rs.findColumn(name); return true; } catch (SQLException e) { return false; }
    }

    private Sale mapSaleRow(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setSaleId(rs.getInt("sale_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setCashierName(rs.getString("cashier_name"));
        if (hasColumn(rs, "patient_name")) s.setPatientName(rs.getString("patient_name"));
        s.setSubtotal(rs.getDouble("subtotal"));
        s.setDiscountAmount(rs.getDouble("discount_amount"));
        s.setTaxAmount(rs.getDouble("tax_amount"));
        s.setGrandTotal(rs.getDouble("grand_total"));
        s.setAmountPaid(rs.getDouble("amount_paid"));
        s.setChangeReturned(rs.getDouble("change_returned"));
        s.setPaymentMethod(rs.getString("payment_method"));
        s.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("sale_date");
        if (ts != null) s.setSaleDate(ts.toLocalDateTime());
        return s;
    }
}

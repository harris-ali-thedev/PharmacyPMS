package com.pharmacy.dao;

import com.pharmacy.model.*;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class SaleDAO {

    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BatchDAO batchDAO       = new BatchDAO();

    /**
     * Persists a complete sale atomically:
     *  1. Inserts Sale header row
     *  2. Inserts each SaleItem row
     *  3. Deducts from Batch (FIFO) and Medicine.stock_qty
     * All steps share one Connection in a transaction.
     */
    public int saveSale(Sale sale) throws SQLException {
        String saleSql = "INSERT INTO sales (user_id, prescription_id, sale_date, subtotal, discount_type, " +
                "discount_value, discount_amount, tax_percent, tax_amount, grand_total, " +
                "amount_paid, change_returned, payment_method, status) VALUES (?,?,NOW(),?,?,?,?,?,?,?,?,?,?,?)";

        String itemSql = "INSERT INTO sale_items (sale_id, med_id, batch_id, quantity, unit_price, subtotal) " +
                "VALUES (?,?,?,?,?,?)";

        Connection con = DatabaseConnection.getInstance().getConnection();
        con.setAutoCommit(false);

        try {
            // 1. Insert sale header
            int saleId;
            try (PreparedStatement ps = con.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, sale.getUserId());
                ps.setObject(2, sale.getPrescriptionId(), Types.INTEGER);
                ps.setDouble(3, sale.getSubtotal());
                ps.setString(4, sale.getDiscountType());
                ps.setDouble(5, sale.getDiscountValue());
                ps.setDouble(6, sale.getDiscountAmount());
                ps.setDouble(7, sale.getTaxPercent());
                ps.setDouble(8, sale.getTaxAmount());
                ps.setDouble(9, sale.getGrandTotal());
                ps.setDouble(10, sale.getAmountPaid());
                ps.setDouble(11, sale.getChangeReturned());
                ps.setString(12, sale.getPaymentMethod());
                ps.setString(13, "COMPLETED");
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                saleId = keys.getInt(1);
            }

            // 2. Insert items + deduct stock
            try (PreparedStatement ps = con.prepareStatement(itemSql)) {
                for (SaleItem item : sale.getItems()) {
                    // FIFO batch resolution
                    Batch batch = batchDAO.getOldestAvailableBatch(item.getMedId(), item.getQuantity());
                    Integer batchId = null;
                    if (batch != null) {
                        batchDAO.deductFromBatch(con, batch.getBatchId(), item.getQuantity());
                        batchId = batch.getBatchId();
                    }

                    ps.setInt(1, saleId);
                    ps.setInt(2, item.getMedId());
                    ps.setObject(3, batchId, Types.INTEGER);
                    ps.setInt(4, item.getQuantity());
                    ps.setDouble(5, item.getUnitPrice());
                    ps.setDouble(6, item.getSubtotal());
                    ps.addBatch();

                    // Deduct from medicines.stock_qty
                    medicineDAO.updateStock(con, item.getMedId(), -item.getQuantity());
                }
                ps.executeBatch();
            }

            con.commit();
            return saleId;

        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    /** Daily sales total and count */
    public Map<String, Object> getDailyStats(LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) AS txn_count, COALESCE(SUM(grand_total),0) AS revenue, " +
                     "COALESCE(SUM(grand_total - discount_amount - tax_amount),0) AS net " +
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

    /** Monthly revenue + profit calculation */
    public Map<String, Object> getMonthlyStats(int year, int month) throws SQLException {
        String sql = "SELECT COALESCE(SUM(s.grand_total),0) AS revenue, " +
                     "COALESCE(SUM(si.quantity * si.unit_price),0) AS sale_val, " +
                     "COUNT(DISTINCT s.sale_id) AS txn_count " +
                     "FROM sales s JOIN sale_items si ON s.sale_id = si.sale_id " +
                     "WHERE YEAR(s.sale_date)=? AND MONTH(s.sale_date)=? AND s.status='COMPLETED'";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("revenue",   rs.getDouble("revenue"));
                map.put("txn_count", rs.getInt("txn_count"));
                return map;
            }
        }
        return Collections.emptyMap();
    }

    /** Top N medicines by quantity sold today */
    public List<Map<String, Object>> getTopMedicines(LocalDate startDate, LocalDate endDate, int limit) throws SQLException {
        // Validation
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }

        String sql = "SELECT m.name, COALESCE(SUM(si.quantity), 0) AS qty_sold " +
                "FROM medicines m " +
                "LEFT JOIN sale_items si ON m.med_id = si.med_id " +
                "LEFT JOIN sales s ON si.sale_id = s.sale_id " +
                "    AND DATE(s.sale_date) BETWEEN ? AND ? " +
                "    AND s.status = 'COMPLETED' " +
                "GROUP BY m.med_id " +
                "ORDER BY qty_sold DESC " +
                "LIMIT ?";

        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(startDate));
            ps.setDate(2, Date.valueOf(endDate));
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
        String sql = "SELECT s.*, u.full_name AS cashier_name FROM sales s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "ORDER BY s.sale_date DESC LIMIT ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapSaleRow(rs));
        }
        return list;
    }

    public List<SaleItem> getItemsBySale(int saleId) throws SQLException {
        List<SaleItem> list = new ArrayList<>();
        String sql = "SELECT si.*, m.name AS med_name FROM sale_items si " +
                     "JOIN medicines m ON si.med_id = m.med_id WHERE si.sale_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SaleItem item = new SaleItem();
                item.setSaleItemId(rs.getInt("sale_item_id"));
                item.setSaleId(saleId);
                item.setMedId(rs.getInt("med_id"));
                item.setQuantity(rs.getInt("quantity"));
                item.setUnitPrice(rs.getDouble("unit_price"));
                item.setSubtotal(rs.getDouble("subtotal"));
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

    private Sale mapSaleRow(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setSaleId(rs.getInt("sale_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setCashierName(rs.getString("cashier_name"));
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

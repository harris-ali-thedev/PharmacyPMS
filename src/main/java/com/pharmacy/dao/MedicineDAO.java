package com.pharmacy.dao;

import com.pharmacy.model.Medicine;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Clinical master data for medicines. Stock quantity is NOT written here:
 * it is owned by {@code StockService} (products.stock_qty) and mirrored into
 * medicines.stock_qty by a database trigger.
 */
public class MedicineDAO {

    private static final String SELECT = "SELECT m.*, s.name AS supplier_name FROM medicines m "
            + "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id ";

    public List<Medicine> getAll() throws SQLException {
        return query(SELECT + "WHERE m.is_active = 1 ORDER BY m.name", ps -> {});
    }

    public List<Medicine> search(String keyword) throws SQLException {
        String like = "%" + keyword + "%";
        return query(SELECT + "WHERE m.is_active = 1 AND "
                + "(m.name LIKE ? OR m.generic_name LIKE ? OR m.barcode LIKE ? OR m.category LIKE ?) "
                + "ORDER BY m.name", ps -> {
            for (int i = 1; i <= 4; i++) ps.setString(i, like);
        });
    }

    public List<Medicine> getLowStock() throws SQLException {
        return query(SELECT + "WHERE m.is_active = 1 AND m.stock_qty <= m.reorder_level ORDER BY m.stock_qty", ps -> {});
    }

    /** Creates the medicine with zero stock; opening stock is received through StockService. */
    public int insert(Medicine m) throws SQLException {
        String sql = "INSERT INTO medicines (name, generic_name, category, barcode, unit_price, unit_cost, pack_cost, "
                + "reorder_level, is_narcotic, is_scheduled, supplier_id, stock_qty) VALUES (?,?,?,?,?,?,?,?,?,?,?,0)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            con.setAutoCommit(false);
            try {
                setParams(ps, m);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int medId = keys.getInt(1);
                        syncProduct(con, m, medId);
                        con.commit();
                        return medId;
                    }
                }
                con.rollback();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
        return -1;
    }

    public void update(Medicine m) throws SQLException {
        String sql = "UPDATE medicines SET name=?, generic_name=?, category=?, barcode=?, unit_price=?, "
                + "unit_cost=?, pack_cost=?, reorder_level=?, is_narcotic=?, is_scheduled=?, "
                + "supplier_id=? WHERE med_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            try {
                setParams(ps, m);
                ps.setInt(12, m.getMedId());
                ps.executeUpdate();
                syncProduct(con, m, m.getMedId());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void delete(int medId) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement med = con.prepareStatement(
                         "UPDATE medicines SET is_active = 0, updated_at = NOW() WHERE med_id = ?");
                 PreparedStatement product = con.prepareStatement(
                         "UPDATE products SET is_active = 0 WHERE legacy_med_id = ?")) {
                med.setInt(1, medId);
                med.executeUpdate();
                product.setInt(1, medId);
                product.executeUpdate();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Mirrors catalogue fields (never stock) into the unified products table. */
    private void syncProduct(Connection con, Medicine m, int medId) throws SQLException {
        boolean exists;
        try (PreparedStatement find = con.prepareStatement("SELECT 1 FROM products WHERE legacy_med_id = ?")) {
            find.setInt(1, medId);
            try (ResultSet rs = find.executeQuery()) { exists = rs.next(); }
        }
        boolean rx = m.isScheduled() || m.isNarcotics();
        if (exists) {
            String sql = """
                    UPDATE products p
                    JOIN product_categories pc ON pc.code = 'MEDICINE'
                    SET p.category_id = pc.category_id, p.name = ?, p.barcode = ?, p.unit_price = ?,
                        p.unit_cost = ?, p.reorder_level = ?, p.tracks_expiry = 1,
                        p.requires_prescription = ?, p.supplier_id = ?, p.is_active = 1
                    WHERE p.legacy_med_id = ?
                    """;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, m.getName());
                ps.setString(2, m.getBarcode());
                ps.setDouble(3, m.getUnitPrice());
                ps.setDouble(4, m.getUnitCost());
                ps.setInt(5, m.getReorderLevel());
                ps.setBoolean(6, rx);
                setSupplier(ps, 7, m);
                ps.setInt(8, medId);
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    INSERT INTO products
                        (legacy_med_id, category_id, name, barcode, unit_price, unit_cost, stock_qty,
                         reorder_level, tracks_expiry, requires_prescription, supplier_id, is_active)
                    SELECT ?, category_id, ?, ?, ?, ?, 0, ?, 1, ?, ?, 1
                    FROM product_categories WHERE code = 'MEDICINE'
                    """;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, medId);
                ps.setString(2, m.getName());
                ps.setString(3, m.getBarcode());
                ps.setDouble(4, m.getUnitPrice());
                ps.setDouble(5, m.getUnitCost());
                ps.setInt(6, m.getReorderLevel());
                ps.setBoolean(7, rx);
                setSupplier(ps, 8, m);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Medicine category is missing from the shared catalog.");
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────
    @FunctionalInterface
    interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<Medicine> query(String sql, ParamSetter setter) throws SQLException {
        List<Medicine> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private void setParams(PreparedStatement ps, Medicine m) throws SQLException {
        ps.setString(1, m.getName());
        ps.setString(2, m.getGenericName());
        ps.setString(3, m.getCategory());
        ps.setString(4, blankToNull(m.getBarcode()));
        ps.setDouble(5, m.getUnitPrice());
        ps.setDouble(6, m.getUnitCost());
        ps.setDouble(7, m.getPackCost());
        ps.setInt(8, m.getReorderLevel());
        ps.setBoolean(9, m.isNarcotics());
        ps.setBoolean(10, m.isScheduled());
        setSupplier(ps, 11, m);
    }

    private static void setSupplier(PreparedStatement ps, int index, Medicine m) throws SQLException {
        if (m.getSupplierId() > 0) ps.setInt(index, m.getSupplierId());
        else ps.setNull(index, Types.INTEGER);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private Medicine mapRow(ResultSet rs) throws SQLException {
        Medicine m = new Medicine();
        m.setMedId(rs.getInt("med_id"));
        m.setName(rs.getString("name"));
        m.setGenericName(rs.getString("generic_name"));
        m.setCategory(rs.getString("category"));
        m.setBarcode(rs.getString("barcode"));
        m.setUnitPrice(rs.getDouble("unit_price"));
        m.setUnitCost(rs.getDouble("unit_cost"));
        m.setPackCost(rs.getDouble("pack_cost"));
        m.setStockQty(rs.getInt("stock_qty"));
        m.setReorderLevel(rs.getInt("reorder_level"));
        m.setNarcotics(rs.getBoolean("is_narcotic"));
        m.setScheduled(rs.getBoolean("is_scheduled"));
        m.setSupplierId(rs.getInt("supplier_id"));
        m.setSupplierName(rs.getString("supplier_name"));
        return m;
    }
}

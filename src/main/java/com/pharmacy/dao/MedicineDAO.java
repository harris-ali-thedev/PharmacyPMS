package com.pharmacy.dao;

import com.pharmacy.model.Medicine;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SqlDialectInspection")
public class MedicineDAO {

    public List<Medicine> getAll() throws SQLException {
        return query("SELECT m.*, s.name AS supplier_name FROM medicines m " +
                     "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id " +
                     "ORDER BY m.name", ps -> {});
    }

    public List<Medicine> search(String keyword) throws SQLException {
        String like = "%" + keyword + "%";
        return query("SELECT m.*, s.name AS supplier_name FROM medicines m " +
                     "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id " +
                     "WHERE m.name LIKE ? OR m.generic_name LIKE ? OR m.barcode LIKE ? OR m.category LIKE ? " +
                     "ORDER BY m.name",
                ps -> {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    ps.setString(4, like);
                });
    }

    public List<Medicine> getLowStock() throws SQLException {
        return query("SELECT m.*, s.name AS supplier_name FROM medicines m " +
                     "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id " +
                     "WHERE m.stock_qty <= m.reorder_level ORDER BY m.stock_qty", ps -> {});
    }

    public Medicine getById(int medId) throws SQLException {
        List<Medicine> list = query(
                "SELECT m.*, s.name AS supplier_name FROM medicines m " +
                "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id WHERE m.med_id = ?",
                ps -> ps.setInt(1, medId));
        return list.isEmpty() ? null : list.get(0);
    }

    public Medicine getByBarcode(String barcode) throws SQLException {
        List<Medicine> list = query(
                "SELECT m.*, s.name AS supplier_name FROM medicines m " +
                "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id WHERE m.barcode = ?",
                ps -> ps.setString(1, barcode));
        return list.isEmpty() ? null : list.get(0);
    }

    public int insert(Medicine m) throws SQLException {
        String sql = "INSERT INTO medicines (name, generic_name, category, barcode, unit_price, unit_cost, pack_cost, " +
                     "stock_qty, reorder_level, is_narcotic, supplier_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, m);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public void update(Medicine m) throws SQLException {
        String sql = "UPDATE medicines SET name=?, generic_name=?, category=?, barcode=?, unit_price=?, " +
                     "unit_cost=?, pack_cost=?, stock_qty=?, reorder_level=?, is_narcotic=?, supplier_id=? WHERE med_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setParams(ps, m);
            ps.setInt(12, m.getMedId());
            ps.executeUpdate();
        }
    }

    public void updateStock(Connection con, int medId, int delta) throws SQLException {
        String sql = "UPDATE medicines SET stock_qty = stock_qty + ? WHERE med_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, medId);
            ps.executeUpdate();
        }
    }

    public void delete(int medId) throws SQLException {
        String sql = "DELETE FROM medicines WHERE med_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            ps.executeUpdate();
        }
    }

    // ─── helpers ──────────────────────────────────────────────
    @FunctionalInterface
    interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<Medicine> query(String sql, ParamSetter setter) throws SQLException {
        List<Medicine> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    private void setParams(PreparedStatement ps, Medicine m) throws SQLException {
        ps.setString(1, m.getName());
        ps.setString(2, m.getGenericName());
        ps.setString(3, m.getCategory());
        ps.setString(4, m.getBarcode());
        ps.setDouble(5, m.getUnitPrice());
        ps.setDouble(6, m.getUnitCost());
        ps.setDouble(7, m.getPackCost());
        ps.setInt(8, m.getStockQty());
        ps.setInt(9, m.getReorderLevel());
        ps.setBoolean(10, m.isNarcotics());
        if (m.getSupplierId() > 0) ps.setInt(11, m.getSupplierId());
        else ps.setNull(11, Types.INTEGER);
    }

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
        m.setSupplierId(rs.getInt("supplier_id"));
        m.setSupplierName(rs.getString("supplier_name"));
        return m;
    }
}

package com.pharmacy.dao;

import com.pharmacy.model.Supplier;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fixed SupplierDAO.
 *
 * Fixes vs v1:
 *   - isDuplicate() had invalid SQL ("SELECT * FROM suppliers DISTINCT email")
 *     — now uses a correct parameterized query
 *   - getAll() had a dangling ALTER TABLE statement inside the results loop (!)
 *   - Added getById() method required by InventoryService
 *   - Soft delete instead of hard DELETE to preserve PO history
 *   - Added supplier performance stats
 */
public class SupplierDAO {

    private static final Logger LOG = Logger.getLogger(SupplierDAO.class.getName());

    public List<Supplier> getAll() throws SQLException {
        // FIXED: removed the rogue ALTER TABLE statement that was here in v1
        String sql = "SELECT * FROM suppliers WHERE is_active = 1 ORDER BY name";
        List<Supplier> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Supplier getById(int supplierId) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE supplier_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    public int insert(Supplier s) throws SQLException {
        // FIXED: check duplicate before insert with correct SQL
        if (isDuplicate(s)) {
            throw new SQLException("A supplier with email '" + s.getEmail() + "' already exists.");
        }

        String sql = "INSERT INTO suppliers (name, contact_name, phone, email, address, " +
                "lead_days, is_active) VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, s);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public void update(Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET name=?, contact_name=?, phone=?, email=?, " +
                "address=?, lead_days=?, is_active=?, updated_at=NOW() " +
                "WHERE supplier_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setParams(ps, s);
            ps.setInt(8, s.getSupplierId());
            ps.executeUpdate();
        }
    }

    /**
     * FIXED: was "SELECT * FROM suppliers DISTINCT email" — invalid SQL.
     * Now correctly checks for duplicate email excluding the supplier being edited.
     */
    public boolean isDuplicate(Supplier s) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suppliers WHERE email = ? AND supplier_id != ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getEmail() != null ? s.getEmail().trim() : "");
            ps.setInt(2, s.getSupplierId());  // 0 for new suppliers — excludes nothing
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Soft delete — sets is_active=0 instead of hard DELETE.
     * Preserves referential integrity with purchase_orders.
     */
    public boolean softDelete(int supplierId) throws SQLException {
        String sql = "UPDATE suppliers SET is_active=0, updated_at=NOW() WHERE supplier_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Supplier purchase history summary */
    public List<java.util.Map<String, Object>> getSupplierPurchaseHistory(int supplierId)
            throws SQLException {
        String sql = """
            SELECT po.po_id, po.order_date, po.status, po.total_cost,
                   COUNT(pi.po_item_id) AS item_count
            FROM purchase_orders po
            LEFT JOIN po_items pi ON po.po_id = pi.po_id
            WHERE po.supplier_id = ?
            GROUP BY po.po_id
            ORDER BY po.order_date DESC
            LIMIT 20
            """;

        List<java.util.Map<String, Object>> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("po_id", rs.getInt("po_id"));
                row.put("order_date", rs.getTimestamp("order_date"));
                row.put("status", rs.getString("status"));
                row.put("total_cost", rs.getDouble("total_cost"));
                row.put("item_count", rs.getInt("item_count"));
                list.add(row);
            }
        }
        return list;
    }

    private void setParams(PreparedStatement ps, Supplier s) throws SQLException {
        ps.setString(1, s.getName());
        ps.setString(2, s.getContactName());
        ps.setString(3, s.getPhone());
        ps.setString(4, s.getEmail());
        ps.setString(5, s.getAddress());
        ps.setInt(6, s.getLeadDays());
        ps.setBoolean(7, s.isActive());
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setName(rs.getString("name"));
        s.setContactName(rs.getString("contact_name"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setLeadDays(rs.getInt("lead_days"));
        s.setActive(rs.getBoolean("is_active"));
        return s;
    }
}

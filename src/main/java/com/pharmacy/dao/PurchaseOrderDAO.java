package com.pharmacy.dao;

import com.pharmacy.model.Medicine;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.*;

/**
 * Handles Purchase Order creation and stock receipt workflow.
 * When a PO is marked RECEIVED, medicine stock and batch records are updated.
 */
public class PurchaseOrderDAO {

    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BatchDAO batchDAO       = new BatchDAO();

    public int createPO(int supplierId, int createdByUserId, List<POItem> items) throws SQLException {
        String poSql   = "INSERT INTO purchase_orders (supplier_id, created_by, status, total_cost) VALUES (?,?,'PENDING',?)";
        String itemSql = "INSERT INTO po_items (po_id, med_id, qty_ordered, unit_cost, pack_cost) VALUES (?,?,?,?,?)";

        double total = items.stream().mapToDouble(i -> i.packCost).sum();

        Connection con = DatabaseConnection.getInstance().getConnection();
        con.setAutoCommit(false);
        try {
            int poId;
            try (PreparedStatement ps = con.prepareStatement(poSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, supplierId);
                ps.setInt(2, createdByUserId);
                ps.setDouble(3, total);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                poId = keys.getInt(1);
            }
            try (PreparedStatement ps = con.prepareStatement(itemSql)) {
                for (POItem item : items) {
                    ps.setInt(1, poId);
                    ps.setInt(2, item.medId);
                    ps.setInt(3, item.qtyOrdered);
                    ps.setDouble(4, item.unitCost);
                    ps.setDouble(5, item.packCost);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            con.commit();
            return poId;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    /**
     * Marks a PO as RECEIVED and updates stock + batches.
     */
    public void receivePO(int poId, java.time.LocalDate expiryDate) throws SQLException {
        String updatePO  = "UPDATE purchase_orders SET status='RECEIVED' WHERE po_id=? AND status IN ('PENDING','ORDERED')";
        String fetchItems = "SELECT * FROM po_items WHERE po_id=?";

        Connection con = DatabaseConnection.getInstance().getConnection();
        con.setAutoCommit(false);
        try {
            // Mark PO received
            try (PreparedStatement ps = con.prepareStatement(updatePO)) {
                ps.setInt(1, poId);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("Only active, unreceived purchase orders can be received.");
                }
            }

            // Process each PO item
            try (PreparedStatement ps = con.prepareStatement(fetchItems)) {
                ps.setInt(1, poId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int medId    = rs.getInt("med_id");
                    int qty      = rs.getInt("qty_ordered");
                    double unitCost  = rs.getDouble("unit_cost");
                    double packCost  = rs.getDouble("pack_cost");

                    // Add batch record
                    com.pharmacy.model.Batch batch = new com.pharmacy.model.Batch();
                    batch.setMedId(medId);
                    batch.setBatchNumber("PO" + poId + "-" + medId);
                    batch.setQtyReceived(qty);
                    batch.setQtyRemaining(qty);
                    batch.setExpiryDate(expiryDate);
                    batch.setUnitCost(unitCost);
                    batch.setPackCost(packCost);
                    batchDAO.insert(batch);

                    // Increase medicine stock
                    medicineDAO.updateStock(con, medId, qty);
                }
            }
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    public List<Map<String, Object>> getAllPOs() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT po.*, s.name AS supplier_name, u.full_name AS created_by_name, " +
                     "(SELECT COUNT(*) FROM po_items pi WHERE pi.po_id = po.po_id) AS item_count " +
                     "FROM purchase_orders po " +
                     "JOIN suppliers s ON po.supplier_id = s.supplier_id " +
                     "JOIN users u ON po.created_by = u.user_id " +
                     "ORDER BY po.order_date DESC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("po_id",           rs.getInt("po_id"));
                row.put("supplier_id",     rs.getInt("supplier_id"));
                row.put("supplier_name",   rs.getString("supplier_name"));
                row.put("created_by_name", rs.getString("created_by_name"));
                row.put("order_date",      rs.getTimestamp("order_date"));
                row.put("status",          rs.getString("status"));
                row.put("total_cost",      rs.getDouble("total_cost"));
                row.put("item_count",      rs.getInt("item_count"));
                list.add(row);
            }
        }
        return list;
    }

    // ─── DTO ─────────────────────────────────────────────────
    public List<POItem> getPOItems(int poId) throws SQLException {
        List<POItem> items = new ArrayList<>();
        String sql = "SELECT pi.*, m.name AS medicine_name FROM po_items pi " +
                     "JOIN medicines m ON pi.med_id = m.med_id " +
                     "WHERE pi.po_id = ? ORDER BY m.name";

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new POItem(
                            rs.getInt("med_id"),
                            rs.getString("medicine_name"),
                            rs.getInt("qty_ordered"),
                            rs.getDouble("unit_cost"),
                            rs.getDouble("pack_cost")
                    ));
                }
            }
        }
        return items;
    }

    public void cancelPO(int poId) throws SQLException {
        String sql = "UPDATE purchase_orders SET status='CANCELLED' WHERE po_id=? AND status NOT IN ('RECEIVED','CANCELLED')";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, poId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Only unreceived purchase orders can be cancelled.");
            }
        }
    }

    public List<Medicine> getMedicinesBySupplier(int supplierId) throws SQLException {
        List<Medicine> medicines = new ArrayList<>();
        String sql = "SELECT m.*, s.name AS supplier_name FROM medicines m " +
                     "LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id " +
                     "WHERE m.supplier_id = ? " +
                     "ORDER BY m.name";

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    medicines.add(mapMedicine(rs));
                }
            }
        }
        return medicines;
    }

    private Medicine mapMedicine(ResultSet rs) throws SQLException {
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

    public static class POItem {
        public int medId;
        public String medicineName;
        public int qtyOrdered;
        public double unitCost;
        public double packCost;

        public POItem(int medId, int qtyOrdered, double unitCost, double packCost) {
            this(medId, null, qtyOrdered, unitCost, packCost);
        }

        public POItem(int medId, String medicineName, int qtyOrdered, double unitCost, double packCost) {
            this.medId = medId;
            this.medicineName = medicineName;
            this.qtyOrdered = qtyOrdered;
            this.unitCost = unitCost;
            this.packCost = packCost;
        }
    }
}

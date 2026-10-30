package com.pharmacy.dao;



import com.pharmacy.service.StockService;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Purchase orders for ANY product (medicines and retail). Receiving a PO goes through
 * {@link StockService} so lots, stock, ledger and narcotic register stay consistent.
 */
public class PurchaseOrderDAO {

    private final StockService stockService = new StockService();

    public int createPO(int supplierId, int createdByUserId, List<POItem> items) throws SQLException {
        String poSql = "INSERT INTO purchase_orders (supplier_id, created_by, status, total_cost) VALUES (?,?,'PENDING',?)";
        String itemSql = "INSERT INTO po_items (po_id, med_id, product_id, qty_ordered, unit_cost, pack_cost) "
                + "SELECT ?, p.legacy_med_id, p.product_id, ?, ?, ? FROM products p WHERE p.product_id = ?";

        double total = items.stream().mapToDouble(i -> i.qtyOrdered * i.packCost).sum();

        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                int poId;
                try (PreparedStatement ps = con.prepareStatement(poSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, supplierId);
                    ps.setInt(2, createdByUserId);
                    ps.setDouble(3, total);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Purchase order was not created.");
                        poId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(itemSql)) {
                    for (POItem item : items) {
                        ps.setInt(1, poId);
                        ps.setInt(2, item.qtyOrdered);
                        ps.setDouble(3, item.unitCost);
                        ps.setDouble(4, item.packCost);
                        ps.setInt(5, item.productId);
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("Unknown product ID " + item.productId);
                        }
                    }
                }
                con.commit();
                return poId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Marks a PO as RECEIVED and receives every line through StockService in one transaction.
     * {@code expiryDate} is applied only to lines whose product tracks expiry.
     */
    public void receivePO(int poId, LocalDate expiryDate) throws SQLException {
        String fetchItems = "SELECT pi.po_item_id, pi.product_id, pi.qty_ordered, pi.unit_cost, pi.pack_cost, "
                + "p.tracks_expiry FROM po_items pi JOIN products p ON p.product_id = pi.product_id WHERE pi.po_id = ?";

        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE purchase_orders SET status='RECEIVED', received_date=CURDATE() "
                                + "WHERE po_id=? AND status IN ('PENDING','ORDERED')")) {
                    ps.setInt(1, poId);
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Only active, unreceived purchase orders can be received.");
                    }
                }

                List<StockService.ReceiveRequest> requests = new ArrayList<>();
                List<Integer> lineIds = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(fetchItems)) {
                    ps.setInt(1, poId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int productId = rs.getInt("product_id");
                            lineIds.add(rs.getInt("po_item_id"));
                            requests.add(new StockService.ReceiveRequest(productId, rs.getInt("qty_ordered"),
                                    rs.getDouble("unit_cost"), rs.getDouble("pack_cost"),
                                    "PO" + poId + "-" + productId,
                                    rs.getBoolean("tracks_expiry") ? expiryDate : null,
                                    "PURCHASE_ORDER", poId, null));
                        }
                    }
                }
                if (requests.isEmpty()) throw new SQLException("Purchase order has no items.");

                try (PreparedStatement mark = con.prepareStatement(
                        "UPDATE po_items SET qty_received = qty_ordered, expiry_date = ?, batch_number = ? "
                                + "WHERE po_item_id = ?")) {
                    for (int i = 0; i < requests.size(); i++) {
                        StockService.ReceiveRequest r = requests.get(i);
                        stockService.receive(con, r);
                        mark.setObject(1, r.expiryDate() == null ? null : java.sql.Date.valueOf(r.expiryDate()), Types.DATE);
                        mark.setString(2, r.batchNumber());
                        mark.setInt(3, lineIds.get(i));
                        mark.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
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

    public List<POItem> getPOItems(int poId) throws SQLException {
        List<POItem> items = new ArrayList<>();
        String sql = "SELECT pi.*, COALESCE(p.name, m.name) AS item_name FROM po_items pi "
                + "LEFT JOIN products p ON p.product_id = pi.product_id "
                + "LEFT JOIN medicines m ON m.med_id = pi.med_id "
                + "WHERE pi.po_id = ? ORDER BY item_name";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new POItem(rs.getInt("product_id"), rs.getString("item_name"),
                            rs.getInt("qty_ordered"), rs.getDouble("unit_cost"), rs.getDouble("pack_cost")));
                }
            }
        }
        return items;
    }

    /** One purchase-order line; works for medicines and retail products alike. */
    public static class POItem {
        public final int productId;
        public final String productName;
        public final int qtyOrdered;
        public final double unitCost;
        public final double packCost;

        public POItem(int productId, String productName, int qtyOrdered, double unitCost, double packCost) {
            this.productId = productId;
            this.productName = productName;
            this.qtyOrdered = qtyOrdered;
            this.unitCost = unitCost;
            this.packCost = packCost;
        }
    }
}

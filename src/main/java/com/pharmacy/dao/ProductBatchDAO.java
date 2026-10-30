package com.pharmacy.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Lot tracking for expiring non-medicine products. */
public class ProductBatchDAO {

    public record ProductBatch(int batchId, String batchNumber, int quantity, double unitCost) {}

    public List<ProductBatch> getAvailable(Connection con, int productId, boolean lock) throws SQLException {
        String sql = "SELECT batch_id, batch_number, qty_remaining, unit_cost FROM product_batches "
                + "WHERE product_id = ? AND qty_remaining > 0 AND expiry_date >= CURRENT_DATE "
                + "AND is_quarantined = 0 ORDER BY expiry_date, batch_id" + (lock ? " FOR UPDATE" : "");
        List<ProductBatch> batches = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    batches.add(new ProductBatch(rs.getInt("batch_id"), rs.getString("batch_number"),
                            rs.getInt("qty_remaining"), rs.getDouble("unit_cost")));
                }
            }
        }
        return batches;
    }

    public void deduct(Connection con, int batchId, int quantity) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE product_batches SET qty_remaining = qty_remaining - ? "
                        + "WHERE batch_id = ? AND qty_remaining >= ? AND expiry_date >= CURRENT_DATE")) {
            ps.setInt(1, quantity);
            ps.setInt(2, batchId);
            ps.setInt(3, quantity);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Insufficient or expired product batch stock (batch ID " + batchId + ").");
            }
        }
    }

    public int insert(Connection con, int productId, String batchNumber, int quantity, LocalDate expiryDate,
                      double unitCost) throws SQLException {
        if (expiryDate == null) {
            throw new IllegalArgumentException("An expiry date is required for a tracked product batch.");
        }
        String sql = "INSERT INTO product_batches (product_id, batch_number, qty_received, qty_remaining, "
                + "expiry_date, unit_cost) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, productId);
            ps.setString(2, batchNumber);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.setDate(5, java.sql.Date.valueOf(expiryDate));
            ps.setDouble(6, unitCost);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Product batch could not be created.");
    }
}

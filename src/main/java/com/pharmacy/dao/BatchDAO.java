package com.pharmacy.dao;

import com.pharmacy.model.Batch;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BatchDAO {

    public List<Batch> getByMedicine(int medId) throws SQLException {
        List<Batch> list = new ArrayList<>();
        String sql = "SELECT * FROM batches WHERE med_id = ? ORDER BY expiry_date ASC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** Returns batches expiring within the given number of days (across all medicines). */
    public List<Batch> getExpiringSoon(int days) throws SQLException {
        List<Batch> list = new ArrayList<>();
        String sql = "SELECT b.*, m.name AS med_name FROM batches b " +
                     "JOIN medicines m ON b.med_id = m.med_id " +
                     "WHERE b.expiry_date <= ? AND b.qty_remaining > 0 ORDER BY b.expiry_date ASC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(LocalDate.now().plusDays(days)));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Batch b = mapRow(rs);
                // attach medicine name via transient field (reuse batchNumber slot trick avoided – use proper field)
                b.setBatchNumber(rs.getString("batch_number")); // already set in mapRow
                list.add(b);
            }
        }
        return list;
    }

    public int insert(Batch b) throws SQLException {
        String sql = "INSERT INTO batches (med_id, batch_number, qty_received, qty_remaining, expiry_date, unit_cost, pack_cost) " +
                     "VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, b.getMedId());
            ps.setString(2, b.getBatchNumber());
            ps.setInt(3, b.getQtyReceived());
            ps.setInt(4, b.getQtyRemaining());
            ps.setDate(5, Date.valueOf(b.getExpiryDate()));
            ps.setDouble(6, b.getUnitCost());
            ps.setDouble(7, b.getPackCost());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public void deductFromBatch(Connection con, int batchId, int qty) throws SQLException {
        String sql = "UPDATE batches SET qty_remaining = qty_remaining - ? WHERE batch_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, batchId);
            ps.executeUpdate();
        }
    }

    /** Returns the oldest non-expired batch with sufficient quantity (FIFO). */
    public Batch getOldestAvailableBatch(int medId, int qtyNeeded) throws SQLException {
        String sql = "SELECT * FROM batches WHERE med_id = ? AND qty_remaining >= ? " +
                     "AND expiry_date > CURDATE() ORDER BY expiry_date ASC LIMIT 1";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            ps.setInt(2, qtyNeeded);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    private Batch mapRow(ResultSet rs) throws SQLException {
        Batch b = new Batch();
        b.setBatchId(rs.getInt("batch_id"));
        b.setMedId(rs.getInt("med_id"));
        b.setBatchNumber(rs.getString("batch_number"));
        b.setQtyReceived(rs.getInt("qty_received"));
        b.setQtyRemaining(rs.getInt("qty_remaining"));
        b.setExpiryDate(rs.getDate("expiry_date").toLocalDate());
        b.setUnitCost(rs.getDouble("unit_cost"));
        b.setPackCost(rs.getDouble("pack_cost"));
        Timestamp ts = rs.getTimestamp("received_at");
        if (ts != null) b.setReceivedAt(ts.toLocalDateTime());
        return b;
    }
}

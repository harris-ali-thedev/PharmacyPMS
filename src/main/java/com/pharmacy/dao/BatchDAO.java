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
        return getExpiringBy(LocalDate.now().plusDays(days));
    }

    /** Returns batches expiring on or before the selected date (across all medicines). */
    public List<Batch> getExpiringBy(LocalDate throughDate) throws SQLException {
        List<Batch> list = new ArrayList<>();
        String sql = "SELECT b.*, m.name AS med_name FROM batches b " +
                     "JOIN medicines m ON b.med_id = m.med_id " +
                     "WHERE b.expiry_date <= ? AND b.qty_remaining > 0 ORDER BY b.expiry_date ASC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(throughDate));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Batch b = mapRow(rs);
                b.setMedName(rs.getString("med_name"));
                list.add(b);
            }
        }
        return list;
    }

    /** Inserts a batch inside the caller's transaction. */
    public int insert(Connection con, Batch b) throws SQLException {
        String sql = "INSERT INTO batches (med_id, batch_number, qty_received, qty_remaining, expiry_date, unit_cost, pack_cost) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, b.getMedId());
            ps.setString(2, b.getBatchNumber());
            ps.setInt(3, b.getQtyReceived());
            ps.setInt(4, b.getQtyRemaining());
            ps.setDate(5, Date.valueOf(b.getExpiryDate()));
            ps.setDouble(6, b.getUnitCost());
            ps.setDouble(7, b.getPackCost());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Batch could not be created.");
    }

    public void deductFromBatch(Connection con, int batchId, int qty) throws SQLException {
        String sql = "UPDATE batches SET qty_remaining = qty_remaining - ? " +
                "WHERE batch_id = ? AND qty_remaining >= ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, batchId);
            ps.setInt(3, qty);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insufficient batch stock for batch ID " + batchId);
            }
        }
    }

    public List<Batch> getAvailableBatches(Connection con, int medId, boolean lockForUpdate) throws SQLException {
        String sql = "SELECT * FROM batches WHERE med_id = ? AND qty_remaining > 0 "
                + "AND expiry_date >= CURDATE() AND COALESCE(is_quarantined, 0) = 0 "
                + "ORDER BY expiry_date ASC, batch_id ASC" + (lockForUpdate ? " FOR UPDATE" : "");
        List<Batch> batches = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) batches.add(mapRow(rs));
            }
        }
        return batches;
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

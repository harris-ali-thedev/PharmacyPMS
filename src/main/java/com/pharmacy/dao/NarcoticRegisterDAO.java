package com.pharmacy.dao;

import com.pharmacy.model.NarcoticRegisterEntry;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the Narcotic Dispensing Register.
 * Maintains a running balance for each narcotic medicine
 * and provides the government-format register report.
 */
public class NarcoticRegisterDAO {

    /**
     * Inserts a register entry and computes the running balance atomically.
     * Must be called within the same transaction as the sale.
     */
    public int insert(Connection con, NarcoticRegisterEntry entry) throws SQLException {
        // Compute the new running balance
        int lastBalance = getLastBalance(con, entry.getMedId());
        int newBalance = lastBalance + entry.getQtyIn() - entry.getQtyOut();

        if (newBalance < 0) {
            throw new SQLException("Narcotic balance would go negative for med_id=" + entry.getMedId()
                    + ". Current balance: " + lastBalance + ", qty_out: " + entry.getQtyOut());
        }

        entry.setRunningBalance(newBalance);

        String sql = """
            INSERT INTO narcotic_register (
                med_id, medicine_name, batch_number, transaction_type,
                qty_in, qty_out, running_balance,
                prescription_id, patient_id, patient_name, patient_nic,
                doctor_name, doctor_reg_no,
                sale_id, witness_name, witness_designation,
                dispensed_by, dispensed_by_name, notes, entry_date
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, entry.getMedId());
            ps.setString(2, entry.getMedicineName());
            ps.setString(3, entry.getBatchNumber());
            ps.setString(4, entry.getTransactionType());
            ps.setInt(5, entry.getQtyIn());
            ps.setInt(6, entry.getQtyOut());
            ps.setInt(7, newBalance);
            ps.setObject(8, entry.getPrescriptionId(), Types.INTEGER);
            ps.setObject(9, entry.getPatientId(), Types.INTEGER);
            ps.setString(10, entry.getPatientName());
            ps.setString(11, entry.getPatientNic());
            ps.setString(12, entry.getDoctorName());
            ps.setString(13, entry.getDoctorRegNo());
            ps.setObject(14, entry.getSaleId(), Types.INTEGER);
            ps.setString(15, entry.getWitnessName());
            ps.setString(16, entry.getWitnessDesignation());
            ps.setInt(17, entry.getDispensedBy());
            ps.setString(18, entry.getDispensedByName());
            ps.setString(19, entry.getNotes());
            ps.setTimestamp(20, Timestamp.valueOf(entry.getEntryDate()));
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    /** Returns the current running balance for a narcotic medicine */
    public int getCurrentBalance(int medId) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            return getLastBalance(con, medId);
        }
    }

    private int getLastBalance(Connection con, int medId) throws SQLException {
        String sql = "SELECT running_balance FROM narcotic_register " +
                "WHERE med_id = ? ORDER BY entry_id DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("running_balance") : 0;
        }
    }

    /** Returns register entries for a date range — the government report format */
    public List<NarcoticRegisterEntry> getRegisterByDateRange(
            int medId, LocalDate from, LocalDate to) throws SQLException {

        String sql = """
            SELECT nr.*, u.full_name AS dispensed_by_name
            FROM narcotic_register nr
            LEFT JOIN users u ON nr.dispensed_by = u.user_id
            WHERE nr.med_id = ? AND DATE(nr.entry_date) BETWEEN ? AND ?
            ORDER BY nr.entry_date ASC
            """;

        List<NarcoticRegisterEntry> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** All narcotic entries for a specific patient */
    public List<NarcoticRegisterEntry> getByPatient(int patientId) throws SQLException {
        String sql = "SELECT * FROM narcotic_register WHERE patient_id = ? ORDER BY entry_date DESC";
        List<NarcoticRegisterEntry> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    private NarcoticRegisterEntry mapRow(ResultSet rs) throws SQLException {
        NarcoticRegisterEntry e = new NarcoticRegisterEntry();
        e.setEntryId(rs.getInt("entry_id"));
        e.setMedId(rs.getInt("med_id"));
        e.setMedicineName(rs.getString("medicine_name"));
        e.setBatchNumber(rs.getString("batch_number"));
        e.setTransactionType(rs.getString("transaction_type"));
        e.setQtyIn(rs.getInt("qty_in"));
        e.setQtyOut(rs.getInt("qty_out"));
        e.setRunningBalance(rs.getInt("running_balance"));
        int pid = rs.getInt("prescription_id");
        if (!rs.wasNull()) e.setPrescriptionId(pid);
        int patId = rs.getInt("patient_id");
        if (!rs.wasNull()) e.setPatientId(patId);
        e.setPatientName(rs.getString("patient_name"));
        e.setPatientNic(rs.getString("patient_nic"));
        e.setDoctorName(rs.getString("doctor_name"));
        e.setDoctorRegNo(rs.getString("doctor_reg_no"));
        int saleId = rs.getInt("sale_id");
        if (!rs.wasNull()) e.setSaleId(saleId);
        e.setWitnessName(rs.getString("witness_name"));
        e.setWitnessDesignation(rs.getString("witness_designation"));
        e.setDispensedBy(rs.getInt("dispensed_by"));
        e.setDispensedByName(rs.getString("dispensed_by_name"));
        e.setNotes(rs.getString("notes"));
        Timestamp ts = rs.getTimestamp("entry_date");
        if (ts != null) e.setEntryDate(ts.toLocalDateTime());
        return e;
    }
}

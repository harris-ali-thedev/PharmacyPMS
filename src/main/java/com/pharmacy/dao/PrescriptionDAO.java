package com.pharmacy.dao;

import com.pharmacy.model.Prescription;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionDAO {

    public List<Prescription> getAll() throws SQLException {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name AS verified_by_name FROM prescriptions p " +
                     "LEFT JOIN users u ON p.verified_by = u.user_id ORDER BY p.created_at DESC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Prescription getById(int id) throws SQLException {
        String sql = "SELECT p.*, u.full_name AS verified_by_name FROM prescriptions p " +
                     "LEFT JOIN users u ON p.verified_by = u.user_id WHERE p.prescription_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public int insert(Prescription p) throws SQLException {
        String sql = "INSERT INTO prescriptions (patient_name, doctor_name, doctor_reg_no, image_path, notes, issued_date, verified_by) " +
                     "VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getPatientName());
            ps.setString(2, p.getDoctorName());
            ps.setString(3, p.getDoctorRegNo());
            ps.setString(4, p.getImagePath());
            ps.setString(5, p.getNotes());
            ps.setObject(6, p.getIssuedDate() != null ? Date.valueOf(p.getIssuedDate()) : null, Types.DATE);
            ps.setObject(7, p.getVerifiedBy(), Types.INTEGER);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public void verify(int prescriptionId, int pharmacistId) throws SQLException {
        String sql = "UPDATE prescriptions SET verified_by=? WHERE prescription_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, pharmacistId);
            ps.setInt(2, prescriptionId);
            ps.executeUpdate();
        }
    }

    private Prescription mapRow(ResultSet rs) throws SQLException {
        Prescription p = new Prescription();
        p.setPrescriptionId(rs.getInt("prescription_id"));
        p.setPatientName(rs.getString("patient_name"));
        p.setDoctorName(rs.getString("doctor_name"));
        p.setDoctorRegNo(rs.getString("doctor_reg_no"));
        p.setImagePath(rs.getString("image_path"));
        p.setNotes(rs.getString("notes"));
        Date issued = rs.getDate("issued_date");
        if (issued != null) p.setIssuedDate(issued.toLocalDate());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) p.setCreatedAt(created.toLocalDateTime());
        int vb = rs.getInt("verified_by");
        if (!rs.wasNull()) p.setVerifiedBy(vb);
        p.setVerifiedByName(rs.getString("verified_by_name"));
        return p;
    }
}

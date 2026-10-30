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
            for (Prescription p : list) loadItems(con, p);
        }
        return list;
    }

    /**
     * Dispensable prescriptions for a patient: not cancelled/dispensed/expired,
     * and not past expiry_date when that column is set.
     */
    public List<Prescription> getByPatientId(int patientId) throws SQLException {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name AS verified_by_name FROM prescriptions p " +
                     "LEFT JOIN users u ON p.verified_by = u.user_id " +
                     "WHERE p.patient_id = ? " +
                     "  AND (p.status IS NULL OR p.status IN ('PENDING','VERIFIED','PARTIAL')) " +
                     "  AND (p.expiry_date IS NULL OR p.expiry_date >= CURDATE()) " +
                     "ORDER BY p.issued_date DESC, p.created_at DESC";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            for (Prescription p : list) loadItems(con, p);
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
            if (rs.next()) {
                Prescription p = mapRow(rs);
                rs.close();
                loadItems(con, p);
                return p;
            }
        }
        return null;
    }

    public int insert(Prescription p) throws SQLException {
        String sql = "INSERT INTO prescriptions (prescription_number, patient_id, patient_name, patient_nic, "
                + "doctor_name, doctor_reg_no, doctor_phone, clinic_name, issued_date, expiry_date, validity_days, "
                + "max_refills, diagnosis, notes, image_path, status, created_by) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            con.setAutoCommit(false);
            try {
            ps.setString(1, p.getPrescriptionNumber() == null || p.getPrescriptionNumber().isBlank()
                    ? null : p.getPrescriptionNumber().trim());
            ps.setObject(2, p.getPatientId() > 0 ? p.getPatientId() : null, Types.INTEGER);
            ps.setString(3, p.getPatientName());
            ps.setString(4, p.getPatientNic());
            ps.setString(5, p.getDoctorName());
            ps.setString(6, p.getDoctorRegNo());
            ps.setString(7, p.getDoctorPhone());
            ps.setString(8, p.getClinicName());
            ps.setObject(9, p.getIssuedDate() != null ? Date.valueOf(p.getIssuedDate()) : null, Types.DATE);
            ps.setObject(10, p.getExpiryDate() != null ? Date.valueOf(p.getExpiryDate()) : null, Types.DATE);
            ps.setInt(11, p.getValidityDays());
            ps.setInt(12, p.getMaxRefills());
            ps.setString(13, p.getDiagnosis());
            ps.setString(14, p.getNotes());
            ps.setString(15, p.getImagePath());
            ps.setString(16, p.getStatus());
            ps.setObject(17, p.getCreatedBy(), Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    if (p.getPrescriptionNumber() == null || p.getPrescriptionNumber().isBlank()) {
                        try (PreparedStatement num = con.prepareStatement(
                                "UPDATE prescriptions SET prescription_number = ? WHERE prescription_id = ?")) {
                            num.setString(1, String.format("RX-%d-%06d", java.time.Year.now().getValue(), id));
                            num.setInt(2, id);
                            num.executeUpdate();
                        }
                    }
                    saveItems(con, id, p.getItems());
                    con.commit();
                    p.setPrescriptionId(id);
                    return id;
                }
            }
            throw new SQLException("No prescription ID was generated");
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private void saveItems(Connection con, int id, List<Prescription.PrescriptionItem> items) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO prescription_items "
                + "(prescription_id,med_id,medicine_name,dosage,frequency,duration,qty_prescribed,is_scheduled,is_narcotic,instructions) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (var item : items) {
                ps.setInt(1, id); ps.setInt(2, item.getMedId()); ps.setString(3, item.getMedicineName());
                ps.setString(4, item.getDosage()); ps.setString(5, item.getFrequency()); ps.setString(6, item.getDuration());
                ps.setInt(7, item.getQtyPrescribed()); ps.setBoolean(8, item.isScheduled());
                ps.setBoolean(9, item.isNarcotic()); ps.setString(10, item.getInstructions()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadItems(Connection con, Prescription p) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM prescription_items WHERE prescription_id=? ORDER BY item_id")) {
            ps.setInt(1, p.getPrescriptionId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var item = new Prescription.PrescriptionItem();
                    item.setItemId(rs.getInt("item_id")); item.setMedId(rs.getInt("med_id"));
                    item.setMedicineName(rs.getString("medicine_name")); item.setDosage(rs.getString("dosage"));
                    item.setFrequency(rs.getString("frequency")); item.setDuration(rs.getString("duration"));
                    item.setQtyPrescribed(rs.getInt("qty_prescribed")); item.setQtyDispensed(rs.getInt("qty_dispensed"));
                    item.setScheduled(rs.getBoolean("is_scheduled")); item.setNarcotic(rs.getBoolean("is_narcotic"));
                    item.setInstructions(rs.getString("instructions")); p.getItems().add(item);
                }
            }
        }
    }

    public void verify(int prescriptionId, int pharmacistId) throws SQLException {
        String sql = "UPDATE prescriptions SET verified_by=?, status='VERIFIED', verified_at=NOW() WHERE prescription_id=?";
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

        // Extended schema columns (schema.sql / schema_pro.sql)
        if (hasColumn(rs, "prescription_number")) {
            p.setPrescriptionNumber(rs.getString("prescription_number"));
        }
        if (hasColumn(rs, "patient_id")) {
            int pid = rs.getInt("patient_id");
            if (!rs.wasNull()) p.setPatientId(pid);
        }
        if (hasColumn(rs, "patient_nic")) {
            p.setPatientNic(rs.getString("patient_nic"));
        }
        if (hasColumn(rs, "doctor_phone")) {
            p.setDoctorPhone(rs.getString("doctor_phone"));
        }
        if (hasColumn(rs, "clinic_name")) {
            p.setClinicName(rs.getString("clinic_name"));
        }
        if (hasColumn(rs, "expiry_date")) {
            Date expiry = rs.getDate("expiry_date");
            if (expiry != null) p.setExpiryDate(expiry.toLocalDate());
        }
        if (hasColumn(rs, "validity_days")) {
            p.setValidityDays(rs.getInt("validity_days"));
        }
        if (hasColumn(rs, "max_refills")) {
            p.setMaxRefills(rs.getInt("max_refills"));
        }
        if (hasColumn(rs, "refills_used")) {
            p.setRefillsUsed(rs.getInt("refills_used"));
        }
        if (hasColumn(rs, "diagnosis")) {
            p.setDiagnosis(rs.getString("diagnosis"));
        }
        if (hasColumn(rs, "status")) {
            String status = rs.getString("status");
            if (status != null) p.setStatus(status);
        }
        if (hasColumn(rs, "e_prescription")) {
            p.setEPrescription(rs.getBoolean("e_prescription"));
        }
        if (hasColumn(rs, "verified_at")) {
            Timestamp vat = rs.getTimestamp("verified_at");
            if (vat != null) p.setVerifiedAt(vat.toLocalDateTime());
        }
        if (hasColumn(rs, "created_by")) {
            int cb = rs.getInt("created_by");
            if (!rs.wasNull()) p.setCreatedBy(cb);
        }
        if (hasColumn(rs, "updated_at")) {
            Timestamp upd = rs.getTimestamp("updated_at");
            if (upd != null) p.setUpdatedAt(upd.toLocalDateTime());
        }

        return p;
    }

    private static boolean hasColumn(ResultSet rs, String name) {
        try {
            rs.findColumn(name);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}

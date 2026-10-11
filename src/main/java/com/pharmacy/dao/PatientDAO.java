package com.pharmacy.dao;

import com.pharmacy.model.Patient;
import com.pharmacy.model.enums.BloodGroup;
import com.pharmacy.model.enums.Gender;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data access layer for Patient entity.
 * Includes search, allergy management, medication history,
 * and loyalty points tracking.
 */
public class PatientDAO {

    private static final Logger LOG = Logger.getLogger(PatientDAO.class.getName());

    // ── CRUD ─────────────────────────────────────────────────

    public List<Patient> getAll() throws SQLException {
        String sql = "SELECT * FROM patients WHERE is_active = 1 ORDER BY last_name, first_name";
        return executeQuery(sql, ps -> {});
    }

    public Patient getById(int patientId) throws SQLException {
        String sql = "SELECT * FROM patients WHERE patient_id = ?";
        List<Patient> list = executeQuery(sql, ps -> ps.setInt(1, patientId));
        return list.isEmpty() ? null : list.get(0);
    }

    public Patient getByCode(String patientCode) throws SQLException {
        String sql = "SELECT * FROM patients WHERE patient_code = ?";
        List<Patient> list = executeQuery(sql, ps -> ps.setString(1, patientCode));
        return list.isEmpty() ? null : list.get(0);
    }

    public Patient getByPhone(String phone) throws SQLException {
        String sql = "SELECT * FROM patients WHERE phone = ? AND is_active = 1";
        List<Patient> list = executeQuery(sql, ps -> ps.setString(1, phone));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Full-text search: name, phone, NIC, patient code */
    public List<Patient> search(String keyword) throws SQLException {
        String like = "%" + keyword.trim() + "%";
        String sql = "SELECT * FROM patients WHERE is_active = 1 AND (" +
                "  CONCAT(first_name, ' ', last_name) LIKE ? " +
                "  OR phone LIKE ? " +
                "  OR nic_number LIKE ? " +
                "  OR patient_code LIKE ?" +
                ") ORDER BY last_name, first_name LIMIT 50";
        return executeQuery(sql, ps -> {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
        });
    }

    public int insert(Patient p) throws SQLException {
        String sql = """
            INSERT INTO patients (
                patient_code, first_name, last_name, full_name, date_of_birth, gender,
                blood_group, phone, email, nic_number, address,
                emergency_contact, emergency_phone,
                allergies, chronic_conditions, clinical_notes,
                insurer_name, policy_number, insurer_phone,
                loyalty_points, loyalty_tier, is_active, created_by
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Generate patient code if missing
            if (p.getPatientCode() == null) {
                p.setPatientCode(generatePatientCode(con));
            }

            ps.setString(1, p.getPatientCode());
            ps.setString(2, p.getFirstName());
            ps.setString(3, p.getLastName());
            ps.setString(4, p.getFullName() != null ? p.getFullName() : p.getDisplayName());
            ps.setObject(5, p.getDateOfBirth() != null ? Date.valueOf(p.getDateOfBirth()) : null, Types.DATE);
            ps.setString(6, p.getGender() != null ? p.getGender().name() : null);
            ps.setString(7, p.getBloodGroup() != null ? p.getBloodGroup().name() : null);
            ps.setString(8, p.getPhone());
            ps.setString(9, p.getEmail());
            ps.setString(10, p.getNicNumber());
            ps.setString(11, p.getAddress());
            ps.setString(12, p.getEmergencyContact());
            ps.setString(13, p.getEmergencyPhone());
            ps.setString(14, listToString(p.getAllergies()));
            ps.setString(15, listToString(p.getChronicConditions()));
            ps.setString(16, p.getClinicalNotes());
            ps.setString(17, p.getInsurerName());
            ps.setString(18, p.getPolicyNumber());
            ps.setString(19, p.getInsurerPhone());
            ps.setInt(20, p.getLoyaltyPoints());
            ps.setString(21, p.getLoyaltyTier() != null ? p.getLoyaltyTier() : "BRONZE");
            ps.setBoolean(22, p.isActive());
            ps.setObject(23, p.getCreatedBy(), Types.INTEGER);

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public void update(Patient p) throws SQLException {
        String sql = """
            UPDATE patients SET
                first_name=?, last_name=?, full_name=?, date_of_birth=?, gender=?,
                blood_group=?, phone=?, email=?, nic_number=?, address=?,
                emergency_contact=?, emergency_phone=?,
                allergies=?, chronic_conditions=?, clinical_notes=?,
                insurer_name=?, policy_number=?, insurer_phone=?,
                loyalty_points=?, loyalty_tier=?, is_active=?, updated_at=NOW()
            WHERE patient_id=?
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, p.getFirstName());
            ps.setString(2, p.getLastName());
            ps.setString(3, p.getFullName() != null ? p.getFullName() : p.getDisplayName());
            ps.setObject(4, p.getDateOfBirth() != null ? Date.valueOf(p.getDateOfBirth()) : null, Types.DATE);
            ps.setString(5, p.getGender() != null ? p.getGender().name() : null);
            ps.setString(6, p.getBloodGroup() != null ? p.getBloodGroup().name() : null);
            ps.setString(7, p.getPhone());
            ps.setString(8, p.getEmail());
            ps.setString(9, p.getNicNumber());
            ps.setString(10, p.getAddress());
            ps.setString(11, p.getEmergencyContact());
            ps.setString(12, p.getEmergencyPhone());
            ps.setString(13, listToString(p.getAllergies()));
            ps.setString(14, listToString(p.getChronicConditions()));
            ps.setString(15, p.getClinicalNotes());
            ps.setString(16, p.getInsurerName());
            ps.setString(17, p.getPolicyNumber());
            ps.setString(18, p.getInsurerPhone());
            ps.setInt(19, p.getLoyaltyPoints());
            ps.setString(20, p.getLoyaltyTier());
            ps.setBoolean(21, p.isActive());
            ps.setInt(22, p.getPatientId());
            ps.executeUpdate();
        }
    }

    public void softDelete(int patientId) throws SQLException {
        String sql = "UPDATE patients SET is_active = 0, updated_at = NOW() WHERE patient_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.executeUpdate();
        }
    }

    // ── Allergy management ───────────────────────────────────

    public void addAllergy(int patientId, String allergen) throws SQLException {
        Patient p = getById(patientId);
        if (p == null) throw new SQLException("Patient not found: " + patientId);
        if (!p.hasAllergy(allergen)) {
            p.getAllergies().add(allergen.trim());
            String sql = "UPDATE patients SET allergies = ?, updated_at = NOW() WHERE patient_id = ?";
            try (Connection con = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, listToString(p.getAllergies()));
                ps.setInt(2, patientId);
                ps.executeUpdate();
            }
        }
    }

    // ── Loyalty ──────────────────────────────────────────────

    public void addLoyaltyPoints(Connection con, int patientId, int points) throws SQLException {
        String sql = """
            UPDATE patients
            SET loyalty_points = loyalty_points + ?,
                loyalty_tier = CASE
                    WHEN loyalty_points + ? >= 5000 THEN 'GOLD'
                    WHEN loyalty_points + ? >= 2000 THEN 'SILVER'
                    ELSE 'BRONZE'
                END,
                updated_at = NOW()
            WHERE patient_id = ?
            """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, points);
            ps.setInt(2, points);
            ps.setInt(3, points);
            ps.setInt(4, patientId);
            ps.executeUpdate();
        }
    }

    // ── Stats ────────────────────────────────────────────────

    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients WHERE is_active = 1";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private String generatePatientCode(Connection con) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTRING(patient_code, 5) AS UNSIGNED)) FROM patients WHERE patient_code LIKE 'PAT-%'";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int next = rs.next() ? rs.getInt(1) + 1 : 1;
            return String.format("PAT-%06d", next);
        }
    }

    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join("|", list);
    }

    private List<String> stringToList(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\\|")));
    }

    @FunctionalInterface
    interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<Patient> executeQuery(String sql, ParamSetter setter) throws SQLException {
        List<Patient> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Patient mapRow(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setPatientId(rs.getInt("patient_id"));
        p.setPatientCode(rs.getString("patient_code"));
        p.setFirstName(rs.getString("first_name"));
        p.setLastName(rs.getString("last_name"));
        p.setFullName(rs.getString("full_name"));

        Date dob = rs.getDate("date_of_birth");
        if (dob != null) p.setDateOfBirth(dob.toLocalDate());

        String gender = rs.getString("gender");
        if (gender != null) {
            try { p.setGender(Gender.valueOf(gender)); } catch (Exception ignored) {}
        }

        String bg = rs.getString("blood_group");
        if (bg != null) {
            try { p.setBloodGroup(BloodGroup.valueOf(bg)); } catch (Exception ignored) {}
        }

        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setNicNumber(rs.getString("nic_number"));
        p.setAddress(rs.getString("address"));
        p.setEmergencyContact(rs.getString("emergency_contact"));
        p.setEmergencyPhone(rs.getString("emergency_phone"));
        p.setAllergies(stringToList(rs.getString("allergies")));
        p.setChronicConditions(stringToList(rs.getString("chronic_conditions")));
        p.setClinicalNotes(rs.getString("clinical_notes"));
        p.setInsurerName(rs.getString("insurer_name"));
        p.setPolicyNumber(rs.getString("policy_number"));
        p.setInsurerPhone(rs.getString("insurer_phone"));
        p.setLoyaltyPoints(rs.getInt("loyalty_points"));
        p.setLoyaltyTier(rs.getString("loyalty_tier"));
        p.setActive(rs.getBoolean("is_active"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) p.setUpdatedAt(updatedAt.toLocalDateTime());

        return p;
    }

//    public void save(Patient p){}
}

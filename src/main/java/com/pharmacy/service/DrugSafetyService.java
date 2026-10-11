package com.pharmacy.service;

import com.pharmacy.dao.PatientDAO;
import com.pharmacy.model.DrugInteractionAlert;
import com.pharmacy.model.DrugInteractionAlert.AlertType;
import com.pharmacy.model.DrugInteractionAlert.Severity;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Patient;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drug Safety Checking Service.
 *
 * Performs three critical safety checks at the point of dispensing:
 *   1. Drug–Allergy check — patient's known allergies vs dispensed drug
 *   2. Drug–Drug Interaction check — dispensed drug vs patient's current medications
 *   3. Duplicate Therapy check — same therapeutic class already dispensed
 *
 * This is the most safety-critical service in the system.
 * Every check result is returned as a DrugInteractionAlert and displayed
 * to the pharmacist before dispensing is confirmed.
 *
 * Data source: drug_interactions table in the local DB (pre-populated from
 * DRAP drug list or DrugBank free dataset). For production, integrate
 * a licensed drug knowledge base (First Databank, Medi-Span, or Multum).
 */
public class DrugSafetyService {

    private static final Logger LOG = Logger.getLogger(DrugSafetyService.class.getName());
    private final PatientDAO patientDAO = new PatientDAO();

    /**
     * Master safety check — runs all checks and returns all alerts.
     *
     * @param patientId     Patient receiving the medication (can be null for walk-in)
     * @param newMedicines  Medicines about to be dispensed
     * @return List of alerts ordered by severity (most severe first)
     */
    public List<DrugInteractionAlert> checkAll(Integer patientId, List<Medicine> newMedicines) {
        List<DrugInteractionAlert> alerts = new ArrayList<>();

        if (newMedicines == null || newMedicines.isEmpty()) return alerts;

        try {
            Patient patient = (patientId != null) ? patientDAO.getById(patientId) : null;

            // 1. Drug–Allergy checks
            if (patient != null && !patient.getAllergies().isEmpty()) {
                alerts.addAll(checkDrugAllergies(patient, newMedicines));
            }

            // 2. Drug–Drug Interaction checks
            List<Medicine> currentMeds = (patient != null)
                    ? getCurrentMedications(patient.getPatientId()) : new ArrayList<>();

            alerts.addAll(checkDrugInteractions(newMedicines, currentMeds));

            // 3. Duplicate therapy within the dispensing basket
            alerts.addAll(checkDuplicateTherapy(newMedicines));

            // Sort: CONTRAINDICATED → MAJOR → MODERATE → MINOR → INFO
            alerts.sort((a, b) -> a.getSeverity().compareTo(b.getSeverity()));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Drug safety check failed — dispensing continues with logged error", e);
            // Safety check failure must never block dispensing entirely —
            // log it and return a warning-level alert
            DrugInteractionAlert systemAlert = new DrugInteractionAlert(
                    Severity.INFO, AlertType.DRUG_DRUG,
                    "Safety Check", "System",
                    "Automated interaction check could not be completed. Manual verification required."
            );
            alerts.add(systemAlert);
        }

        return alerts;
    }

    // ── 1. Drug–Allergy Check ────────────────────────────────

    private List<DrugInteractionAlert> checkDrugAllergies(Patient patient, List<Medicine> medicines) {
        List<DrugInteractionAlert> alerts = new ArrayList<>();

        for (Medicine med : medicines) {
            for (String allergen : patient.getAllergies()) {
                if (allergen == null || allergen.isBlank()) continue;

                boolean directMatch = med.getName().toLowerCase().contains(allergen.toLowerCase())
                        || (med.getGenericName() != null
                        && med.getGenericName().toLowerCase().contains(allergen.toLowerCase()));

                boolean classMatch = med.getAllergyClass() != null
                        && med.getAllergyClass().toLowerCase().contains(allergen.toLowerCase());

                if (directMatch || classMatch) {
                    DrugInteractionAlert alert = new DrugInteractionAlert(
                            Severity.CONTRAINDICATED,
                            AlertType.DRUG_ALLERGY,
                            med.getName(),
                            allergen,
                            "Patient has a documented allergy to " + allergen + "."
                    );
                    alert.setClinicalEffect("Potential allergic reaction — ranging from rash to anaphylaxis");
                    alert.setManagement("Do NOT dispense. Consult prescriber for alternative.");
                    alert.setOverridable(false);
                    alert.setSource("Patient allergy record");
                    alerts.add(alert);
                }
            }
        }

        // Check cross-class allergies from the interaction DB
        try {
            alerts.addAll(checkCrossAllergyFromDB(patient.getAllergies(), medicines));
        } catch (SQLException e) {
            LOG.warning("Cross-allergy DB check failed: " + e.getMessage());
        }

        return alerts;
    }

    private List<DrugInteractionAlert> checkCrossAllergyFromDB(
            List<String> allergies, List<Medicine> medicines) throws SQLException {

        List<DrugInteractionAlert> alerts = new ArrayList<>();
        if (allergies.isEmpty()) return alerts;

        String sql = "SELECT * FROM drug_allergy_cross_refs " +
                "WHERE LOWER(allergen_class) = LOWER(?) AND LOWER(drug_class) = LOWER(?)";

        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (String allergen : allergies) {
                for (Medicine med : medicines) {
                    if (med.getAllergyClass() == null) continue;
                    ps.setString(1, allergen);
                    ps.setString(2, med.getAllergyClass());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        DrugInteractionAlert alert = new DrugInteractionAlert(
                                Severity.MAJOR,
                                AlertType.DRUG_ALLERGY,
                                med.getName(),
                                allergen,
                                rs.getString("description")
                        );
                        alert.setManagement("Verify with patient. Consider alternative.");
                        alert.setSource("Drug cross-allergy database");
                        alerts.add(alert);
                    }
                }
            }
        }
        return alerts;
    }

    // ── 2. Drug–Drug Interaction Check ──────────────────────

    private List<DrugInteractionAlert> checkDrugInteractions(
            List<Medicine> newMeds, List<Medicine> currentMeds) throws SQLException {

        List<DrugInteractionAlert> alerts = new ArrayList<>();
        if (currentMeds.isEmpty() && newMeds.size() < 2) return alerts;

        // Combine new + current for cross-basket checking
        List<Medicine> allMeds = new ArrayList<>(currentMeds);
        allMeds.addAll(newMeds);

        String sql = """
            SELECT di.*, m1.name AS drug1_name, m2.name AS drug2_name
            FROM drug_interactions di
            WHERE (di.drug1_group IN (
                SELECT drug_interaction_groups FROM medicines WHERE med_id = ?
            ) AND di.drug2_group IN (
                SELECT drug_interaction_groups FROM medicines WHERE med_id = ?
            )) OR (di.drug1_group IN (
                SELECT drug_interaction_groups FROM medicines WHERE med_id = ?
            ) AND di.drug2_group IN (
                SELECT drug_interaction_groups FROM medicines WHERE med_id = ?
            ))
            """;

        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            for (Medicine newMed : newMeds) {
                for (Medicine otherMed : allMeds) {
                    if (newMed.getMedId() == otherMed.getMedId()) continue;

                    alerts.addAll(queryInteractionDB(con, newMed, otherMed));
                }
            }
        }

        return alerts;
    }

    private List<DrugInteractionAlert> queryInteractionDB(
            Connection con, Medicine drug1, Medicine drug2) throws SQLException {

        List<DrugInteractionAlert> alerts = new ArrayList<>();
        if (drug1.getDrugInteractionGroups() == null || drug2.getDrugInteractionGroups() == null) {
            return alerts;
        }

        String sql = """
            SELECT * FROM drug_interactions
            WHERE (drug1_group = ? AND drug2_group = ?)
               OR (drug1_group = ? AND drug2_group = ?)
            """;

        for (String g1 : drug1.getDrugInteractionGroups().split(",")) {
            for (String g2 : drug2.getDrugInteractionGroups().split(",")) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, g1.trim());
                    ps.setString(2, g2.trim());
                    ps.setString(3, g2.trim());
                    ps.setString(4, g1.trim());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        Severity sev = parseSeverity(rs.getString("severity"));
                        DrugInteractionAlert alert = new DrugInteractionAlert(
                                sev, AlertType.DRUG_DRUG,
                                drug1.getName(), drug2.getName(),
                                rs.getString("description")
                        );
                        alert.setClinicalEffect(rs.getString("clinical_effect"));
                        alert.setManagement(rs.getString("management"));
                        alert.setSource("Drug interaction database");
                        alerts.add(alert);
                    }
                }
            }
        }
        return alerts;
    }

    // ── 3. Duplicate Therapy Check ───────────────────────────

    private List<DrugInteractionAlert> checkDuplicateTherapy(List<Medicine> medicines) {
        List<DrugInteractionAlert> alerts = new ArrayList<>();

        for (int i = 0; i < medicines.size(); i++) {
            for (int j = i + 1; j < medicines.size(); j++) {
                Medicine a = medicines.get(i);
                Medicine b = medicines.get(j);

                boolean sameGeneric = a.getGenericName() != null
                        && a.getGenericName().equalsIgnoreCase(b.getGenericName());
                boolean sameTherapeuticClass = a.getTherapeuticClass() != null
                        && a.getTherapeuticClass().equalsIgnoreCase(b.getTherapeuticClass());

                if (sameGeneric) {
                    DrugInteractionAlert alert = new DrugInteractionAlert(
                            Severity.MAJOR, AlertType.DUPLICATE_THERAPY,
                            a.getName(), b.getName(),
                            "Both " + a.getName() + " and " + b.getName()
                                    + " contain the same active ingredient: " + a.getGenericName()
                    );
                    alert.setManagement("Verify with prescriber — likely a duplicate dispensing.");
                    alert.setSource("Generic name matching");
                    alerts.add(alert);
                } else if (sameTherapeuticClass) {
                    DrugInteractionAlert alert = new DrugInteractionAlert(
                            Severity.MODERATE, AlertType.DUPLICATE_THERAPY,
                            a.getName(), b.getName(),
                            "Both medicines belong to the same therapeutic class: "
                                    + a.getTherapeuticClass()
                    );
                    alert.setManagement("Confirm with prescriber that dual therapy is intentional.");
                    alert.setSource("Therapeutic class matching");
                    alerts.add(alert);
                }
            }
        }
        return alerts;
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Returns medicines currently being taken by the patient
     * based on their last 90 days of dispensed prescriptions.
     */
    private List<Medicine> getCurrentMedications(int patientId) throws SQLException {
        List<Medicine> meds = new ArrayList<>();
        String sql = """
            SELECT DISTINCT m.*, s.name AS supplier_name
            FROM sale_items si
            JOIN sales s2 ON si.sale_id = s2.sale_id
            JOIN medicines m ON si.med_id = m.med_id
            LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id
            WHERE s2.patient_id = ?
              AND s2.sale_date >= DATE_SUB(NOW(), INTERVAL 90 DAY)
              AND s2.status = 'COMPLETED'
            """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Medicine m = new Medicine();
                m.setMedId(rs.getInt("med_id"));
                m.setName(rs.getString("name"));
                m.setGenericName(rs.getString("generic_name"));
                m.setTherapeuticClass(rs.getString("therapeutic_class"));
                m.setAllergyClass(rs.getString("allergy_class"));
                m.setDrugInteractionGroups(rs.getString("drug_interaction_groups"));
                meds.add(m);
            }
        } catch (SQLException e) {
            // Column may not exist in old schema — fail gracefully
            LOG.warning("Could not retrieve current medications: " + e.getMessage());
        }
        return meds;
    }

    private Severity parseSeverity(String s) {
        if (s == null) return Severity.INFO;
        return switch (s.toUpperCase()) {
            case "CONTRAINDICATED" -> Severity.CONTRAINDICATED;
            case "MAJOR"           -> Severity.MAJOR;
            case "MODERATE"        -> Severity.MODERATE;
            case "MINOR"           -> Severity.MINOR;
            default                -> Severity.INFO;
        };
    }

    /** Returns true if there are any CONTRAINDICATED or MAJOR alerts */
    public boolean hasCriticalAlerts(List<DrugInteractionAlert> alerts) {
        return alerts.stream().anyMatch(DrugInteractionAlert::isCritical);
    }
}

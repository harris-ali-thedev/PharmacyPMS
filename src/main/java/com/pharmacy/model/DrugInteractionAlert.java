package com.pharmacy.model;

/**
 * Result of a drug-drug interaction (DDI) or drug-allergy check.
 * Returned by DrugSafetyService and displayed at the POS
 * before the pharmacist confirms dispensing.
 */
public class DrugInteractionAlert {

    public enum Severity { CONTRAINDICATED, MAJOR, MODERATE, MINOR, INFO }
    public enum AlertType { DRUG_DRUG, DRUG_ALLERGY, DUPLICATE_THERAPY, DOSE_RANGE }

    private Severity  severity;
    private AlertType alertType;
    private String    drug1Name;
    private String    drug2Name;         // Or allergen name
    private String    description;       // Clinical description
    private String    clinicalEffect;    // What happens
    private String    management;        // What the pharmacist should do
    private boolean   overridable;       // Can a pharmacist override with justification?
    private String    source;            // Data source: "DRAP-DB", "DrugBank", etc.

    public DrugInteractionAlert() {}

    public DrugInteractionAlert(Severity severity, AlertType alertType,
                                String drug1, String drug2, String description) {
        this.severity = severity;
        this.alertType = alertType;
        this.drug1Name = drug1;
        this.drug2Name = drug2;
        this.description = description;
        // CONTRAINDICATED alerts cannot be overridden
        this.overridable = severity != Severity.CONTRAINDICATED;
    }

    public boolean isCritical() {
        return severity == Severity.CONTRAINDICATED || severity == Severity.MAJOR;
    }

    public String getSeverityLabel() {
        return switch (severity) {
            case CONTRAINDICATED -> "CONTRAINDICATED — Do not dispense";
            case MAJOR           -> "MAJOR — Significant risk";
            case MODERATE        -> "MODERATE — Monitor patient";
            case MINOR           -> "MINOR — Be aware";
            case INFO            -> "INFO";
        };
    }

    // ── Getters / Setters ────────────────────────────────────

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity v) { severity = v; }
    public AlertType getAlertType() { return alertType; }
    public void setAlertType(AlertType v) { alertType = v; }
    public String getDrug1Name() { return drug1Name; }
    public void setDrug1Name(String v) { drug1Name = v; }
    public String getDrug2Name() { return drug2Name; }
    public void setDrug2Name(String v) { drug2Name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getClinicalEffect() { return clinicalEffect; }
    public void setClinicalEffect(String v) { clinicalEffect = v; }
    public String getManagement() { return management; }
    public void setManagement(String v) { management = v; }
    public boolean isOverridable() { return overridable; }
    public void setOverridable(boolean v) { overridable = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }

    public String getSeverity1() {
        Severity severity = getSeverity();
        switch(severity){
            case CONTRAINDICATED -> { return "CONTRAINDICATED";}
            case MAJOR -> { return "MAJOR"; }
            case MINOR -> { return "MINOR"; }
            case INFO -> { return "INFO"; }
            default -> {return "MODERATE"; }
        }
    }

    public String getRecommendation() {
        Severity severity = getSeverity();
        switch (severity) {
            case CONTRAINDICATED -> {
                return "Avoid prescribing this drug; refer to addiction specialist immediately.";
            }
            case MAJOR -> {
                return "Do not prescribe; consider alternative therapy and offer addiction treatment referral.";
            }
            case MINOR -> {
                return "Prescribe with awareness, provide patient education on risks and monitor for escalation.";
            }
            case INFO -> {
                return "Low risk but counsel patient on proper use and potential for dependence.";
            }
            default -> {
                return "Use with caution and close monitoring; consider non-addictive alternatives and brief intervention.";
            }
        }
    }

}

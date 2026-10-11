package com.pharmacy.view.prescription;

import com.pharmacy.model.DrugInteractionAlert;
import com.pharmacy.model.Prescription;
import com.pharmacy.service.DrugSafetyService;
import com.pharmacy.view.AppUIManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * DrugInteractionView — Displays drug interaction alerts before dispensing.
 */
public class DrugInteractionView {

    private final Prescription prescription;
    private final DrugSafetyService safetyService = new DrugSafetyService();

    public DrugInteractionView(Prescription prescription) {
        this.prescription = prescription;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Drug Interaction Check — " + prescription.getPrescriptionNumber());
        dialog.setWidth(700);
        dialog.setHeight(560);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        // Header
        HBox header = new HBox(12);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:#FFF7ED; -fx-border-color:transparent transparent #FED7AA transparent; -fx-border-width:0 0 1 0;");
        Label icon = new Label("⚠");
        icon.setStyle("-fx-font-size:28px;");
        VBox titleBox = new VBox(4);
        titleBox.getChildren().addAll(
                AppUIManager.sectionTitle("Drug Interaction & Safety Check"),
                new Label("Patient: " + (prescription.getPatientName() != null ? prescription.getPatientName() : "—")
                        + "  |  Rx: " + prescription.getPrescriptionNumber())
        );
        header.getChildren().addAll(icon, titleBox);
        root.getChildren().add(header);

        // Content
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        List<DrugInteractionAlert> alerts = List.of();
        try {
            alerts = new DrugSafetyService().checkAll(prescription.getPatientId(), null);
        } catch (Exception ex) {
            /* safety service or DB not connected */
        }

        if (alerts.isEmpty()) {
            VBox safeCard = AppUIManager.card();
            safeCard.setStyle(safeCard.getStyle() + "-fx-border-color:#BBF7D0; -fx-background-color:#F0FDF4;");
            HBox safeRow = new HBox(12);
            safeRow.setAlignment(Pos.CENTER_LEFT);
            Label safeIcon = new Label("✅");
            safeIcon.setStyle("-fx-font-size:32px;");
            VBox safeText = new VBox(4);
            Label safeTitle = new Label("No Drug Interactions Detected");
            safeTitle.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#166534;");
            Label safeDesc = new Label("All medications in this prescription appear safe together. " +
                    "No known interactions with the patient's current medications or documented allergies.");
            safeDesc.setStyle("-fx-font-size:13px; -fx-text-fill:#166534; -fx-wrap-text:true;");
            safeText.getChildren().addAll(safeTitle, safeDesc);
            safeRow.getChildren().addAll(safeIcon, safeText);
            safeCard.getChildren().add(safeRow);
            content.getChildren().add(safeCard);
        } else {
            for (DrugInteractionAlert alert : alerts) {
                content.getChildren().add(buildAlertCard(alert));
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // Footer
        HBox footer = new HBox(12);
        footer.setPadding(new Insets(14, 24, 14, 24));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:white; -fx-border-color:" + AppUIManager.COLOR_BORDER + " transparent transparent transparent; -fx-border-width:1 0 0 0;");

        boolean hasContraindication = alerts.stream()
                .anyMatch(a -> "CONTRAINDICATED".equals(a.getSeverity()));

        if (hasContraindication) {
            Label warn = new Label("⚠  CONTRAINDICATION DETECTED — Pharmacist must review before dispensing");
            warn.setStyle("-fx-font-size:12px; -fx-text-fill:" + AppUIManager.COLOR_DANGER + "; -fx-font-weight:bold;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Button closeBtn = AppUIManager.dangerButton("Do Not Dispense");
            closeBtn.setOnAction(e -> dialog.close());
            footer.getChildren().addAll(warn, sp, closeBtn);
        } else {
            Button overrideBtn = AppUIManager.warningButton("⚠  Dispense with Override");
            overrideBtn.setOnAction(e -> dialog.close());
            Button dispenseBtn = AppUIManager.successButton("✔  Proceed to Dispense");
            dispenseBtn.setOnAction(e -> { dialog.close(); });
            Button closeBtn = AppUIManager.outlineButton("Cancel");
            closeBtn.setOnAction(e -> dialog.close());
            footer.getChildren().addAll(closeBtn, overrideBtn, dispenseBtn);
        }

        root.getChildren().add(footer);
        dialog.setScene(new Scene(root));
        dialog.show();
    }

    private VBox buildAlertCard(DrugInteractionAlert alert) {
        String severity = alert.getSeverity() == null ? "MODERATE" : alert.getSeverity1();
        String bg, border, textColor;
        switch (severity) {
            case "CONTRAINDICATED" -> { bg = "#FEF2F2"; border = "#FECACA"; textColor = "#991B1B"; }
            case "MAJOR"           -> { bg = "#FFF7ED"; border = "#FED7AA"; textColor = "#92400E"; }
            default                -> { bg = "#FFFBEB"; border = "#FDE68A"; textColor = "#92400E"; }
        }

        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:8; " +
                "-fx-border-color:" + border + "; -fx-border-width:1; -fx-border-radius:8;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label sev = new Label(severity);
        sev.setStyle("-fx-background-color:" + textColor + "; -fx-text-fill:white; " +
                "-fx-background-radius:4; -fx-padding:2 8 2 8; -fx-font-size:11px; -fx-font-weight:bold;");
        Label drugs = new Label((alert.getDrug1Name() == null ? "Drug 1" : alert.getDrug1Name()) +
                "  ⟷  " + (alert.getDrug2Name() == null ? "Drug 2" : alert.getDrug2Name()));
        drugs.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:" + textColor + ";");
        titleRow.getChildren().addAll(sev, drugs);

        Label desc = new Label(alert.getDescription() == null ? "Drug interaction detected." : alert.getDescription());
        desc.setStyle("-fx-font-size:13px; -fx-text-fill:#374151; -fx-wrap-text:true;");

        if (alert.getRecommendation() != null) {
            Label rec = new Label("Recommendation: " + alert.getRecommendation());
            rec.setStyle("-fx-font-size:12px; -fx-text-fill:" + textColor + "; -fx-wrap-text:true; -fx-font-weight:bold;");
            card.getChildren().addAll(titleRow, desc, rec);
        } else {
            card.getChildren().addAll(titleRow, desc);
        }
        return card;
    }
}
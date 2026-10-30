package com.pharmacy.view.billing;

import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * InsuranceClaimView — Process insurance claims and co-pay calculations.
 */
public class InsuranceClaimView {

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        HBox header = new HBox();
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        VBox tb = new VBox(3);
        tb.getChildren().addAll(
                AppUIManager.pageTitle("🏥  Insurance Claims"),
                label("Process insurance claims and calculate patient co-pay.", AppUIManager.COLOR_TEXT_MEDIUM)
        );
        header.getChildren().add(tb);
        page.getChildren().add(header);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // Claim entry form
        VBox claimCard = AppUIManager.card();
        claimCard.getChildren().add(AppUIManager.sectionTitle("New Insurance Claim"));

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50); c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);

        TextField invoiceField    = AppUIManager.styledField("Invoice number");
        TextField policyField     = AppUIManager.styledField("Policy number");
        TextField insurerField    = AppUIManager.styledField("Insurance company");
        TextField coveragePctField = AppUIManager.styledField("Coverage %  e.g. 80");
        TextField totalField      = AppUIManager.styledField("Total invoice amount");
        TextField coPayField      = AppUIManager.styledField("Co-pay amount (auto)");
        coPayField.setEditable(false);
        coPayField.setStyle(coPayField.getStyle() + "-fx-background-color:#F8FAFC;");

        // Auto-calculate co-pay
        Runnable calcCopay = () -> {
            try {
                double total = Double.parseDouble(totalField.getText().trim());
                double cov   = Double.parseDouble(coveragePctField.getText().trim());
                double copay = total * (100 - cov) / 100;
                coPayField.setText(String.format("Rs %.2f", copay));
            } catch (Exception ignored) { coPayField.setText("—"); }
        };
        totalField.textProperty().addListener((obs, o, v) -> calcCopay.run());
        coveragePctField.textProperty().addListener((obs, o, v) -> calcCopay.run());

        row(grid, 0, "Invoice #", invoiceField, "Policy Number", policyField);
        row(grid, 1, "Insurance Company", insurerField, "Coverage %", coveragePctField);
        row(grid, 2, "Total Amount", totalField, "Patient Co-Pay", coPayField);

        Button submitBtn = AppUIManager.primaryButton("📤  Submit Claim");
        submitBtn.setText("Record Claim");
        totalField.setEditable(false);
        totalField.setPromptText("Loaded from the invoice when recorded");
        submitBtn.setOnAction(e -> {
            if (invoiceField.getText().isBlank() || policyField.getText().isBlank()) {
                MessageDialog.showWarning("Required", "Invoice number and policy number are required.");
                return;
            }
            try {
                int saleId=Integer.parseInt(invoiceField.getText().trim());
                java.math.BigDecimal coverage=new java.math.BigDecimal(coveragePctField.getText().trim());
                String policy=policyField.getText().trim(), insurer=insurerField.getText().trim();
                submitBtn.setDisable(true);
                com.pharmacy.util.UiTasks.run(() -> new com.pharmacy.service.InsuranceClaimService().record(saleId,policy,insurer,coverage), claim -> {
                    submitBtn.setDisable(false);
                    totalField.setText(claim.invoiceTotal().toPlainString()); coPayField.setText(claim.copay().toPlainString());
                    MessageDialog.showSuccess("Claim Recorded", "Claim #" + claim.id() + " saved for invoice " + saleId
                            + ". Patient co-pay: Rs " + claim.copay() + ". Submit this record to the insurer through your normal process.");
                }, error -> { submitBtn.setDisable(false); MessageDialog.showError("Claim failed",error.getMessage()); });
            } catch(Exception ex) {MessageDialog.showWarning("Invalid claim","Enter a numeric invoice ID and a valid coverage percentage.");}
        });

        claimCard.getChildren().addAll(grid, submitBtn);
        content.getChildren().add(claimCard);
        page.getChildren().add(content);

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return sp;
    }

    private void row(GridPane g, int r, String l1, Node f1, String l2, Node f2) {
        g.add(new VBox(5, AppUIManager.fieldLabel(l1), f1), 0, r);
        g.add(new VBox(5, AppUIManager.fieldLabel(l2), f2), 1, r);
    }
    private Label label(String text, String color) {
        Label l = new Label(text); l.setStyle("-fx-font-size:13px; -fx-text-fill:" + color + ";"); return l;
    }
}

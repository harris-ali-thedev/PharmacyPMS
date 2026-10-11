package com.pharmacy.view.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * BarcodeScannerDialog — Modal for barcode input.
 *
 * Accepts input from a physical barcode scanner (which sends keystrokes ending
 * with Enter) or manual keyboard entry. The field auto-focuses on open so the
 * scanner can fire immediately.
 */
public class BarcodeScannerDialog {

    private final Consumer<String> onScan;
    private Stage dialog;

    public BarcodeScannerDialog(Consumer<String> onScan) {
        this.onScan = onScan;
    }

    public void show() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Barcode Scanner");
        dialog.setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(28, 32, 24, 32));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(380);

        // Icon
        Label icon = new Label("▦");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #1E3A5F;");

        Label title = new Label("Scan Barcode");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        Label instruction = new Label("Point scanner at barcode, or type the code manually and press Enter.");
        instruction.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-wrap-text: true; -fx-text-alignment: center;");
        instruction.setMaxWidth(300);
        instruction.setAlignment(Pos.CENTER);

        TextField barcodeField = new TextField();
        barcodeField.setPromptText("Barcode will appear here…");
        barcodeField.setPrefHeight(42);
        barcodeField.setStyle("-fx-background-radius: 6; -fx-border-radius: 6; " +
                "-fx-border-color: #0EA5E9; -fx-border-width: 2; -fx-font-size: 15px; " +
                "-fx-padding: 4 12 4 12; -fx-font-weight: bold; -fx-letter-spacing: 2;");

        Label statusLabel = new Label("Ready — waiting for scan…");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #22C55E;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setPrefWidth(100);
        cancelBtn.setPrefHeight(36);
        cancelBtn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button submitBtn = new Button("Submit");
        submitBtn.setPrefWidth(100);
        submitBtn.setPrefHeight(36);
        submitBtn.setStyle("-fx-background-color: #1E3A5F; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        Runnable submit = () -> {
            String code = barcodeField.getText().trim();
            if (!code.isEmpty()) {
                dialog.close();
                onScan.accept(code);
            } else {
                barcodeField.setStyle("-fx-background-radius: 6; -fx-border-radius: 6; " +
                        "-fx-border-color: #DC2626; -fx-border-width: 2; -fx-font-size: 15px; " +
                        "-fx-padding: 4 12 4 12;");
                statusLabel.setText("Please scan or enter a barcode first.");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #DC2626;");
            }
        };

        submitBtn.setOnAction(e -> submit.run());
        barcodeField.setOnAction(e -> submit.run());

        // Auto-submit when scanner fires (typically very fast input followed by Enter)
        barcodeField.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.isEmpty()) {
                statusLabel.setText("Code entered: " + val);
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #0EA5E9;");
            }
        });

        buttons.getChildren().addAll(cancelBtn, submitBtn);
        root.getChildren().addAll(icon, title, instruction, barcodeField, statusLabel, buttons);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();

        // Auto-focus the input field so scanner fires immediately
        barcodeField.requestFocus();
    }
}
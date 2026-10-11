package com.pharmacy.view.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * ConfirmationDialog — Professional Yes/No dialog with optional detail text.
 */
public class ConfirmationDialog {

    private final String title;
    private final String message;
    private final String detail;
    private boolean confirmed = false;

    public ConfirmationDialog(String title, String message) {
        this(title, message, null);
    }

    public ConfirmationDialog(String title, String message, String detail) {
        this.title   = title;
        this.message = message;
        this.detail  = detail;
    }

    /** Shows dialog and returns true if user clicked Confirm */
    public boolean showAndWait() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 5);");

        // Header
        HBox header = new HBox(10);
        header.setPadding(new Insets(18, 24, 14, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #FFF7ED; -fx-background-radius: 12 12 0 0; " +
                "-fx-border-color: transparent transparent #FED7AA transparent; -fx-border-width: 0 0 1 0;");

        Label iconLabel = new Label("⚠");
        iconLabel.setStyle("-fx-font-size: 22px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #92400E;");

        header.getChildren().addAll(iconLabel, titleLabel);

        // Body
        VBox body = new VBox(10);
        body.setPadding(new Insets(20, 24, 20, 24));

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1E293B; -fx-wrap-text: true;");
        msgLabel.setMaxWidth(340);
        body.getChildren().add(msgLabel);

        if (detail != null && !detail.isBlank()) {
            Label detailLabel = new Label(detail);
            detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-wrap-text: true;");
            detailLabel.setMaxWidth(340);
            body.getChildren().add(detailLabel);
        }

        // Buttons
        HBox buttons = new HBox(10);
        buttons.setPadding(new Insets(0, 24, 20, 24));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setPrefWidth(90);
        cancelBtn.setPrefHeight(36);
        cancelBtn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px;");
        cancelBtn.setOnAction(e -> { confirmed = false; dialog.close(); });

        Button confirmBtn = new Button("Confirm");
        confirmBtn.setPrefWidth(90);
        confirmBtn.setPrefHeight(36);
        confirmBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px; -fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> { confirmed = true; dialog.close(); });

        buttons.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(header, body, buttons);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return confirmed;
    }

    /** Convenience static factory */
    public static boolean confirm(String title, String message) {
        return new ConfirmationDialog(title, message).showAndWait();
    }

    public static boolean confirmDelete(String entityName) {
        return new ConfirmationDialog(
                "Confirm Delete",
                "Are you sure you want to delete this " + entityName + "?",
                "This action cannot be undone."
        ).showAndWait();
    }
}
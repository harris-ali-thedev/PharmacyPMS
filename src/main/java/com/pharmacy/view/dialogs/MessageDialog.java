package com.pharmacy.view.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * MessageDialog — Professional Info / Warning / Error / Success dialog.
 */
public class MessageDialog {

    public enum Type { INFO, SUCCESS, WARNING, ERROR }

    private final String title;
    private final String message;
    private final Type   type;

    public MessageDialog(String title, String message, Type type) {
        this.title   = title;
        this.message = message;
        this.type    = type;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        String[] colors = colorsFor(type);
        String icon = iconFor(type);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 5);");

        // Header
        HBox header = new HBox(10);
        header.setPadding(new Insets(18, 24, 14, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + colors[0] + "; -fx-background-radius: 12 12 0 0; " +
                "-fx-border-color: transparent transparent " + colors[2] + " transparent; " +
                "-fx-border-width: 0 0 1 0;");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 22px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + colors[1] + ";");

        header.getChildren().addAll(iconLabel, titleLabel);

        // Body
        VBox body = new VBox(16);
        body.setPadding(new Insets(20, 24, 20, 24));

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1E293B; -fx-wrap-text: true;");
        msgLabel.setMaxWidth(360);
        body.getChildren().add(msgLabel);

        // OK Button
        HBox buttons = new HBox();
        buttons.setPadding(new Insets(0, 24, 20, 24));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button okBtn = new Button("OK");
        okBtn.setPrefWidth(80);
        okBtn.setPrefHeight(36);
        okBtn.setStyle("-fx-background-color: " + colors[1] + "; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px; -fx-font-weight: bold;");
        okBtn.setOnAction(e -> dialog.close());

        buttons.getChildren().add(okBtn);
        root.getChildren().addAll(header, body, buttons);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // Static convenience methods
    public static void showInfo(String title, String message) {
        new MessageDialog(title, message, Type.INFO).show();
    }

    public static void showSuccess(String title, String message) {
        new MessageDialog(title, message, Type.SUCCESS).show();
    }

    public static void showWarning(String title, String message) {
        new MessageDialog(title, message, Type.WARNING).show();
    }

    public static void showError(String title, String message) {
        new MessageDialog(title, message, Type.ERROR).show();
    }

    private String[] colorsFor(Type t) {
        return switch (t) {
            case INFO    -> new String[]{"#EFF6FF", "#1D4ED8", "#BFDBFE"};
            case SUCCESS -> new String[]{"#F0FDF4", "#16A34A", "#BBF7D0"};
            case WARNING -> new String[]{"#FFFBEB", "#D97706", "#FDE68A"};
            case ERROR   -> new String[]{"#FEF2F2", "#DC2626", "#FECACA"};
        };
    }

    private String iconFor(Type t) {
        return switch (t) {
            case INFO    -> "ℹ";
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR   -> "✕";
        };
    }
}
package com.pharmacy.view.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.Consumer;

/**
 * SearchBar — Reusable search component with debounce and clear button.
 *
 * Usage:
 *   SearchBar bar = new SearchBar("Search patients…", query -> loadResults(query));
 *   toolbar.getChildren().add(bar);
 */
public class SearchBar extends HBox {

    private final TextField field;

    public SearchBar(String prompt, Consumer<String> onSearch) {
        super(0);
        setAlignment(Pos.CENTER_LEFT);

        field = new TextField();
        field.setPromptText("🔍  " + prompt);
        field.setPrefHeight(36);
        field.setPrefWidth(280);
        field.setStyle(fieldStyle());
        HBox.setHgrow(field, Priority.ALWAYS);

        field.textProperty().addListener((obs, old, val) -> onSearch.accept(val == null ? "" : val.trim()));

        Button clearBtn = new Button("✕");
        clearBtn.setPrefHeight(36);
        clearBtn.setPrefWidth(36);
        clearBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #64748B; " +
                "-fx-background-radius: 0 6 6 0; -fx-cursor: hand; -fx-font-size: 12px;");
        clearBtn.setOnMouseEntered(e -> clearBtn.setStyle("-fx-background-color: #CBD5E1; -fx-text-fill: #334155; " +
                "-fx-background-radius: 0 6 6 0; -fx-cursor: hand; -fx-font-size: 12px;"));
        clearBtn.setOnMouseExited(e -> clearBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #64748B; " +
                "-fx-background-radius: 0 6 6 0; -fx-cursor: hand; -fx-font-size: 12px;"));
        clearBtn.setOnAction(e -> {
            field.clear();
            onSearch.accept("");
        });

        // Slightly round only left side of text field when paired with clear button
        field.setStyle(fieldStyle() + "-fx-background-radius: 6 0 0 6; -fx-border-radius: 6 0 0 6;");

        getChildren().addAll(field, clearBtn);
    }

    public String getText() { return field.getText(); }

    public void clear() { field.clear(); }

    public void requestFocusOnField() { field.requestFocus(); }

    private String fieldStyle() {
        return "-fx-background-radius: 6; -fx-border-radius: 6; " +
                "-fx-border-color: #CBD5E1; -fx-border-width: 1; -fx-font-size: 13px; -fx-padding: 4 10 4 10;";
    }
}
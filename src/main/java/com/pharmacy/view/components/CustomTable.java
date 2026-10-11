package com.pharmacy.view.components;

import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * CustomTable — Reusable, professional table component.
 *
 * Features:
 *   - Built-in search bar filtering all visible columns
 *   - Column sorting (click header)
 *   - CSV export
 *   - Row count display
 *   - Configurable row height and alternating row colour
 *   - Generic type T
 */
public class CustomTable<T> extends VBox {

    private final TableView<T> tableView;
    private final ObservableList<T> masterData = FXCollections.observableArrayList();
    private final FilteredList<T> filteredData;
    private final SortedList<T> sortedData;

    private final Label rowCountLabel;
    private final TextField searchField;

    private Predicate<T> externalFilter = item -> true;

    public CustomTable() {
        super(0);
        setStyle("-fx-background-color: transparent;");

        tableView   = new TableView<>();
        filteredData = new FilteredList<>(masterData, p -> true);
        sortedData   = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedData);

        AppUIManager.styleTable(tableView);
        tableView.setPlaceholder(new Label("No records found"));
        tableView.setFixedCellSize(36);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // ── Toolbar ──────────────────────────────────────────
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(0, 0, 10, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍  Search all columns…");
        searchField.setPrefWidth(260);
        searchField.setPrefHeight(34);
        searchField.setStyle("-fx-background-radius: 6; -fx-border-radius: 6; " +
                "-fx-border-color: #CBD5E1; -fx-border-width: 1; -fx-font-size: 13px; -fx-padding: 4 10 4 10;");
        searchField.textProperty().addListener((obs, old, val) -> applyFilter(val));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        rowCountLabel = new Label("0 records");
        rowCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        Button exportBtn = AppUIManager.outlineButton("⬇  Export CSV");
        exportBtn.setPrefHeight(34);
        exportBtn.setOnAction(e -> exportCsv());

        Button refreshBtn = AppUIManager.outlineButton("↻  Refresh");
        refreshBtn.setPrefHeight(34);

        toolbar.getChildren().addAll(searchField, spacer, rowCountLabel, exportBtn, refreshBtn);

        getChildren().addAll(toolbar, tableView);
        VBox.setVgrow(this, Priority.ALWAYS);

        // Update row count whenever data changes
        sortedData.addListener((javafx.collections.ListChangeListener<T>) c ->
                rowCountLabel.setText(sortedData.size() + " record" + (sortedData.size() == 1 ? "" : "s")));
    }

    // ── Public API ───────────────────────────────────────────

    public TableView<T> getTableView() { return tableView; }

    public void setItems(List<T> items) {
        masterData.setAll(items);
        rowCountLabel.setText(sortedData.size() + " records");
    }

    public void addItems(List<T> items) {
        masterData.addAll(items);
    }

    public void clearItems() {
        masterData.clear();
    }

    public ObservableList<T> getMasterData() { return masterData; }

    public T getSelectedItem() { return tableView.getSelectionModel().getSelectedItem(); }

    /** Set an additional filter predicate on top of the search filter */
    public void setFilter(Predicate<T> filter) {
        this.externalFilter = (filter != null) ? filter : item -> true;
        applyFilter(searchField.getText());
    }

    public void clearSearch() {
        searchField.clear();
    }

    /** Call this from outside to set a refresh action on the Refresh button */
    public void setRefreshAction(Runnable action) {
        // Find refresh button
        if (!getChildren().isEmpty() && getChildren().get(0) instanceof HBox toolbar) {
            toolbar.getChildren().stream()
                    .filter(n -> n instanceof Button b && b.getText().contains("Refresh"))
                    .forEach(n -> ((Button) n).setOnAction(e -> action.run()));
        }
    }

    public void addColumn(TableColumn<T, ?> col) {
        tableView.getColumns().add(col);
    }

    @SafeVarargs
    public final void addColumns(TableColumn<T, ?>... cols) {
        tableView.getColumns().addAll(cols);
    }

    // ── Filter logic ─────────────────────────────────────────

    private void applyFilter(String searchText) {
        String lower = (searchText == null) ? "" : searchText.toLowerCase().trim();

        filteredData.setPredicate(item -> {
            if (!externalFilter.test(item)) return false;
            if (lower.isEmpty()) return true;

            // Search all visible string columns
            for (TableColumn<T, ?> col : tableView.getColumns()) {
                try {
                    Object val = col.getCellData(item);
                    if (val != null && val.toString().toLowerCase().contains(lower)) return true;
                } catch (Exception ignored) {}
            }
            return false;
        });
    }

    // ── CSV Export ───────────────────────────────────────────

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export to CSV");
        chooser.setInitialFileName("export.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // Header row
            StringBuilder header = new StringBuilder();
            for (TableColumn<T, ?> col : tableView.getColumns()) {
                if (!header.isEmpty()) header.append(",");
                header.append("\"").append(col.getText().replace("\"", "\"\"")).append("\"");
            }
            pw.println(header);

            // Data rows
            for (T item : sortedData) {
                StringBuilder row = new StringBuilder();
                for (TableColumn<T, ?> col : tableView.getColumns()) {
                    if (!row.isEmpty()) row.append(",");
                    try {
                        Object val = col.getCellData(item);
                        String cell = val == null ? "" : val.toString();
                        row.append("\"").append(cell.replace("\"", "\"\"")).append("\"");
                    } catch (Exception ex) {
                        row.append("\"\"");
                    }
                }
                pw.println(row);
            }
            showExportSuccess(file.getName());

        } catch (IOException ex) {
            new MessageDialog("Export Failed", "Could not save file:\n" + ex.getMessage(),
                    MessageDialog.Type.ERROR).show();
        }
    }

    private void showExportSuccess(String filename) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Successful");
        alert.setHeaderText(null);
        alert.setContentText("Data exported to: " + filename);
        alert.showAndWait();
    }
}
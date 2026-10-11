package com.pharmacy.view.admin;

import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuditLogView {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final int DEFAULT_LIMIT = 300;

    private final TableView<AuditLogEntry> table = new TableView<>();
    private final Label statusLabel = new Label("Ready");
    private final TextField searchField = new TextField();
    private final ComboBox<String> actionFilter = new ComboBox<>();
    private final ComboBox<String> entityFilter = new ComboBox<>();
    private final DatePicker fromDatePicker = new DatePicker();
    private final DatePicker toDatePicker = new DatePicker();

    private final Label selectedAction = detailValue();
    private final Label selectedUser = detailValue();
    private final Label selectedEntity = detailValue();
    private final Label selectedIp = detailValue();
    private final Label selectedTime = detailValue();
    private final TextArea selectedDescription = new TextArea();

    public Node getView() {
        if (!SessionManager.getInstance().isAdmin()) {
            BorderPane denied = new BorderPane();
            denied.setStyle("-fx-background-color: #F3F4F6;");
            Label message = new Label("Access denied. Admins only.");
            message.setStyle("-fx-font-size: 16px; -fx-text-fill: #DC2626;");
            denied.setCenter(message);
            return denied;
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F4F6;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));

        Label title = new Label("Audit Log");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Review security-sensitive actions, user activity, and system events.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        HBox toolbar = buildToolbar();
        HBox bottomToolbar = buildBottomToolbar();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No audit entries found."));
        table.setPrefHeight(500);
        buildColumns();
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showDetails(newValue));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMaxSize(1200, 800);
        table.setMinSize(800,400);

        VBox details = buildDetailsPane();
        content.getChildren().addAll(title, subtitle, toolbar, table, details, bottomToolbar);

        root.setCenter(AppUIManager.scrollWrap(content));
        loadAuditEntries();
        return root;
    }

    private HBox buildToolbar() {
        searchField.setPromptText("Search user, action, description...");
        searchField.setPrefWidth(260);

        actionFilter.getItems().addAll(
                "All Actions",
                "LOGIN_SUCCESS",
                "LOGIN_FAILED",
                "LOGOUT",
                "SALE_COMPLETED",
                "SALE_VOIDED",
                "ADD_USER",
                "EDIT_USER",
                "DEACTIVATE_USER",
                "RESET_PASSWORD",
                "CHANGE_USERNAME",
                "NARCOTIC_DISPENSED"
        );
        actionFilter.setValue("All Actions");
        actionFilter.setPrefWidth(160);

        entityFilter.getItems().addAll(
                "All Entities",
                "users",
                "sales",
                "prescriptions",
                "medicines",
                "narcotic_register",
                "system"
        );
        entityFilter.setValue("All Entities");
        entityFilter.setPrefWidth(150);

        fromDatePicker.setPromptText("From");
        toDatePicker.setPromptText("To");

        Button filterBtn = new Button("Apply Filters");
        filterBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        filterBtn.setOnAction(e -> loadAuditEntries());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        HBox toolbar = new HBox(10, searchField, actionFilter, entityFilter, fromDatePicker, toDatePicker,
                filterBtn, spacer, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        return toolbar;
    }

    private HBox buildBottomToolbar(){
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> clearFilters());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadAuditEntries());

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        HBox bottomToolbar = new HBox(10, clearBtn, refreshBtn);
        bottomToolbar.setAlignment(Pos.BOTTOM_RIGHT);

        return bottomToolbar;
    }

    private void buildColumns() {
        TableColumn<AuditLogEntry, Number> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().id()));
        idCol.setMaxWidth(80);

        TableColumn<AuditLogEntry, LocalDateTime> timeCol = new TableColumn<>("Logged At");
        timeCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().loggedAt()));
        timeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DISPLAY_TIME.format(item));
            }
        });
        timeCol.setPrefWidth(150);

        TableColumn<AuditLogEntry, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(c -> new SimpleStringProperty(blankIfNull(c.getValue().username())));
        userCol.setPrefWidth(130);

        TableColumn<AuditLogEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(c -> new SimpleStringProperty(blankIfNull(c.getValue().action())));
        actionCol.setPrefWidth(140);

        TableColumn<AuditLogEntry, String> entityCol = new TableColumn<>("Entity");
        entityCol.setCellValueFactory(c -> new SimpleStringProperty(formatEntity(c.getValue())));
        entityCol.setPrefWidth(150);

        TableColumn<AuditLogEntry, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(blankIfNull(c.getValue().description())));
        descCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.length() > 90 ? item.substring(0, 87) + "..." : item);
            }
        });

        TableColumn<AuditLogEntry, String> ipCol = new TableColumn<>("IP");
        ipCol.setCellValueFactory(c -> new SimpleStringProperty(blankIfNull(c.getValue().ipAddress())));
        ipCol.setPrefWidth(110);

        table.getColumns().setAll(idCol, timeCol, userCol, actionCol, entityCol, descCol, ipCol);
    }

    private VBox buildDetailsPane() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 10;");

        Label title = new Label("Entry Details");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        selectedDescription.setEditable(false);
        selectedDescription.setWrapText(true);
        selectedDescription.setPrefRowCount(4);
        selectedDescription.setStyle("-fx-control-inner-background: #F9FAFB;");

        VBox rows = new VBox(8,
                detailRow("Action", selectedAction),
                detailRow("User", selectedUser),
                detailRow("Entity", selectedEntity),
                detailRow("IP Address", selectedIp),
                detailRow("Logged At", selectedTime));

        Label descLabel = new Label("Description");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        card.getChildren().addAll(title, rows, descLabel, selectedDescription);
        return card;
    }

    private void loadAuditEntries() {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, username, action, entity_type, entity_id, description, ip_address, logged_at "
                        + "FROM audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!searchField.getText().isBlank()) {
            sql.append(" AND (username LIKE ? OR action LIKE ? OR description LIKE ?)");
            String like = "%" + searchField.getText().trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        if (!"All Actions".equals(actionFilter.getValue())) {
            sql.append(" AND action = ?");
            params.add(actionFilter.getValue());
        }

        if (!"All Entities".equals(entityFilter.getValue())) {
            sql.append(" AND entity_type = ?");
            params.add(entityFilter.getValue());
        }

        if (fromDatePicker.getValue() != null) {
            sql.append(" AND logged_at >= ?");
            params.add(Timestamp.valueOf(fromDatePicker.getValue().atStartOfDay()));
        }

        if (toDatePicker.getValue() != null) {
            sql.append(" AND logged_at < ?");
            params.add(Timestamp.valueOf(toDatePicker.getValue().plusDays(1).atStartOfDay()));
        }

        sql.append(" ORDER BY logged_at DESC LIMIT ").append(DEFAULT_LIMIT);

        List<AuditLogEntry> entries = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                if (value instanceof Timestamp timestamp) {
                    ps.setTimestamp(i + 1, timestamp);
                } else {
                    ps.setObject(i + 1, value, Types.VARCHAR);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp timestamp = rs.getTimestamp("logged_at");
                    entries.add(new AuditLogEntry(
                            rs.getInt("id"),
                            nullableInt(rs, "user_id"),
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("entity_type"),
                            nullableInt(rs, "entity_id"),
                            rs.getString("description"),
                            rs.getString("ip_address"),
                            timestamp != null ? timestamp.toLocalDateTime() : null
                    ));
                }
            }

            table.setItems(FXCollections.observableArrayList(entries));
            if (entries.isEmpty()) {
                showDetails(null);
            } else {
                table.getSelectionModel().selectFirst();
            }
            statusLabel.setText(entries.size() + " entries loaded");
        } catch (Exception e) {
            table.getItems().clear();
            showDetails(null);
            statusLabel.setText("Load failed");
            AlertUtil.showError("Audit Log", "Could not load audit entries: " + e.getMessage());
        }
    }

    private void clearFilters() {
        searchField.clear();
        actionFilter.setValue("All Actions");
        entityFilter.setValue("All Entities");
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        loadAuditEntries();
    }

    private void showDetails(AuditLogEntry entry) {
        if (entry == null) {
            selectedAction.setText("-");
            selectedUser.setText("-");
            selectedEntity.setText("-");
            selectedIp.setText("-");
            selectedTime.setText("-");
            selectedDescription.clear();
            return;
        }

        selectedAction.setText(blankIfNull(entry.action()));
        selectedUser.setText(blankIfNull(entry.username()));
        selectedEntity.setText(formatEntity(entry));
        selectedIp.setText(blankIfNull(entry.ipAddress()));
        selectedTime.setText(entry.loggedAt() == null ? "-" : DISPLAY_TIME.format(entry.loggedAt()));
        selectedDescription.setText(blankIfNull(entry.description()));
    }

    private VBox detailRow(String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        return new VBox(2, label, valueLabel);
    }

    private static Label detailValue() {
        Label label = new Label("-");
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #111827; -fx-font-weight: bold;");
        return label;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String formatEntity(AuditLogEntry entry) {
        String entityType = blankIfNull(entry.entityType());
        return entry.entityId() == null ? entityType : entityType + " #" + entry.entityId();
    }

    private static String blankIfNull(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record AuditLogEntry(
            int id,
            Integer userId,
            String username,
            String action,
            String entityType,
            Integer entityId,
            String description,
            String ipAddress,
            LocalDateTime loggedAt
    ) {}
}

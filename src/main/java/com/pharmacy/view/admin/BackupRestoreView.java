package com.pharmacy.view.admin;

import com.pharmacy.scheduler.BackupScheduler;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.ViewManager;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class BackupRestoreView {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final TextField backupDirField = new TextField(AppConstants.DEFAULT_BACKUP_DIR);
    private final Label statusLabel = new Label("Ready");
    private final Label totalBackupsLabel = infoValue("0");
    private final Label latestBackupLabel = infoValue("-");
    private final Label selectedFileLabel = infoValue("No file selected");
    private final TextArea notesArea = new TextArea();
    private final TableView<BackupFileEntry> backupTable = new TableView<>();
    private Path selectedRestorePath;

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

        Label title = new Label("Backup & Restore");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label subtitle = new Label("Manage database backups, monitor backup files, and restore from a verified SQL dump.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        content.getChildren().addAll(title, subtitle, buildSummaryCards(), buildControlCard(), buildBackupTableCard());

        root.setCenter(content);
        refreshBackupList();
        return root;
    }

    private HBox buildSummaryCards() {
        HBox row = new HBox(14,
                infoCard("Backup Directory", backupDirField.textProperty(), "Default storage location"),
                infoCard("Available Backups", totalBackupsLabel.textProperty(), "SQL backups found in current directory"),
                infoCard("Latest Backup", latestBackupLabel.textProperty(), "Most recent file modification time"),
                infoCard("Status", statusLabel.textProperty(), "Backup and restore require MySQL CLI tools"));
        return row;
    }

    private HBox buildControlCard() {
        VBox card = createCard();

        backupDirField.setPrefWidth(380);

        Button browseDirBtn = new Button("Browse");
        browseDirBtn.setOnAction(e -> chooseDirectory());

        Button manualBackupBtn = new Button("Run Backup");
        manualBackupBtn.setStyle(primaryButtonStyle());
        manualBackupBtn.setOnAction(e -> runManualBackup());

        Button chooseRestoreBtn = new Button("Select SQL File");
        chooseRestoreBtn.setOnAction(e -> chooseRestoreFile());

        Button restoreSelectedBtn = new Button("Restore Selected");
        restoreSelectedBtn.setStyle(dangerButtonStyle());
        restoreSelectedBtn.setOnAction(e -> restoreSelectedBackup());

        Button refreshBtn = new Button("Refresh Files");
        refreshBtn.setOnAction(e -> refreshBackupList());

        HBox dirRow = new HBox(10, formLabel("Backup Directory"), backupDirField, browseDirBtn);
        dirRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(10, manualBackupBtn, chooseRestoreBtn, restoreSelectedBtn, refreshBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(4);
        notesArea.setText(
                "Use manual backup before high-risk admin actions.\n" +
                "Restore replaces the active database contents with the selected SQL dump.\n" +
                "Ensure mysqldump and mysql are available on the machine PATH."
        );

        notesArea.setStyle("-fx-control-inner-background: #F9FAFB;");

        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        card.getChildren().addAll(
                sectionTitle("Backup Controls"),
                dirRow,
                actionRow,
                detailRow("Selected Restore File", selectedFileLabel),
                detailRow("Status", statusLabel),
                notesArea
        );

        VBox.setVgrow(notesArea, Priority.ALWAYS);

        HBox wrapper = new HBox(card);
        HBox.setHgrow(card, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildBackupTableCard() {
        VBox card = createCard();
        card.getChildren().add(sectionTitle("Available Backups"));

        backupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        backupTable.setPlaceholder(new Label("No backup files found in the selected directory."));
        buildColumns();
        VBox.setVgrow(backupTable, Priority.ALWAYS);

        backupTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                selectedFileLabel.setText("No file selected");
                selectedRestorePath = null;
            } else {
                selectedFileLabel.setText(newValue.path().toString());
                selectedRestorePath = newValue.path();
            }
        });

        card.getChildren().add(backupTable);
        return card;
    }

    private void buildColumns() {
        TableColumn<BackupFileEntry, String> nameCol = new TableColumn<>("File");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(260);

        TableColumn<BackupFileEntry, Number> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().sizeBytes()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : humanSize(item.longValue()));
            }
        });
        sizeCol.setPrefWidth(100);

        TableColumn<BackupFileEntry, LocalDateTime> modifiedCol = new TableColumn<>("Modified");
        modifiedCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().modifiedAt()));
        modifiedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DISPLAY_TIME.format(item));
            }
        });
        modifiedCol.setPrefWidth(160);

        TableColumn<BackupFileEntry, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path().toString()));

        backupTable.getColumns().setAll(nameCol, sizeCol, modifiedCol, pathCol);
    }

    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Directory");

        Path current = currentBackupDir();
        if (Files.exists(current)) {
            chooser.setInitialDirectory(current.toFile());
        }

        Stage stage = ViewManager.getInstance().getPrimaryStage();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            backupDirField.setText(selected.getAbsolutePath());
            refreshBackupList();
        }
    }

    private void chooseRestoreFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup SQL File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Backup", "*.sql"));

        Path current = currentBackupDir();
        if (Files.exists(current)) {
            chooser.setInitialDirectory(current.toFile());
        }

        Stage stage = ViewManager.getInstance().getPrimaryStage();
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            BackupFileEntry entry = createEntry(selected.toPath());
            if (entry != null) {
                selectedRestorePath = entry.path();
                selectedFileLabel.setText(entry.path().toString());
                backupTable.getItems().stream()
                        .filter(item -> item.path().equals(entry.path()))
                        .findFirst()
                        .ifPresent(item -> backupTable.getSelectionModel().select(item));
            }
        }
    }

    private void runManualBackup() {
        Path backupDir = currentBackupDir();
        statusLabel.setText("Running backup...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return BackupScheduler.performBackup(backupDir.toString());
            }
        };

        task.setOnSucceeded(e -> {
            String outFile = task.getValue();
            statusLabel.setText("Backup completed");
            notesArea.setText("Backup completed successfully.\n\nCreated file:\n" + outFile);
            refreshBackupList();
            AlertUtil.showInfo("Backup Complete", "Backup saved to:\n" + outFile);
        });

        task.setOnFailed(e -> {
            Throwable error = task.getException();
            statusLabel.setText("Backup failed");
            notesArea.setText("Backup failed.\n\n" + (error != null ? error.getMessage() : "Unknown error"));
            AlertUtil.showError("Backup Failed", error != null ? error.getMessage() : "Unknown error");
        });

        VBox.setVgrow(notesArea, Priority.ALWAYS);

        startBackgroundTask(task);
    }

    private void restoreSelectedBackup() {
        Path restorePath = selectedRestorePath;
        if (restorePath == null) {
            BackupFileEntry selected = backupTable.getSelectionModel().getSelectedItem();
            restorePath = selected != null ? selected.path() : null;
        }

        if (restorePath == null) {
            AlertUtil.showError("Restore", "Select a backup file first.");
            return;
        }

        boolean confirm = AlertUtil.showConfirm(
                "Restore Database",
                "Restore from " + restorePath.getFileName() + "?\n\nThis will overwrite the current database."
        );
        if (!confirm) {
            return;
        }

        Path finalRestorePath = restorePath;
        statusLabel.setText("Running restore...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                restoreBackup(finalRestorePath);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText("Restore completed");
            notesArea.setText("Restore completed successfully from:\n" + finalRestorePath);
            AlertUtil.showInfo("Restore Complete", "Database restore finished successfully.");
        });

        task.setOnFailed(e -> {
            Throwable error = task.getException();
            statusLabel.setText("Restore failed");
            notesArea.setText("Restore failed.\n\n" + (error != null ? error.getMessage() : "Unknown error"));
            AlertUtil.showError("Restore Failed", error != null ? error.getMessage() : "Unknown error");
        });

        VBox.setVgrow(notesArea, Priority.ALWAYS);

        startBackgroundTask(task);
    }

    private void refreshBackupList() {
        Path backupDir = currentBackupDir();
        try {
            Files.createDirectories(backupDir);
            List<BackupFileEntry> entries;
            try (Stream<Path> files = Files.list(backupDir)) {
                entries = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".sql"))
                        .map(this::createEntry)
                        .filter(entry -> entry != null)
                        .sorted(Comparator.comparing(BackupFileEntry::modifiedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
            }

            backupTable.setItems(FXCollections.observableArrayList(entries));
            totalBackupsLabel.setText(String.valueOf(entries.size()));
            latestBackupLabel.setText(entries.isEmpty() ? "-" : DISPLAY_TIME.format(entries.get(0).modifiedAt()));
            statusLabel.setText("Loaded " + entries.size() + " backup file(s)");

            if (selectedRestorePath != null) {
                Path currentSelection = selectedRestorePath;
                backupTable.getItems().stream()
                        .filter(item -> item.path().equals(currentSelection))
                        .findFirst()
                        .ifPresent(item -> backupTable.getSelectionModel().select(item));
            }
        } catch (Exception e) {
            backupTable.getItems().clear();
            totalBackupsLabel.setText("0");
            latestBackupLabel.setText("-");
            statusLabel.setText("Backup listing failed");
            notesArea.setText("Could not load backup files.\n\n" + e.getMessage());
        }
        VBox.setVgrow(notesArea, Priority.ALWAYS);

    }

    private void restoreBackup(Path backupFile) throws Exception {
        Properties props = loadDbProperties();

        String url = props.getProperty("db.url", "");
        String user = props.getProperty("db.user", "root");
        String password = props.getProperty("db.password", "");
        String host = props.getProperty("db.host", "localhost");
        String port = props.getProperty("db.port", "3306");
        String dbName = extractDbName(url);

        ProcessBuilder pb = new ProcessBuilder(
                "mysql",
                "-h", host,
                "-P", port,
                "-u", user,
                dbName
        );
        pb.environment().put("MYSQL_PWD", password);
        pb.redirectInput(backupFile.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes());
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Restore interrupted", e);
        }

        if (exitCode != 0) {
            AuditLogger.log("DB_RESTORE_FAILED", "system", null, "Restore failed from " + backupFile);
            throw new IOException("mysql restore failed (exit " + exitCode + "): " + output);
        }

        AuditLogger.log("DB_RESTORE", "system", null, "Restore completed from " + backupFile);
    }

    private void startBackgroundTask(Task<?> task) {
        Thread worker = new Thread(task, "backup-restore-view");
        worker.setDaemon(true);
        worker.start();
    }

    private Properties loadDbProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/db.properties")) {
            if (is == null) {
                throw new IOException("db.properties not found on classpath");
            }
            props.load(is);
            return props;
        }
    }

    private String extractDbName(String url) {
        String dbName = "pharmacy_db";
        int idx = url.lastIndexOf('/');
        if (idx >= 0) {
            int q = url.indexOf('?', idx);
            dbName = q > 0 ? url.substring(idx + 1, q) : url.substring(idx + 1);
        }
        return dbName;
    }

    private BackupFileEntry createEntry(Path path) {
        try {
            long size = Files.size(path);
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            LocalDateTime modifiedAt = LocalDateTime.ofInstant(modified, ZoneId.systemDefault());
            return new BackupFileEntry(path.getFileName().toString(), path, size, modifiedAt);
        } catch (IOException e) {
            return null;
        }
    }

    private Path currentBackupDir() {
        String value = backupDirField.getText() == null || backupDirField.getText().isBlank()
                ? AppConstants.DEFAULT_BACKUP_DIR
                : backupDirField.getText().trim();
        return Paths.get(value);
    }

    private VBox infoCard(String title, javafx.beans.value.ObservableValue<String> value, String subtitle) {
        VBox card = createCard();
        card.setPrefWidth(220);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(value);
        valueLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        valueLabel.setWrapText(true);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4B5563;");
        subtitleLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, valueLabel, subtitleLabel);
        return card;
    }

    private VBox createCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 10;");
        return card;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        return label;
    }

    private HBox detailRow(String labelText, Label valueLabel) {
        Label label = formLabel(labelText);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, label, spacer, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        return label;
    }

    private static Label infoValue(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        label.setWrapText(true);
        return label;
    }

    private String primaryButtonStyle() {
        return "-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;";
    }

    private String dangerButtonStyle() {
        return "-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;";
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    private record BackupFileEntry(String name, Path path, long sizeBytes, LocalDateTime modifiedAt) {}
}

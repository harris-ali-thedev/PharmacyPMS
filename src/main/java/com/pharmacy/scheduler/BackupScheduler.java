package com.pharmacy.scheduler;

import com.pharmacy.util.AppConstants;
import com.pharmacy.util.AuditLogger;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Database backup logic, extracted for use by both the scheduler
 * and the manual backup button in the Admin UI.
 */
public class BackupScheduler {

    private static final Logger LOG = Logger.getLogger(BackupScheduler.class.getName());

    public static String performBackup(String backupDir) throws IOException {
        Properties props = new Properties();
        try (InputStream is = BackupScheduler.class.getResourceAsStream("/db.properties")) {
            if (is == null) throw new IOException("db.properties not found on classpath");
            props.load(is);
        }

        String url      = config(props, "db.url", "PHARMACY_DB_URL", "");
        String user     = config(props, "db.user", "PHARMACY_DB_USER", "root");
        String password = config(props, "db.password", "PHARMACY_DB_PASSWORD", "");
        var endpoint = com.pharmacy.util.MySqlEndpoint.fromJdbcUrl(url);
        String host=endpoint.host(), port=endpoint.port(), dbName=endpoint.database();

        // Ensure backup directory exists
        Files.createDirectories(Paths.get(backupDir));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + "_" + UUID.randomUUID();
        String outFile   = backupDir + File.separator + dbName + "_backup_" + timestamp + ".sql";

        List<String> command = new ArrayList<>(Arrays.asList(
                "mysqldump",
                "-h", host,
                "-P", port,
                "-u", user,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--add-drop-database",
                "--databases", dbName
        ));

        // Add password securely via environment variable to avoid it appearing in process list
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("MYSQL_PWD", password);
        pb.redirectOutput(new File(outFile));
        pb.redirectErrorStream(false);

        Process process = pb.start();
        String errOutput = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Backup interrupted", e);
        }

        if (exitCode != 0) {
            Files.deleteIfExists(Path.of(outFile));
            throw new IOException("mysqldump failed (exit " + exitCode + "): " + errOutput);
        }

        // Prune old backups beyond retention period
        pruneOldBackups(backupDir, dbName);

        AuditLogger.log("DB_BACKUP", "system", null, "Backup succeeded: " + outFile);
        LOG.info("Backup saved: " + outFile);
        return outFile;
    }

    private static String config(Properties props, String property, String env, String fallback) {
        String value = System.getenv(env);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return props.getProperty(property, fallback);
    }

    private static void pruneOldBackups(String backupDir, String dbName) {
        Path dir = Paths.get(backupDir);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files
                    .filter(p -> p.getFileName().toString().startsWith(dbName + "_backup_"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }))
                    .toList();

            // Keep only last N backups
            int keep = AppConstants.BACKUP_RETENTION_COUNT;
            if (backups.size() > keep) {
                for (int i = 0; i < backups.size() - keep; i++) {
                    Files.deleteIfExists(backups.get(i));
                    LOG.info("Pruned old backup: " + backups.get(i));
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Backup pruning failed", e);
        }
    }
}

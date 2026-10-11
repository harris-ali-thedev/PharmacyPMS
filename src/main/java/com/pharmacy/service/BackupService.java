package com.pharmacy.service;

import com.pharmacy.util.AuditLogger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupService {

    private static BackupService instance;
    private ScheduledExecutorService scheduler;
    private String backupDir;

    private BackupService() {}

    public static BackupService getInstance() {
        if (instance == null) instance = new BackupService();
        return instance;
    }

    public void start(String backupDirectory) {
        this.backupDir = backupDirectory;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackupService");
            t.setDaemon(true);
            return t;
        });
        // Initial delay: 0, period: 24h
        scheduler.scheduleAtFixedRate(this::performBackup, 0, 24, TimeUnit.HOURS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void performBackup() {
        try {
            Properties props = new Properties();
            try (InputStream is = getClass().getResourceAsStream("/db.properties")) {
                if (is == null) return;
                props.load(is);
            }

            String url  = props.getProperty("db.url", "");
            String user = props.getProperty("db.user", "root");
            String pass = props.getProperty("db.password", "");


            // Extract database name from URL
            String dbName = "pharmacy_db";
            int idx = url.lastIndexOf('/');
            int q   = url.indexOf('?', idx);
            if (idx >= 0) {
                dbName = q > 0 ? url.substring(idx + 1, q) : url.substring(idx + 1);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String outFile   = backupDir + File.separator + dbName + "_backup_" + timestamp + ".sql";

            ProcessBuilder pb = new ProcessBuilder(
                    "G:\\Mysql\\MySQL Server 8.0\\bin\\mysqldump.exe",
                    "-u", user,
                    "-p" + pass,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    dbName
            );
            pb.redirectOutput(new File(outFile));
            pb.redirectErrorStream(false);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                AuditLogger.log("DB_BACKUP", "system", null, "Backup succeeded: " + outFile);
                System.out.println("[BackupService] Backup saved: " + outFile);
            } else {
                AuditLogger.log("DB_BACKUP_FAILED", "system", null, "Backup failed with exit code " + exitCode);
                System.err.println("[BackupService] Backup failed, exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("[BackupService] Error: " + e.getMessage());
        }
    }
}

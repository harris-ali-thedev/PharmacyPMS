package com.pharmacy.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database connection manager.
 * Reads defaults from db.properties and allows environment variables to override
 * credentials in production: PHARMACY_DB_URL, PHARMACY_DB_USER, PHARMACY_DB_PASSWORD.
 */
public class DatabaseConnection {

    private static volatile DatabaseConnection instance;
    private com.zaxxer.hikari.HikariDataSource pool;

    private static final String PROPS_FILE = "/db.properties";

    private DatabaseConnection() {}

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (pool == null) initializePool();
        return pool.getConnection();
    }

    private void initializePool() throws SQLException {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream(PROPS_FILE)) {
            if (is == null) {
                throw new SQLException("db.properties not found on classpath.");
            }
            props.load(is);
        } catch (Exception e) {
            throw new SQLException("Failed to load db.properties: " + e.getMessage(), e);
        }

        String url  = getConfigValue(props, "db.url", "PHARMACY_DB_URL");
        String user = getConfigValue(props, "db.user", "PHARMACY_DB_USER");
        String pass = getConfigValue(props, "db.password", "PHARMACY_DB_PASSWORD");

        if (url == null || url.isBlank()) {
            throw new SQLException("Database URL is not configured. Set db.url or PHARMACY_DB_URL.");
        }
        if (user == null || user.isBlank()) {
            throw new SQLException("Database user is not configured. Set db.user or PHARMACY_DB_USER.");
        }

        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setPoolName("PharmacyPool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        try { pool = new com.zaxxer.hikari.HikariDataSource(config); }
        catch (RuntimeException ex) { throw new SQLException("Could not initialize database pool", ex); }
    }

    public synchronized void close() {
        if (pool != null) { pool.close(); pool = null; }
    }

    private String getConfigValue(Properties props, String propertyName, String environmentName) {
        String value = System.getenv(environmentName);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return props.getProperty(propertyName);
    }
}

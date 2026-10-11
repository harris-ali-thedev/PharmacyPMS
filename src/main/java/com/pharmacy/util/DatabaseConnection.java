package com.pharmacy.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton database connection manager.
 * Reads connection settings from db.properties on the classpath.
 */
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private Connection connection;

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

    public Connection getConnection() throws SQLException {
//        if (connection == null || connection.isClosed()) {
//            connection = createConnection();
//        }
        connection = createConnection();
        return connection;
    }

    private Connection createConnection() throws SQLException {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream(PROPS_FILE)) {
            if (is == null) {
                throw new SQLException("db.properties not found on classpath.");
            }
            props.load(is);
        } catch (Exception e) {
            throw new SQLException("Failed to load db.properties: " + e.getMessage(), e);
        }

        String url  = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String pass = props.getProperty("db.password");

        return DriverManager.getConnection(url, user, pass);
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}

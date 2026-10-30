package com.pharmacy.service;

import com.pharmacy.util.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public final class SettingsService {
    public enum TaxMode { NO_TAX, TAX_EXCLUSIVE }
    public record Settings(String storeName, TaxMode taxMode, int timeoutMinutes) {}
    private static volatile Settings current = new Settings(AppConstants.PHARMACY_NAME, TaxMode.NO_TAX, AppConstants.SESSION_TIMEOUT_MINUTES);
    public static Settings current() { return current; }
    public Settings load() throws SQLException {
        Map<String,String> values=new HashMap<>();
        try(var con=DatabaseConnection.getInstance().getConnection();var ps=con.prepareStatement("SELECT setting_key,setting_value FROM settings");var rs=ps.executeQuery()) {
            while(rs.next())values.put(rs.getString(1),rs.getString(2));
        }
        Settings settings=new Settings(values.getOrDefault("store_name",AppConstants.PHARMACY_NAME),
                TaxMode.valueOf(values.getOrDefault("tax_mode","NO_TAX")), Integer.parseInt(values.getOrDefault("session_timeout_minutes","15")));
        validate(settings); apply(settings); return settings;
    }
    public void save(Settings settings) throws SQLException {
        SessionManager.getInstance().requireRole("ADMIN","MAIN_ADMIN"); validate(settings);
        try(var con=DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try(var ps=con.prepareStatement("INSERT INTO settings(setting_key,setting_value) VALUES(?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)")) {
                String[][] pairs={{"store_name",settings.storeName().trim()},{"tax_mode",settings.taxMode().name()},{"session_timeout_minutes",Integer.toString(settings.timeoutMinutes())}};
                for(var pair:pairs) { ps.setString(1,pair[0]); ps.setString(2,pair[1]); ps.addBatch(); }
                ps.executeBatch(); con.commit();
            } catch(Exception ex) { con.rollback(); throw ex; }
        }
        apply(settings);
        AuditLogger.log("SETTINGS_UPDATED","settings",null,"Billing and session settings updated");
    }
    private static void validate(Settings settings) {
        if(settings.storeName()==null || settings.storeName().isBlank() || settings.storeName().length()>150
                || settings.taxMode()==null || settings.timeoutMinutes()<1 || settings.timeoutMinutes()>240)
            throw new IllegalArgumentException("Enter a store name (up to 150 characters) and timeout between 1 and 240 minutes.");
    }
    private static void apply(Settings settings) { current=settings; SessionManager.getInstance().setTimeoutMinutes(settings.timeoutMinutes()); }
}

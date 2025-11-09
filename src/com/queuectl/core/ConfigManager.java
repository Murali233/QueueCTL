package com.queuectl.core;

import com.queuectl.db.DatabaseManager;

import java.sql.*;

public class ConfigManager {
    private DatabaseManager dbManager;

    public ConfigManager() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public String getConfig(String key) throws SQLException {
        String sql = "SELECT config_value FROM config WHERE config_key = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String value = rs.getString("config_value");
                rs.close();
                return value;
            }
            rs.close();
            return null;
        }
    }

    public int getConfigInt(String key, int defaultValue) {
        try {
            String value = getConfig(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (SQLException | NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setConfig(String key, String value) throws SQLException {
        String sql = "INSERT INTO config (config_key, config_value) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE config_value = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, value);
            stmt.executeUpdate();
        }
    }

    public void listConfig() throws SQLException {
        String sql = "SELECT config_key, config_value FROM config ORDER BY config_key";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            System.out.println("\n=== Configuration ===");
            while (rs.next()) {
                System.out.printf("%s = %s\n", rs.getString("config_key"), rs.getString("config_value"));
            }
            System.out.println();
        }
    }
}

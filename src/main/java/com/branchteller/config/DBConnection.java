package com.branchteller.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DB_URL      = envOr("DB_URL", "jdbc:mysql://localhost:3306/branch_teller");
    private static final String DB_USER     = envOr("DB_USER", "root");
    private static final String DB_PASSWORD = envOr("DB_PASSWORD", "admin123");

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}

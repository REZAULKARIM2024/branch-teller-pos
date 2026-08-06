package com.branchteller.dao;

import com.branchteller.config.DBConnection;
import com.branchteller.model.Customer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDAO {

    public Optional<Customer> findByPhone(String phone) throws SQLException {
        String sql = "SELECT * FROM customers WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<Customer> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM customers ORDER BY customer_id DESC";
        List<Customer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public int create(Connection conn, Customer c) throws SQLException {
        String sql = "INSERT INTO customers (full_name, phone, email, address, kyc_status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getFullName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setString(5, c.getKycStatus() == null ? "PENDING" : c.getKycStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void updateKycStatus(Connection conn, int customerId, String status) throws SQLException {
        String sql = "UPDATE customers SET kyc_status = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateCreditScore(Connection conn, int customerId, int score) throws SQLException {
        String sql = "UPDATE customers SET credit_score = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, score);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public Optional<Customer> findById(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT * FROM customers WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("customer_id"));
        c.setFullName(rs.getString("full_name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setKycStatus(rs.getString("kyc_status"));
        try {
            int score = rs.getInt("credit_score");
            c.setCreditScore(rs.wasNull() ? null : score);
        } catch (SQLException ignore) {
            // column not present yet (pre-Phase-17 schema)
        }
        return c;
    }
}

package com.branchteller.dao;

import com.branchteller.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public int insert(Connection conn, Notification n) throws SQLException {
        String sql = "INSERT INTO notifications (customer_id, channel, subject, message, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, n.getCustomerId());
            ps.setString(2, n.getChannel());
            ps.setString(3, n.getSubject());
            ps.setString(4, n.getMessage());
            ps.setString(5, n.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<Notification> findRecent(Connection conn, int limit) throws SQLException {
        String sql = "SELECT n.*, c.full_name AS customer_name FROM notifications n " +
                "JOIN customers c ON c.customer_id = n.customer_id ORDER BY n.created_at DESC LIMIT ?";
        List<Notification> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<Notification> findByCustomer(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT n.*, c.full_name AS customer_name FROM notifications n " +
                "JOIN customers c ON c.customer_id = n.customer_id WHERE n.customer_id = ? ORDER BY n.created_at DESC";
        List<Notification> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("notification_id"));
        n.setCustomerId(rs.getInt("customer_id"));
        n.setCustomerName(rs.getString("customer_name"));
        n.setChannel(rs.getString("channel"));
        n.setSubject(rs.getString("subject"));
        n.setMessage(rs.getString("message"));
        n.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) n.setCreatedAt(createdAt.toLocalDateTime());
        return n;
    }
}

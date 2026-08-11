package com.branchteller.dao;

import com.branchteller.model.Complaint;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintDAO {

    /** QA finding (fixed): ComplaintService's assign()/resolve()/close() used to update a
     *  complaint by ID with no way to check whether it existed or what state it was currently
     *  in -- an unknown ID silently updated zero rows, and there was nothing to stop
     *  "reassigning" or "re-resolving" a complaint that had already been CLOSED. This lookup
     *  lets the service check both before mutating. */
    public Optional<Complaint> findById(Connection conn, int complaintId) throws SQLException {
        String sql = "SELECT * FROM complaints WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBasic(rs));
            }
        }
        return Optional.empty();
    }

    public int insert(Connection conn, Complaint c) throws SQLException {
        String sql = "INSERT INTO complaints (customer_id, category, description, priority) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getCustomerId());
            ps.setString(2, c.getCategory());
            ps.setString(3, c.getDescription());
            ps.setString(4, c.getPriority());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void assign(Connection conn, int complaintId, int assignedTo) throws SQLException {
        String sql = "UPDATE complaints SET assigned_to = ?, status = 'IN_PROGRESS' WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assignedTo);
            ps.setInt(2, complaintId);
            ps.executeUpdate();
        }
    }

    public void resolve(Connection conn, int complaintId, String resolutionNote) throws SQLException {
        String sql = "UPDATE complaints SET status = 'RESOLVED', resolved_at = NOW(), resolution_note = ? WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolutionNote);
            ps.setInt(2, complaintId);
            ps.executeUpdate();
        }
    }

    public void close(Connection conn, int complaintId) throws SQLException {
        String sql = "UPDATE complaints SET status = 'CLOSED' WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.executeUpdate();
        }
    }

    public List<Complaint> findAll(Connection conn) throws SQLException {
        String sql = "SELECT c.*, cu.full_name AS customer_name, u.full_name AS assigned_to_name FROM complaints c " +
                "JOIN customers cu ON cu.customer_id = c.customer_id " +
                "LEFT JOIN users u ON u.user_id = c.assigned_to ORDER BY c.created_at DESC";
        List<Complaint> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    /** Bare row mapper for {@link #findById} -- unlike {@link #map}, this doesn't rely on the
     *  customer_name/assigned_to_name columns that only exist after {@link #findAll}'s JOINs, so
     *  it's safe to use against a plain {@code SELECT * FROM complaints}. */
    private Complaint mapBasic(ResultSet rs) throws SQLException {
        Complaint c = new Complaint();
        c.setId(rs.getInt("complaint_id"));
        c.setCustomerId(rs.getInt("customer_id"));
        c.setCategory(rs.getString("category"));
        c.setDescription(rs.getString("description"));
        c.setStatus(rs.getString("status"));
        c.setPriority(rs.getString("priority"));
        int assignedTo = rs.getInt("assigned_to");
        c.setAssignedTo(rs.wasNull() ? null : assignedTo);
        return c;
    }

    private Complaint map(ResultSet rs) throws SQLException {
        Complaint c = new Complaint();
        c.setId(rs.getInt("complaint_id"));
        c.setCustomerId(rs.getInt("customer_id"));
        c.setCustomerName(rs.getString("customer_name"));
        c.setCategory(rs.getString("category"));
        c.setDescription(rs.getString("description"));
        c.setStatus(rs.getString("status"));
        c.setPriority(rs.getString("priority"));
        int assignedTo = rs.getInt("assigned_to");
        c.setAssignedTo(rs.wasNull() ? null : assignedTo);
        c.setAssignedToName(rs.getString("assigned_to_name"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) c.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        if (resolvedAt != null) c.setResolvedAt(resolvedAt.toLocalDateTime());
        c.setResolutionNote(rs.getString("resolution_note"));
        return c;
    }
}

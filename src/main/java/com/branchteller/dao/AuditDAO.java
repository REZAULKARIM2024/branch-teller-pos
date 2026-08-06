package com.branchteller.dao;

import com.branchteller.model.AuditLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuditDAO {

    public void insert(Connection conn, AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_trail (actor_id, action, entity_type, entity_id, before_value, after_value) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (log.getActorId() != null) ps.setInt(1, log.getActorId());
            else ps.setNull(1, Types.INTEGER);
            ps.setString(2, log.getAction());
            ps.setString(3, log.getEntityType());
            if (log.getEntityId() != null) ps.setInt(4, log.getEntityId());
            else ps.setNull(4, Types.INTEGER);
            ps.setString(5, log.getBeforeValue());
            ps.setString(6, log.getAfterValue());
            ps.executeUpdate();
        }
    }

    public List<AuditLog> findRecent(Connection conn, int limit) throws SQLException {
        String sql = "SELECT at.*, u.full_name AS actor_name FROM audit_trail at " +
                "LEFT JOIN users u ON u.user_id = at.actor_id " +
                "ORDER BY at.created_at DESC LIMIT ?";
        List<AuditLog> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<AuditLog> findByEntityType(Connection conn, String entityType, int limit) throws SQLException {
        String sql = "SELECT at.*, u.full_name AS actor_name FROM audit_trail at " +
                "LEFT JOIN users u ON u.user_id = at.actor_id " +
                "WHERE at.entity_type = ? ORDER BY at.created_at DESC LIMIT ?";
        List<AuditLog> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private AuditLog map(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getInt("audit_id"));
        int actorId = rs.getInt("actor_id");
        log.setActorId(rs.wasNull() ? null : actorId);
        log.setAction(rs.getString("action"));
        log.setEntityType(rs.getString("entity_type"));
        int entityId = rs.getInt("entity_id");
        log.setEntityId(rs.wasNull() ? null : entityId);
        log.setBeforeValue(rs.getString("before_value"));
        log.setAfterValue(rs.getString("after_value"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) log.setCreatedAt(ts.toLocalDateTime());
        log.setActorName(rs.getString("actor_name"));
        return log;
    }
}

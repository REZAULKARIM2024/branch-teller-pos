package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AuditDAO;
import com.branchteller.model.AuditLog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AuditService {

    private final AuditDAO auditDAO = new AuditDAO();

    /** Opens its own connection -- for logging outside an existing transaction. */
    public void log(Integer actorId, String action, String entityType, Integer entityId,
                     String beforeValue, String afterValue) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            log(conn, actorId, action, entityType, entityId, beforeValue, afterValue);
        }
    }

    /** Uses the caller's existing connection/transaction -- so the audit row commits or
     *  rolls back together with the balance change it's describing. */
    public void log(Connection conn, Integer actorId, String action, String entityType, Integer entityId,
                     String beforeValue, String afterValue) throws SQLException {
        AuditLog entry = new AuditLog(actorId, action, entityType, entityId, beforeValue, afterValue);
        auditDAO.insert(conn, entry);
    }

    public List<AuditLog> recent(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return auditDAO.findRecent(conn, limit);
        }
    }

    public List<AuditLog> byEntityType(String entityType, int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return auditDAO.findByEntityType(conn, entityType, limit);
        }
    }
}

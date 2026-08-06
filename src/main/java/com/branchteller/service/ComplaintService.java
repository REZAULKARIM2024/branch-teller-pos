package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.ComplaintDAO;
import com.branchteller.model.Complaint;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class ComplaintService {

    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final AuditService auditService = new AuditService();

    public Complaint log(int customerId, String category, String description, String priority, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Complaint c = new Complaint();
            c.setCustomerId(customerId);
            c.setCategory(category);
            c.setDescription(description);
            c.setPriority(priority);
            int id = complaintDAO.insert(conn, c);
            c.setId(id);
            c.setStatus("OPEN");
            auditService.log(conn, actorId, "COMPLAINT_LOGGED", "complaint", id, null, category);
            return c;
        }
    }

    public void assign(int complaintId, int assignedTo, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            complaintDAO.assign(conn, complaintId, assignedTo);
            auditService.log(conn, actorId, "COMPLAINT_ASSIGNED", "complaint", complaintId, null, "assigned to user " + assignedTo);
        }
    }

    public void resolve(int complaintId, String resolutionNote, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            complaintDAO.resolve(conn, complaintId, resolutionNote);
            auditService.log(conn, actorId, "COMPLAINT_RESOLVED", "complaint", complaintId, null, resolutionNote);
        }
    }

    public void close(int complaintId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            complaintDAO.close(conn, complaintId);
            auditService.log(conn, actorId, "COMPLAINT_CLOSED", "complaint", complaintId, null, "CLOSED");
        }
    }

    public List<Complaint> all() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return complaintDAO.findAll(conn);
        }
    }
}

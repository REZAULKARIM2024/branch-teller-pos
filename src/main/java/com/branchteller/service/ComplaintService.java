package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.ComplaintDAO;
import com.branchteller.dao.CustomerDAO;
import com.branchteller.dao.UserDAO;
import com.branchteller.model.Complaint;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class ComplaintService {

    private static final Set<String> VALID_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int MAX_TEXT_LENGTH = 500; // matches complaints.description/resolution_note VARCHAR(500)

    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();

    public Complaint log(int customerId, String category, String description, String priority, int actorId) throws SQLException {
        // QA finding (fixed): none of this was validated before -- an unknown customerId only
        // failed with a raw FK-violation SQLException, a blank description was accepted (the GUI
        // checked, but any other caller -- the REST API, a future integration -- would not have
        // been protected), and an invalid priority string would have been silently accepted here
        // and only rejected later by MySQL's ENUM (with a confusing raw error), never by H2 in
        // tests since the test schema didn't enforce it either.
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (description.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Description cannot exceed " + MAX_TEXT_LENGTH + " characters");
        }
        if (priority == null || !VALID_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Priority must be one of " + VALID_PRIORITIES + ", got: " + priority);
        }
        try (Connection conn = DBConnection.getConnection()) {
            customerDAO.findById(conn, customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
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
            Complaint existing = requireComplaint(conn, complaintId);
            // QA finding (fixed): assign() had no state guard at all -- a CLOSED complaint (a
            // terminal state, same as a CANCELLED card or standing instruction elsewhere in this
            // app) could be reassigned and quietly flipped back to IN_PROGRESS, effectively
            // un-closing it.
            if ("CLOSED".equals(existing.getStatus())) {
                throw new IllegalStateException(
                        "Complaint #" + complaintId + " is CLOSED and cannot be reassigned; closing is final");
            }
            userDAO.findById(conn, assignedTo)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + assignedTo));
            complaintDAO.assign(conn, complaintId, assignedTo);
            auditService.log(conn, actorId, "COMPLAINT_ASSIGNED", "complaint", complaintId,
                    existing.getStatus(), "assigned to user " + assignedTo);
        }
    }

    public void resolve(int complaintId, String resolutionNote, int actorId) throws SQLException {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException("Resolution note is required");
        }
        if (resolutionNote.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Resolution note cannot exceed " + MAX_TEXT_LENGTH + " characters");
        }
        try (Connection conn = DBConnection.getConnection()) {
            Complaint existing = requireComplaint(conn, complaintId);
            // Same terminal-state rule as assign() above -- a CLOSED complaint can't be
            // "re-resolved" with a different note after the fact.
            if ("CLOSED".equals(existing.getStatus())) {
                throw new IllegalStateException(
                        "Complaint #" + complaintId + " is CLOSED and cannot be resolved again; closing is final");
            }
            complaintDAO.resolve(conn, complaintId, resolutionNote);
            auditService.log(conn, actorId, "COMPLAINT_RESOLVED", "complaint", complaintId,
                    existing.getStatus(), resolutionNote);
        }
    }

    public void close(int complaintId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Complaint existing = requireComplaint(conn, complaintId);
            // QA finding (fixed): close() could be called repeatedly on an already-CLOSED
            // complaint with no error and no-op effect, and -- worse -- on an unknown complaint
            // ID, which silently updated zero rows instead of telling the caller anything went
            // wrong.
            if ("CLOSED".equals(existing.getStatus())) {
                throw new IllegalStateException("Complaint #" + complaintId + " is already CLOSED");
            }
            complaintDAO.close(conn, complaintId);
            auditService.log(conn, actorId, "COMPLAINT_CLOSED", "complaint", complaintId,
                    existing.getStatus(), "CLOSED");
        }
    }

    public List<Complaint> all() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return complaintDAO.findAll(conn);
        }
    }

    private Complaint requireComplaint(Connection conn, int complaintId) throws SQLException {
        return complaintDAO.findById(conn, complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
    }
}

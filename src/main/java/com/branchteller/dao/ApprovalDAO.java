package com.branchteller.dao;

import com.branchteller.model.PendingApproval;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ApprovalDAO {

    public int insert(Connection conn, PendingApproval a) throws SQLException {
        String sql = "INSERT INTO pending_approvals (request_type, account_id, to_account_id, amount, requested_by, request_note) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.getRequestType());
            if (a.getAccountId() != null) ps.setInt(2, a.getAccountId()); else ps.setNull(2, Types.INTEGER);
            if (a.getToAccountId() != null) ps.setInt(3, a.getToAccountId()); else ps.setNull(3, Types.INTEGER);
            ps.setBigDecimal(4, a.getAmount());
            ps.setInt(5, a.getRequestedBy());
            ps.setString(6, a.getRequestNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public Optional<PendingApproval> findById(Connection conn, int approvalId) throws SQLException {
        String sql = "SELECT p.*, a.account_number, ta.account_number AS to_account_number, u.full_name AS requested_by_name " +
                "FROM pending_approvals p " +
                "LEFT JOIN accounts a ON a.account_id = p.account_id " +
                "LEFT JOIN accounts ta ON ta.account_id = p.to_account_id " +
                "JOIN users u ON u.user_id = p.requested_by WHERE p.approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, approvalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void decide(Connection conn, int approvalId, String status, int approvedBy, String decisionNote) throws SQLException {
        String sql = "UPDATE pending_approvals SET status = ?, approved_by = ?, decision_note = ?, decided_at = NOW() WHERE approval_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, approvedBy);
            ps.setString(3, decisionNote);
            ps.setInt(4, approvalId);
            ps.executeUpdate();
        }
    }

    public List<PendingApproval> findByStatus(Connection conn, String status) throws SQLException {
        String sql = "SELECT p.*, a.account_number, ta.account_number AS to_account_number, u.full_name AS requested_by_name " +
                "FROM pending_approvals p " +
                "LEFT JOIN accounts a ON a.account_id = p.account_id " +
                "LEFT JOIN accounts ta ON ta.account_id = p.to_account_id " +
                "JOIN users u ON u.user_id = p.requested_by " +
                "WHERE p.status = ? ORDER BY p.created_at DESC";
        List<PendingApproval> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private PendingApproval map(ResultSet rs) throws SQLException {
        PendingApproval a = new PendingApproval();
        a.setId(rs.getInt("approval_id"));
        a.setRequestType(rs.getString("request_type"));
        int accId = rs.getInt("account_id");
        a.setAccountId(rs.wasNull() ? null : accId);
        a.setAccountNumber(rs.getString("account_number"));
        int toAccId = rs.getInt("to_account_id");
        a.setToAccountId(rs.wasNull() ? null : toAccId);
        a.setToAccountNumber(rs.getString("to_account_number"));
        a.setAmount(rs.getBigDecimal("amount"));
        a.setRequestedBy(rs.getInt("requested_by"));
        a.setRequestedByName(rs.getString("requested_by_name"));
        a.setStatus(rs.getString("status"));
        int approvedBy = rs.getInt("approved_by");
        a.setApprovedBy(rs.wasNull() ? null : approvedBy);
        a.setRequestNote(rs.getString("request_note"));
        a.setDecisionNote(rs.getString("decision_note"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) a.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        if (decidedAt != null) a.setDecidedAt(decidedAt.toLocalDateTime());
        return a;
    }
}

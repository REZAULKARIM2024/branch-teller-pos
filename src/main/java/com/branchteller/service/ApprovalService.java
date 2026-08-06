package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.ApprovalDAO;
import com.branchteller.model.PendingApproval;
import com.branchteller.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Maker-checker dual control. Any WITHDRAW or TRANSFER whose amount exceeds the
 * requesting teller's approval_limit is queued here instead of being executed immediately.
 * A manager/admin ("checker") reviews the queue; approving actually performs the movement
 * via BankingService, rejecting simply closes the request with no funds movement.
 */
public class ApprovalService {

    private final ApprovalDAO approvalDAO = new ApprovalDAO();
    private final BankingService bankingService = new BankingService();
    private final AuditService auditService = new AuditService();

    public boolean requiresApproval(User requester, BigDecimal amount) {
        return amount.compareTo(requester.getApprovalLimit()) > 0;
    }

    public PendingApproval submitWithdrawal(int accountId, BigDecimal amount, int requestedBy, String note) throws SQLException {
        return submit("WITHDRAW", accountId, null, amount, requestedBy, note);
    }

    public PendingApproval submitTransfer(int fromAccountId, int toAccountId, BigDecimal amount, int requestedBy, String note) throws SQLException {
        return submit("TRANSFER", fromAccountId, toAccountId, amount, requestedBy, note);
    }

    private PendingApproval submit(String type, int accountId, Integer toAccountId, BigDecimal amount, int requestedBy, String note) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            PendingApproval a = new PendingApproval();
            a.setRequestType(type);
            a.setAccountId(accountId);
            a.setToAccountId(toAccountId);
            a.setAmount(amount);
            a.setRequestedBy(requestedBy);
            a.setRequestNote(note);
            int id = approvalDAO.insert(conn, a);
            a.setId(id);
            a.setStatus("PENDING");
            auditService.log(conn, requestedBy, "APPROVAL_REQUESTED", "pending_approval", id, null,
                    type + " $" + amount + " requires manager approval");
            return a;
        }
    }

    public List<PendingApproval> pending() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return approvalDAO.findByStatus(conn, "PENDING");
        }
    }

    public List<PendingApproval> history(String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return approvalDAO.findByStatus(conn, status);
        }
    }

    /** Approves and executes the queued movement. */
    public void approve(int approvalId, User approver, String decisionNote) throws SQLException, InsufficientFundsException {
        PendingApproval a;
        try (Connection conn = DBConnection.getConnection()) {
            a = approvalDAO.findById(conn, approvalId)
                    .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
            if (!"PENDING".equals(a.getStatus())) {
                throw new IllegalStateException("Request " + approvalId + " already decided");
            }
        }

        if ("WITHDRAW".equals(a.getRequestType())) {
            bankingService.withdraw(a.getAccountId(), a.getAmount(), a.getRequestedBy(),
                    "[Manager-approved] " + (a.getRequestNote() == null ? "" : a.getRequestNote()));
        } else if ("TRANSFER".equals(a.getRequestType())) {
            bankingService.transfer(a.getAccountId(), a.getToAccountId(), a.getAmount(), a.getRequestedBy(),
                    "[Manager-approved] " + (a.getRequestNote() == null ? "" : a.getRequestNote()));
        }

        try (Connection conn = DBConnection.getConnection()) {
            approvalDAO.decide(conn, approvalId, "APPROVED", approver.getId(), decisionNote);
            auditService.log(conn, approver.getId(), "APPROVAL_GRANTED", "pending_approval", approvalId, "PENDING", "APPROVED");
        }
    }

    public void reject(int approvalId, User approver, String decisionNote) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            approvalDAO.decide(conn, approvalId, "REJECTED", approver.getId(), decisionNote);
            auditService.log(conn, approver.getId(), "APPROVAL_REJECTED", "pending_approval", approvalId, "PENDING", "REJECTED");
        }
    }
}

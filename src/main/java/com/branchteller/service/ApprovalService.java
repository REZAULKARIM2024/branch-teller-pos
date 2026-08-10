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

    /** Approves and executes the queued movement.
     *
     * <p>The status transition is claimed atomically (an UPDATE ... WHERE status='PENDING')
     * BEFORE the money movement runs, so two concurrent approve() calls racing on the same
     * request can't both pass a stale check and both execute the withdrawal/transfer -- only
     * one caller's claim can succeed. If the claim fails, the request was already decided
     * (approved, rejected, or claimed by a concurrent approve() call right now) and this
     * throws immediately without touching any funds. If the claim succeeds but the money
     * movement then fails (e.g. insufficient funds), the claim is reverted back to PENDING
     * so the request isn't stuck in a decided state with no funds actually moved.
     *
     * <p>QA finding (fixed): the revert-on-failure catch below used to only catch {@code
     * SQLException} and {@code InsufficientFundsException} -- the two failure modes anticipated
     * when this was written. But {@code BankingService.withdraw}/{@code transfer} can also throw
     * an unchecked {@code IllegalArgumentException} (account not found, or -- since the CLOSED-
     * account guard was added -- the account being CLOSED), and that type slipped straight
     * through this catch, uncaught, leaving the request stuck claimed as APPROVED with no
     * revert and no funds moved: a real hole in the exact safety net this method's own javadoc
     * promises. Broadened to catch any {@code RuntimeException} too, so this guarantee actually
     * holds for every way the money movement can fail, not just the two originally anticipated
     * ones. */
    public void approve(int approvalId, User approver, String decisionNote) throws SQLException, InsufficientFundsException {
        PendingApproval a;
        try (Connection conn = DBConnection.getConnection()) {
            a = approvalDAO.findById(conn, approvalId)
                    .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
            if (!"PENDING".equals(a.getStatus())) {
                throw new IllegalStateException("Request " + approvalId + " already decided");
            }
            boolean claimed = approvalDAO.decide(conn, approvalId, "APPROVED", approver.getId(), decisionNote);
            if (!claimed) {
                throw new IllegalStateException("Request " + approvalId + " already decided");
            }
        }

        try {
            if ("WITHDRAW".equals(a.getRequestType())) {
                bankingService.withdraw(a.getAccountId(), a.getAmount(), a.getRequestedBy(),
                        "[Manager-approved] " + (a.getRequestNote() == null ? "" : a.getRequestNote()));
            } else if ("TRANSFER".equals(a.getRequestType())) {
                bankingService.transfer(a.getAccountId(), a.getToAccountId(), a.getAmount(), a.getRequestedBy(),
                        "[Manager-approved] " + (a.getRequestNote() == null ? "" : a.getRequestNote()));
            }
        } catch (RuntimeException | SQLException | InsufficientFundsException ex) {
            try (Connection conn = DBConnection.getConnection()) {
                approvalDAO.revertToPending(conn, approvalId);
            }
            throw ex;
        }

        try (Connection conn = DBConnection.getConnection()) {
            auditService.log(conn, approver.getId(), "APPROVAL_GRANTED", "pending_approval", approvalId, "PENDING", "APPROVED");
        }
    }

    /** Rejects a queued request -- no funds move. Guarded the same way as approve(): the
     *  request must exist and still be PENDING, and the status transition is claimed
     *  atomically so a reject() racing against a concurrent approve()/reject() on the same
     *  request can't silently overwrite an already-decided (and possibly already-executed)
     *  request. */
    public void reject(int approvalId, User approver, String decisionNote) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            PendingApproval a = approvalDAO.findById(conn, approvalId)
                    .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
            if (!"PENDING".equals(a.getStatus())) {
                throw new IllegalStateException("Request " + approvalId + " already decided");
            }
            boolean claimed = approvalDAO.decide(conn, approvalId, "REJECTED", approver.getId(), decisionNote);
            if (!claimed) {
                throw new IllegalStateException("Request " + approvalId + " already decided");
            }
            auditService.log(conn, approver.getId(), "APPROVAL_REJECTED", "pending_approval", approvalId, a.getStatus(), "REJECTED");
        }
    }
}

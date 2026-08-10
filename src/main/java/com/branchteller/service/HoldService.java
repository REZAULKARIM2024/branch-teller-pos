package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.HoldDAO;
import com.branchteller.model.Account;
import com.branchteller.model.AccountHold;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Account holds/liens. Available balance for withdrawals/transfers = ledger balance minus
 * the sum of all ACTIVE holds on the account (court order, fraud investigation, uncleared
 * cheque, etc). BankingService consults activeHoldsTotal() before releasing funds.
 */
public class HoldService {

    private final HoldDAO holdDAO = new HoldDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final AuditService auditService = new AuditService();

    /**
     * QA finding (fixed): this method used to insert a hold against any accountId at all --
     * never looking the account up, so a typo'd or made-up account ID would fail only with a
     * raw, confusing SQLException from the database's foreign-key constraint, instead of a clear
     * message. It also never validated {@code reason}, even though holds exist specifically for
     * "court order, fraud investigation" style compliance actions -- a blank reason would have
     * been accepted, leaving a legally/compliance-significant restriction on a customer's funds
     * with no record of why. Both fixed.
     *
     * <p>Deliberately NOT blocking CLOSED accounts here, unlike the money-moving features this
     * review already hardened (Teller Counter, Cheques, Loans): a hold doesn't move any money, it
     * only restricts future withdrawals -- which are already blocked entirely on a CLOSED account
     * regardless of holds. Refusing to let compliance staff record a fraud/court-order hold
     * against an account specifically because it's already closed would itself be a bug; the two
     * situations aren't the same shape even though the "CLOSED blocks" rule applies almost
     * everywhere else in this app.</p>
     *
     * <p>Also newly writes an audit trail entry -- HoldService never logged anything at all
     * before this review, a real gap for a feature whose entire purpose is compliance-sensitive
     * restrictions on customer funds.</p>
     */
    public AccountHold placeHold(int accountId, BigDecimal amount, String reason, int placedBy) throws SQLException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Hold amount must be positive");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to place a hold");

        try (Connection conn = DBConnection.getConnection()) {
            Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

            AccountHold h = new AccountHold();
            h.setAccountId(accountId);
            h.setAmount(amount);
            h.setReason(reason.trim());
            h.setPlacedBy(placedBy);
            int id = holdDAO.insert(conn, h);
            h.setId(id);
            h.setStatus("ACTIVE");

            auditService.log(conn, placedBy, "HOLD_PLACED", "account_hold", id, null,
                    "$" + amount + " on " + acct.getAccountNumber() + ": " + reason.trim());
            return h;
        }
    }

    /**
     * QA finding (fixed): used to call {@code holdDAO.release} completely unconditionally -- no
     * check that the hold even existed, and none that it was still ACTIVE. Releasing an unknown
     * hold ID silently updated zero rows with no error at all, misleading a manager into thinking
     * their release succeeded when nothing happened. Releasing an already-RELEASED hold a second
     * time (e.g. a double-click, or two staff racing on the same hold) silently overwrote {@code
     * released_at}/{@code released_by} with new values, corrupting exactly the audit-relevant
     * "who released this and when" fields a compliance review would rely on. Fixed with the same
     * exists-and-still-ACTIVE guard {@code ApprovalService}/{@code ChequeService}/{@code
     * LoanService} already use for their own status transitions. Also newly writes an audit
     * trail entry, for the same reason {@link #placeHold} does.
     */
    public void releaseHold(int holdId, int releasedBy) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            AccountHold hold = holdDAO.findById(conn, holdId)
                    .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));
            if (!"ACTIVE".equals(hold.getStatus())) {
                throw new IllegalStateException("Hold " + holdId + " is not active (current status: " + hold.getStatus() + ")");
            }
            holdDAO.release(conn, holdId, releasedBy);
            auditService.log(conn, releasedBy, "HOLD_RELEASED", "account_hold", holdId, "ACTIVE", "RELEASED");
        }
    }

    public BigDecimal activeHoldsTotal(Connection conn, int accountId) throws SQLException {
        return holdDAO.activeHoldsTotal(conn, accountId);
    }

    public BigDecimal activeHoldsTotal(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.activeHoldsTotal(conn, accountId);
        }
    }

    public List<AccountHold> activeHolds() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.findActive(conn);
        }
    }

    public List<AccountHold> byAccount(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.findByAccount(conn, accountId);
        }
    }
}

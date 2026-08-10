package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.ChequeDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Cheque;
import com.branchteller.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cheque deposit + clearing queue. Depositing a cheque does NOT move funds -- it just
 * queues it as PENDING (a real hold period). Clearing it credits the account and posts
 * a ledger transaction + audit entry; bouncing it just closes out the cheque with no
 * balance change. Both clear() and bounce() run in a single JDBC transaction alongside
 * the account/ledger update, same atomicity pattern as BankingService.
 */
public class ChequeService {

    private final ChequeDAO chequeDAO = new ChequeDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final AuditService auditService = new AuditService();

    /**
     * QA finding (fixed): this method used to insert a cheque against any accountId at all --
     * it never even looked the account up, let alone checked its status. A cheque could be
     * queued (and later cleared -- see {@link #clear}) against a CLOSED account, crediting funds
     * into an account that shouldn't be able to receive any money at all, exactly the same class
     * of bug this review already found and fixed in {@code BankingService}'s deposit/withdraw/
     * transfer. {@code ChequePanel}'s deposit form only reaches this after a successful account
     * lookup, so this was invisible from the GUI alone -- it only shows up when the account
     * happens to be CLOSED, or when this service is called directly. Fixed by looking the account
     * up here too and rejecting a CLOSED one, while still allowing DORMANT accounts to receive a
     * cheque (same reasoning as the Teller Counter fix: transacting is normally how a dormant
     * account gets reactivated).
     *
     * <p>Also newly rejects: a blank/null cheque number (previously only enforced by the GUI,
     * not the service itself), and depositing the exact same cheque number against the same
     * account a second time while an earlier deposit of it is still PENDING or already CLEARED --
     * there was nothing stopping the same physical cheque from being queued (and double-credited)
     * twice. A cheque that previously BOUNCED can still be re-deposited, since a genuinely
     * re-presented cheque is a normal, legitimate flow.</p>
     */
    public Cheque deposit(int accountId, String chequeNo, BigDecimal amount, int tellerId, String note) throws SQLException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Cheque amount must be positive");
        if (chequeNo == null || chequeNo.isBlank()) throw new IllegalArgumentException("Cheque number is required");

        try (Connection conn = DBConnection.getConnection()) {
            Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
            if ("CLOSED".equals(acct.getStatus())) {
                throw new IllegalArgumentException("Account " + acct.getAccountNumber()
                        + " is closed and can't receive a cheque deposit");
            }
            if (chequeDAO.existsActiveForAccount(conn, accountId, chequeNo)) {
                throw new IllegalArgumentException("Cheque #" + chequeNo
                        + " is already pending or cleared for this account");
            }

            Cheque cheque = new Cheque();
            cheque.setAccountId(accountId);
            cheque.setChequeNo(chequeNo);
            cheque.setAmount(amount);
            cheque.setTellerId(tellerId);
            cheque.setDepositDate(LocalDate.now());
            cheque.setNote(note);
            int id = chequeDAO.insert(conn, cheque);
            cheque.setId(id);
            cheque.setStatus("PENDING");
            auditService.log(conn, tellerId, "CHEQUE_DEPOSIT", "cheque", id, null, amount.toString());
            return cheque;
        }
    }

    public List<Cheque> pendingCheques() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return chequeDAO.findPending(conn);
        }
    }

    public void clear(int chequeId, int tellerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Cheque cheque = chequeDAO.findById(conn, chequeId)
                        .orElseThrow(() -> new IllegalArgumentException("Cheque not found: " + chequeId));
                if (!"PENDING".equals(cheque.getStatus())) {
                    throw new IllegalStateException("Cheque " + chequeId + " is not pending");
                }

                Account acct = accountDAO.findByIdForUpdate(conn, cheque.getAccountId())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + cheque.getAccountId()));

                // QA finding (fixed): guards against the account being closed *after* the cheque
                // was queued but *before* it clears -- the same "closed in the meantime" scenario
                // ApprovalService.approve() already had to handle for a queued withdrawal. Nothing
                // has been written yet at this point, so throwing here just leaves the cheque
                // sitting safely PENDING -- no revert-style cleanup needed, unlike the maker-checker
                // case, because the balance update below hasn't happened.
                if ("CLOSED".equals(acct.getStatus())) {
                    throw new IllegalArgumentException("Account " + acct.getAccountNumber()
                            + " is closed and can't receive a cleared cheque");
                }

                BigDecimal newBalance = acct.getBalance().add(cheque.getAmount());
                accountDAO.updateBalance(conn, acct.getId(), newBalance);

                Transaction txn = new Transaction(acct.getId(), "DEPOSIT", cheque.getAmount(), tellerId,
                        "Cheque #" + cheque.getChequeNo() + " cleared");
                txn.setBalanceAfter(newBalance);
                transactionDAO.insert(conn, txn);

                chequeDAO.updateStatus(conn, chequeId, "CLEARED", LocalDate.now());

                auditService.log(conn, tellerId, "CHEQUE_CLEARED", "cheque", chequeId, "PENDING", "CLEARED");
                auditService.log(conn, tellerId, "CHEQUE_CLEARED", "account", acct.getId(),
                        acct.getBalance().toString(), newBalance.toString());

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void bounce(int chequeId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Optional<Cheque> cheque = chequeDAO.findById(conn, chequeId);
            if (cheque.isEmpty() || !"PENDING".equals(cheque.get().getStatus())) {
                throw new IllegalStateException("Cheque " + chequeId + " is not pending");
            }
            chequeDAO.updateStatus(conn, chequeId, "BOUNCED", LocalDate.now());
            auditService.log(conn, actorId, "CHEQUE_BOUNCED", "cheque", chequeId, "PENDING", "BOUNCED");
        }
    }
}

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

    public Cheque deposit(int accountId, String chequeNo, BigDecimal amount, int tellerId, String note) throws SQLException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Cheque amount must be positive");

        try (Connection conn = DBConnection.getConnection()) {
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

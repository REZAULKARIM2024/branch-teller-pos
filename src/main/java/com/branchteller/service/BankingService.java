package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Core teller-counter operations. Deposit/withdraw/transfer are each wrapped in a single
 * JDBC transaction so the balance update, the transaction-ledger row, the audit-trail
 * entry, and any AML flag commit or roll back together -- mirrors POSService.checkout()'s
 * atomicity in the NY Coffee Co. POS project.
 */
public class BankingService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final AuditService auditService = new AuditService();
    private final AmlService amlService = new AmlService();
    private final GlService glService = new GlService();
    private final HoldService holdService = new HoldService();

    public Optional<Account> lookupAccount(String accountNumber) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return accountDAO.findByAccountNumber(conn, accountNumber);
        }
    }

    public BigDecimal availableBalance(int accountId, BigDecimal balance) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return balance.subtract(holdService.activeHoldsTotal(conn, accountId));
        }
    }

    public Transaction deposit(int accountId, BigDecimal amount, int tellerId, String note) throws SQLException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Deposit amount must be positive");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

                BigDecimal newBalance = acct.getBalance().add(amount);
                accountDAO.updateBalance(conn, accountId, newBalance);

                Transaction txn = new Transaction(accountId, "DEPOSIT", amount, tellerId, note);
                txn.setBalanceAfter(newBalance);
                int txnId = transactionDAO.insert(conn, txn);
                txn.setId(txnId);

                auditService.log(conn, tellerId, "DEPOSIT", "account", accountId,
                        acct.getBalance().toString(), newBalance.toString());
                amlService.checkAndFlag(conn, accountId, txnId, amount, "DEPOSIT");
                glService.post(conn, "1000", "1100", amount, txnId, "Deposit to account #" + accountId);

                conn.commit();
                return txn;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Transaction withdraw(int accountId, BigDecimal amount, int tellerId, String note)
            throws SQLException, InsufficientFundsException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

                BigDecimal held = holdService.activeHoldsTotal(conn, accountId);
                BigDecimal available = acct.getBalance().subtract(held);
                if (available.compareTo(amount) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient available funds: available " + available +
                                    " (balance " + acct.getBalance() + " - holds " + held + ") < requested " + amount);
                }

                BigDecimal newBalance = acct.getBalance().subtract(amount);
                accountDAO.updateBalance(conn, accountId, newBalance);

                Transaction txn = new Transaction(accountId, "WITHDRAW", amount, tellerId, note);
                txn.setBalanceAfter(newBalance);
                int txnId = transactionDAO.insert(conn, txn);
                txn.setId(txnId);

                auditService.log(conn, tellerId, "WITHDRAW", "account", accountId,
                        acct.getBalance().toString(), newBalance.toString());
                amlService.checkAndFlag(conn, accountId, txnId, amount, "WITHDRAW");
                glService.post(conn, "1100", "1000", amount, txnId, "Withdrawal from account #" + accountId);

                conn.commit();
                return txn;
            } catch (InsufficientFundsException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Moves funds between two accounts as one atomic transaction; records both ledger legs. */
    public void transfer(int fromAccountId, int toAccountId, BigDecimal amount, int tellerId, String note)
            throws SQLException, InsufficientFundsException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
        if (fromAccountId == toAccountId) throw new IllegalArgumentException("Cannot transfer to the same account");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock accounts in a fixed order (lowest id first) to avoid deadlocks
                int firstLock = Math.min(fromAccountId, toAccountId);
                int secondLock = Math.max(fromAccountId, toAccountId);
                accountDAO.findByIdForUpdate(conn, firstLock);
                accountDAO.findByIdForUpdate(conn, secondLock);

                Account from = accountDAO.findByIdForUpdate(conn, fromAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + fromAccountId));
                Account to = accountDAO.findByIdForUpdate(conn, toAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + toAccountId));

                BigDecimal held = holdService.activeHoldsTotal(conn, fromAccountId);
                BigDecimal available = from.getBalance().subtract(held);
                if (available.compareTo(amount) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient available funds: available " + available +
                                    " (balance " + from.getBalance() + " - holds " + held + ") < requested " + amount);
                }

                BigDecimal fromNewBalance = from.getBalance().subtract(amount);
                BigDecimal toNewBalance = to.getBalance().add(amount);
                accountDAO.updateBalance(conn, fromAccountId, fromNewBalance);
                accountDAO.updateBalance(conn, toAccountId, toNewBalance);

                Transaction outTxn = new Transaction(fromAccountId, "TRANSFER_OUT", amount, tellerId, note);
                outTxn.setBalanceAfter(fromNewBalance);
                int outId = transactionDAO.insert(conn, outTxn);

                Transaction inTxn = new Transaction(toAccountId, "TRANSFER_IN", amount, tellerId, note);
                inTxn.setBalanceAfter(toNewBalance);
                inTxn.setRelatedTxnId(outId);
                transactionDAO.insert(conn, inTxn);

                auditService.log(conn, tellerId, "TRANSFER_OUT", "account", fromAccountId,
                        from.getBalance().toString(), fromNewBalance.toString());
                auditService.log(conn, tellerId, "TRANSFER_IN", "account", toAccountId,
                        to.getBalance().toString(), toNewBalance.toString());
                amlService.checkAndFlag(conn, fromAccountId, outId, amount, "TRANSFER_OUT");

                conn.commit();
            } catch (InsufficientFundsException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}

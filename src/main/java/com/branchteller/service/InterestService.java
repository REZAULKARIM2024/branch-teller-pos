package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.InterestAccrualDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.InterestAccrual;
import com.branchteller.model.Transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly interest accrual for SAVINGS accounts. Idempotent per (account, period) via a
 * unique constraint on interest_accruals -- running the same period twice just skips
 * accounts already accrued. Each account's credit + ledger entry + accrual record is
 * posted in its own atomic transaction, so one bad account doesn't roll back the whole run.
 */
public class InterestService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final InterestAccrualDAO accrualDAO = new InterestAccrualDAO();
    private final AuditService auditService = new AuditService();
    private final GlService glService = new GlService();

    public static class AccrualResult {
        public final String accountNumber;
        public final BigDecimal amount;
        public final boolean skipped;

        AccrualResult(String accountNumber, BigDecimal amount, boolean skipped) {
            this.accountNumber = accountNumber;
            this.amount = amount;
            this.skipped = skipped;
        }
    }

    /** Runs accrual for every ACTIVE savings account for the given "YYYY-MM" period. */
    public List<AccrualResult> runMonthlyAccrual(String period, int postedByTellerId) throws SQLException {
        List<AccrualResult> results = new ArrayList<>();

        List<Account> savingsAccounts;
        try (Connection conn = DBConnection.getConnection()) {
            savingsAccounts = accountDAO.findActiveByType(conn, "SAVINGS");
        }

        for (Account acct : savingsAccounts) {
            results.add(accrueOne(acct.getId(), period, postedByTellerId));
        }
        return results;
    }

    private AccrualResult accrueOne(int accountId, String period, int postedByTellerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (accrualDAO.existsForPeriod(conn, accountId, period)) {
                    Account acct = accountDAO.findByIdForUpdate(conn, accountId).orElse(null);
                    conn.commit();
                    return new AccrualResult(acct == null ? "?" : acct.getAccountNumber(), BigDecimal.ZERO, true);
                }

                Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

                BigDecimal interest = calculateMonthlyInterest(acct.getBalance(), acct.getInterestRate());

                if (interest.signum() > 0) {
                    BigDecimal newBalance = acct.getBalance().add(interest);
                    accountDAO.updateBalance(conn, accountId, newBalance);

                    Transaction txn = new Transaction(accountId, "DEPOSIT", interest, postedByTellerId,
                            "Interest accrual " + period);
                    txn.setBalanceAfter(newBalance);
                    int txnId = transactionDAO.insert(conn, txn);
                    txn.setId(txnId);

                    auditService.log(conn, postedByTellerId, "INTEREST_ACCRUED", "account", accountId,
                            acct.getBalance().toString(), newBalance.toString());

                    // Interest paid to a depositor is both an expense to the bank and a growth in
                    // what the bank owes the customer -- debit Interest Expense(5000), credit
                    // Customer Deposits Control(1100), same pattern as every other money-movement
                    // service's glService.post() call, inside this account's own transaction.
                    glService.post(conn, "5000", "1100", interest, txnId,
                            "Interest accrual " + period + " - account #" + accountId);
                }

                InterestAccrual accrual = new InterestAccrual();
                accrual.setAccountId(accountId);
                accrual.setPeriod(period);
                accrual.setRateApplied(acct.getInterestRate());
                accrual.setAmount(interest);
                accrual.setPostedDate(LocalDate.now());
                accrualDAO.insert(conn, accrual);

                conn.commit();
                return new AccrualResult(acct.getAccountNumber(), interest, false);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<InterestAccrual> history(String period) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return accrualDAO.findByPeriod(conn, period);
        }
    }

    /** Simple simple-interest monthly accrual: balance * annualRate% / 12. */
    static BigDecimal calculateMonthlyInterest(BigDecimal balance, BigDecimal annualRatePercent) {
        MathContext mc = new MathContext(10);
        return balance.multiply(annualRatePercent, mc)
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
    }
}

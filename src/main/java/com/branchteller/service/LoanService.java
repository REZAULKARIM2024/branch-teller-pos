package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.LoanDAO;
import com.branchteller.dao.LoanRepaymentDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Loan;
import com.branchteller.model.LoanRepayment;
import com.branchteller.model.Transaction;
import com.branchteller.model.User;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Loan lifecycle: apply -> approve/reject -> disburse -> repay.
 * Disbursement credits the account, posts a ledger transaction, and generates the
 * EMI schedule, all in one transaction. Each repayment debits the account against the
 * next pending installment, also atomically. Every balance-changing step is audit-logged.
 */
public class LoanService {

    private final LoanDAO loanDAO = new LoanDAO();
    private final LoanRepaymentDAO repaymentDAO = new LoanRepaymentDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final AuditService auditService = new AuditService();
    private final GlService glService = new GlService();

    /**
     * QA finding (fixed): this method used to insert a loan application against any accountId
     * at all -- it never looked the account up, so a loan could be applied for (and, worse,
     * approved by a manager -- real review effort spent) against an account that was CLOSED or
     * didn't even exist, only to fail at {@link #disburse} time. Fixed by looking the account up
     * here and rejecting a CLOSED one up front, the same "CLOSED blocks, DORMANT doesn't" rule
     * established for Teller Counter/Cheques. Also newly rejects a negative interest rate, which
     * {@link #calculateEmi}'s standard amortization formula was never designed to handle
     * sensibly (zero is still fine -- it's handled as a flat, interest-free split).
     */
    public Loan apply(int customerId, int accountId, String loanType, BigDecimal principal,
                       BigDecimal interestRate, int tenureMonths) throws SQLException {
        if (principal.signum() <= 0) throw new IllegalArgumentException("Principal must be positive");
        if (tenureMonths <= 0) throw new IllegalArgumentException("Tenure must be positive");
        if (interestRate.signum() < 0) throw new IllegalArgumentException("Interest rate can't be negative");

        try (Connection conn = DBConnection.getConnection()) {
            Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
            requireNotClosed(acct, "have a loan applied against it");

            Loan loan = new Loan();
            loan.setCustomerId(customerId);
            loan.setAccountId(accountId);
            loan.setLoanType(loanType);
            loan.setPrincipal(principal);
            loan.setInterestRate(interestRate);
            loan.setTenureMonths(tenureMonths);
            loan.setAppliedDate(LocalDate.now());
            int id = loanDAO.insert(conn, loan);
            loan.setId(id);
            loan.setStatus("APPLIED");
            auditService.log(conn, null, "LOAN_APPLIED", "loan", id, null, principal.toString());
            return loan;
        }
    }

    public List<Loan> findByStatus(String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return loanDAO.findByStatus(conn, status);
        }
    }

    public List<Loan> findAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return loanDAO.findAll(conn);
        }
    }

    public List<LoanRepayment> schedule(int loanId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return repaymentDAO.findByLoanId(conn, loanId);
        }
    }

    /**
     * QA finding (fixed): this method -- and {@link #reject} -- used to call {@code
     * loanDAO.updateStatus} unconditionally, with no check on the loan's current status at all.
     * That meant an already-REJECTED loan could be "approved" afterward, an already-APPROVED (or
     * even already-DISBURSED) loan could be "approved" or "rejected" again, and every one of
     * those calls would write an audit entry unconditionally claiming the before-value was
     * "APPLIED" -- a lie for any loan that wasn't actually still APPLIED. This is exactly the
     * kind of state-machine guard {@link ApprovalService#approve} and {@link ChequeService#clear}
     * already have (both reject a decision on anything that isn't still PENDING) -- Loans was the
     * one place in the maker-checker family of features missing it.
     */
    public void approve(int loanId, int managerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Loan loan = loanDAO.findById(conn, loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
            if (!"APPLIED".equals(loan.getStatus())) {
                throw new IllegalStateException("Loan " + loanId + " is not awaiting approval (current status: "
                        + loan.getStatus() + ")");
            }
            loanDAO.updateStatus(conn, loanId, "APPROVED", managerId);
            auditService.log(conn, managerId, "LOAN_APPROVED", "loan", loanId, "APPLIED", "APPROVED");
        }
    }

    /** See {@link #approve}'s javadoc -- same missing state-machine guard, fixed the same way. */
    public void reject(int loanId, int managerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Loan loan = loanDAO.findById(conn, loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
            if (!"APPLIED".equals(loan.getStatus())) {
                throw new IllegalStateException("Loan " + loanId + " is not awaiting a decision (current status: "
                        + loan.getStatus() + ")");
            }
            loanDAO.updateStatus(conn, loanId, "REJECTED", managerId);
            auditService.log(conn, managerId, "LOAN_REJECTED", "loan", loanId, "APPLIED", "REJECTED");
        }
    }

    /** Same "CLOSED blocks, DORMANT doesn't" rule as {@code BankingService}'s helper of the same
     *  name -- duplicated locally since that one is private to its own class. */
    private void requireNotClosed(Account acct, String action) {
        if ("CLOSED".equals(acct.getStatus())) {
            throw new IllegalArgumentException(
                    "Account " + acct.getAccountNumber() + " is closed and cannot " + action);
        }
    }

    /** Credits the loan principal to the account, posts a ledger entry, and builds the EMI schedule. */
    public void disburse(int loanId, User teller) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Loan loan = loanDAO.findById(conn, loanId)
                        .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
                if (!"APPROVED".equals(loan.getStatus())) {
                    throw new IllegalStateException("Loan " + loanId + " is not approved");
                }

                Account acct = accountDAO.findByIdForUpdate(conn, loan.getAccountId())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + loan.getAccountId()));

                // QA finding (fixed): guards against the account being closed between
                // application/approval and disbursement -- same class of bug already found and
                // fixed for Teller Counter and Cheques. Nothing has been written yet at this
                // point, so throwing here just leaves the loan sitting safely APPROVED, ready to
                // retry once the account situation is resolved.
                requireNotClosed(acct, "receive a loan disbursement");

                BigDecimal newBalance = acct.getBalance().add(loan.getPrincipal());
                accountDAO.updateBalance(conn, acct.getId(), newBalance);

                Transaction txn = new Transaction(acct.getId(), "DEPOSIT", loan.getPrincipal(), teller.getId(),
                        "Loan #" + loanId + " disbursement");
                txn.setBalanceAfter(newBalance);
                int glTxnId = transactionDAO.insert(conn, txn);
                glService.post(conn, "1200", "1100", loan.getPrincipal(), glTxnId, "Loan #" + loanId + " disbursement");

                LocalDate disbursedDate = LocalDate.now();
                loanDAO.markDisbursed(conn, loanId, disbursedDate);

                BigDecimal emi = calculateEmi(loan.getPrincipal(), loan.getInterestRate(), loan.getTenureMonths());
                for (int i = 1; i <= loan.getTenureMonths(); i++) {
                    LoanRepayment r = new LoanRepayment();
                    r.setLoanId(loanId);
                    r.setInstallmentNo(i);
                    r.setDueDate(disbursedDate.plusMonths(i));
                    r.setAmountDue(emi);
                    repaymentDAO.insert(conn, r);
                }

                auditService.log(conn, teller.getId(), "LOAN_DISBURSED", "loan", loanId, "APPROVED", "DISBURSED");
                auditService.log(conn, teller.getId(), "LOAN_DISBURSED", "account", acct.getId(),
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

    /** Pays the next pending installment out of the loan's linked account. */
    public void payNextInstallment(int loanId, int tellerId) throws SQLException, InsufficientFundsException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Loan loan = loanDAO.findById(conn, loanId)
                        .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

                LoanRepayment next = repaymentDAO.findNextPending(conn, loanId)
                        .orElseThrow(() -> new IllegalStateException("No pending installments for loan " + loanId));

                Account acct = accountDAO.findByIdForUpdate(conn, loan.getAccountId())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + loan.getAccountId()));

                // QA finding (fixed): a CLOSED account was never excluded from installment
                // payments either -- same rule as everywhere else this review touched.
                requireNotClosed(acct, "pay a loan installment from");

                if (acct.getBalance().compareTo(next.getAmountDue()) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient funds for installment #" + next.getInstallmentNo() +
                                    ": balance " + acct.getBalance() + " < due " + next.getAmountDue());
                }

                BigDecimal newBalance = acct.getBalance().subtract(next.getAmountDue());
                accountDAO.updateBalance(conn, acct.getId(), newBalance);

                Transaction txn = new Transaction(acct.getId(), "WITHDRAW", next.getAmountDue(), tellerId,
                        "Loan #" + loanId + " installment #" + next.getInstallmentNo());
                txn.setBalanceAfter(newBalance);
                int glTxnId = transactionDAO.insert(conn, txn);
                glService.post(conn, "1100", "1200", next.getAmountDue(), glTxnId,
                        "Loan #" + loanId + " installment #" + next.getInstallmentNo());

                repaymentDAO.recordPayment(conn, next.getId(), next.getAmountDue(), LocalDate.now());

                auditService.log(conn, tellerId, "LOAN_INSTALLMENT_PAID", "loan", loanId,
                        "installment #" + next.getInstallmentNo(), next.getAmountDue().toString());

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

    /** Standard amortizing EMI formula: P * r * (1+r)^n / ((1+r)^n - 1), r = monthly rate. */
    static BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        MathContext mc = new MathContext(10);
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), mc);

        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal factor = onePlusR.pow(tenureMonths, mc);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(factor, mc);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}

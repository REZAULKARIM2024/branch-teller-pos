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

    public Loan apply(int customerId, int accountId, String loanType, BigDecimal principal,
                       BigDecimal interestRate, int tenureMonths) throws SQLException {
        if (principal.signum() <= 0) throw new IllegalArgumentException("Principal must be positive");
        if (tenureMonths <= 0) throw new IllegalArgumentException("Tenure must be positive");

        try (Connection conn = DBConnection.getConnection()) {
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

    public void approve(int loanId, int managerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            loanDAO.updateStatus(conn, loanId, "APPROVED", managerId);
            auditService.log(conn, managerId, "LOAN_APPROVED", "loan", loanId, "APPLIED", "APPROVED");
        }
    }

    public void reject(int loanId, int managerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            loanDAO.updateStatus(conn, loanId, "REJECTED", managerId);
            auditService.log(conn, managerId, "LOAN_REJECTED", "loan", loanId, "APPLIED", "REJECTED");
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

package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.PaymentsDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Simulated external payment rails: NEFT/RTGS/Wire outbound transfers to other banks,
 * and bill payments to registered billers. Both debit the customer's account and settle
 * instantly (a real integration would call a payment gateway and go through PROCESSING).
 */
public class PaymentsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> VALID_TRANSFER_TYPES = Set.of("NEFT", "RTGS", "WIRE");

    private final PaymentsDAO paymentsDAO = new PaymentsDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final AuditService auditService = new AuditService();
    private final AmlService amlService = new AmlService();
    private final GlService glService = new GlService();
    private final HoldService holdService = new HoldService();

    /**
     * QA finding (fixed): this method used to accept any transferType string at all (production's
     * MySQL ENUM would eventually reject a bad one with a raw error, but this project's H2 test
     * schema doesn't enforce that, and even in production it's a confusing failure instead of a
     * clear message), and never validated beneficiaryName/beneficiaryBank/beneficiaryAccount/
     * routingSwift beyond what the GUI happened to send -- all four are NOT NULL in the schema,
     * but a blank string still satisfies NOT NULL, so an empty beneficiary name was silently
     * accepted and money left the account with no real record of where it went. Also newly
     * rejects a CLOSED account, the same "CLOSED blocks, DORMANT doesn't" rule already applied to
     * Teller Counter/Cheques/Loans/Cards/Standing Instructions -- an external transfer moves
     * money out, exactly like those.
     */
    public ExternalTransfer initiateExternalTransfer(int accountId, String transferType, String beneficiaryName,
                                                       String beneficiaryBank, String beneficiaryAccount, String routingSwift,
                                                       BigDecimal amount, int initiatedBy) throws SQLException, InsufficientFundsException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (transferType == null || !VALID_TRANSFER_TYPES.contains(transferType)) {
            throw new IllegalArgumentException("Transfer type must be NEFT, RTGS, or WIRE, got: " + transferType);
        }
        if (beneficiaryName == null || beneficiaryName.isBlank()) {
            throw new IllegalArgumentException("Beneficiary name is required");
        }
        if (beneficiaryBank == null || beneficiaryBank.isBlank()) {
            throw new IllegalArgumentException("Beneficiary bank is required");
        }
        if (beneficiaryAccount == null || beneficiaryAccount.isBlank()) {
            throw new IllegalArgumentException("Beneficiary account is required");
        }
        if (routingSwift == null || routingSwift.isBlank()) {
            throw new IllegalArgumentException("Routing/SWIFT code is required");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
                requireNotClosed(acct, "send an external transfer");

                BigDecimal held = holdService.activeHoldsTotal(conn, accountId);
                BigDecimal available = acct.getBalance().subtract(held);
                if (available.compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Insufficient available funds: available " + available + " < requested " + amount);
                }

                BigDecimal newBalance = acct.getBalance().subtract(amount);
                accountDAO.updateBalance(conn, accountId, newBalance);

                Transaction txn = new Transaction(accountId, "WITHDRAW", amount, initiatedBy,
                        transferType + " transfer to " + beneficiaryName);
                txn.setBalanceAfter(newBalance);
                int txnId = transactionDAO.insert(conn, txn);

                ExternalTransfer t = new ExternalTransfer();
                t.setAccountId(accountId);
                t.setTransferType(transferType);
                t.setBeneficiaryName(beneficiaryName);
                t.setBeneficiaryBank(beneficiaryBank);
                t.setBeneficiaryAccount(beneficiaryAccount);
                t.setRoutingSwift(routingSwift);
                t.setAmount(amount);
                t.setStatus("COMPLETED");
                t.setReferenceNo(transferType + "-" + System.currentTimeMillis() % 1000000 + "-" + (100 + RANDOM.nextInt(900)));
                t.setInitiatedBy(initiatedBy);
                int id = paymentsDAO.insertExternalTransfer(conn, t);
                t.setId(id);

                auditService.log(conn, initiatedBy, transferType + "_TRANSFER", "account", accountId,
                        acct.getBalance().toString(), newBalance.toString());
                amlService.checkAndFlag(conn, accountId, txnId, amount, transferType + "_TRANSFER");
                glService.post(conn, "1100", "1000", amount, txnId, transferType + " to " + beneficiaryBank + " (" + t.getReferenceNo() + ")");

                conn.commit();
                return t;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<ExternalTransfer> recentExternalTransfers(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return paymentsDAO.findExternalTransfers(conn, limit);
        }
    }

    public List<Biller> billers() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return paymentsDAO.findBillers(conn);
        }
    }

    /**
     * QA finding (fixed): used to accept any billerId at all -- an unknown one only failed with a
     * raw foreign-key SQLException instead of a clear message. Also newly rejects a CLOSED
     * account for the same reason as {@link #initiateExternalTransfer}.
     */
    public BillPayment payBill(int accountId, int billerId, BigDecimal amount, int paidBy) throws SQLException, InsufficientFundsException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
                requireNotClosed(acct, "pay a bill from");
                paymentsDAO.findBillerById(conn, billerId)
                        .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + billerId));

                BigDecimal held = holdService.activeHoldsTotal(conn, accountId);
                BigDecimal available = acct.getBalance().subtract(held);
                if (available.compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Insufficient available funds: available " + available + " < requested " + amount);
                }

                BigDecimal newBalance = acct.getBalance().subtract(amount);
                accountDAO.updateBalance(conn, accountId, newBalance);

                Transaction txn = new Transaction(accountId, "WITHDRAW", amount, paidBy, "Bill payment");
                txn.setBalanceAfter(newBalance);
                int txnId = transactionDAO.insert(conn, txn);

                BillPayment p = new BillPayment();
                p.setAccountId(accountId);
                p.setBillerId(billerId);
                p.setReferenceNo("BILL-" + System.currentTimeMillis() % 1000000 + "-" + (100 + RANDOM.nextInt(900)));
                p.setAmount(amount);
                p.setStatus("COMPLETED");
                p.setPaidBy(paidBy);
                int id = paymentsDAO.insertBillPayment(conn, p);
                p.setId(id);

                auditService.log(conn, paidBy, "BILL_PAYMENT", "account", accountId,
                        acct.getBalance().toString(), newBalance.toString());
                glService.post(conn, "1100", "1000", amount, txnId, "Bill payment (" + p.getReferenceNo() + ")");

                conn.commit();
                return p;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<BillPayment> recentBillPayments(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return paymentsDAO.findBillPayments(conn, limit);
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
}
